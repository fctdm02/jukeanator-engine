package com.djt.jukeanator_engine.domain.songplayer.service.utils;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;
import com.djt.jukeanator_engine.domain.songplayer.exception.SongPlayerServiceException;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * WinampMediaPlayer — a drop-in implementation of {@link Player} that controls a running (or
 * auto-launched) Winamp process via the Win32 messaging API using JNA.
 *
 * <p>
 * <b>pom.xml dependencies required:</b>
 * 
 * <pre>{@code
 * <dependency>
 *     <groupId>net.java.dev.jna</groupId>
 *     <artifactId>jna</artifactId>
 *     <version>5.17.0</version>
 * </dependency>
 * <dependency>
 *     <groupId>net.java.dev.jna</groupId>
 *     <artifactId>jna-platform</artifactId>
 *     <version>5.17.0</version>
 * </dependency>
 * }</pre>
 *
 * <p>
 * <b>Volume contract (matches {@link Player} Javadoc):</b>
 * <ul>
 * <li>0 = completely muted</li>
 * <li>100 = unity gain (Winamp internal 0-255 value 128)</li>
 * <li>200 = maximum software amplification (Winamp internal 255)</li>
 * </ul>
 *
 * <p>
 * <b>setOnFinished:</b> Because Winamp does not post an end-of-track event to external processes, a
 * lightweight polling thread samples playback state every 300 ms. The callback fires exactly once
 * when the state transitions from PLAYING → STOPPED.
 */
public class WinampMediaPlayer implements Player {

  private static final Logger LOG = Logger.getLogger(WinampMediaPlayer.class.getName());

  private static final String DEFAULT_WINAMP_EXE_PATH =
      "C:\\Program Files (x86)\\Winamp\\winamp.exe";

  // -----------------------------------------------------------------------
  // Winamp window class name (unchanged across all 2.x / 5.x releases)
  // -----------------------------------------------------------------------
  private static final String WINAMP_WINDOW_CLASS = "Winamp v1.x";

  // -----------------------------------------------------------------------
  // Win32 message constants
  // -----------------------------------------------------------------------
  private static final int WM_COMMAND = 0x0111; // Win32 WM_COMMAND
  private static final int WM_WA_IPC = 0x0400; // WM_USER — Winamp IPC channel

  // -----------------------------------------------------------------------
  // WM_COMMAND button identifiers
  // -----------------------------------------------------------------------
  private static final int WINAMP_BUTTON2_PAUSE = 40046; // Play / Resume
  private static final int WINAMP_BUTTON3 = 40047; // Pause toggle
  private static final int WINAMP_BUTTON4 = 40048; // Stop

  // -----------------------------------------------------------------------
  // WM_WA_IPC command values (lParam)
  // -----------------------------------------------------------------------
  private static final int IPC_ISPLAYING = 104; // 0=stopped, 1=playing, 3=paused
  private static final int IPC_GETOUTPUTTIME = 105; // wParam 0→ms elapsed, wParam 1→length (s)
  private static final int IPC_SETVOLUME = 122; // wParam 0-255 sets volume; wParam -666 reads

  // -----------------------------------------------------------------------
  // Polling
  // -----------------------------------------------------------------------
  private static final long POLL_INTERVAL_MS = 300L;

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private final AtomicReference<SongPlayerStatus> status =
      new AtomicReference<>(SongPlayerStatus.STOPPED);
  private final AtomicBoolean callbackFired = new AtomicBoolean(false);

  private volatile Runnable onFinished;
  private volatile int currentVolume; // 0-200 (Player contract)
  private volatile long songLengthMs = 0L;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "winamp-poll");
        t.setDaemon(true);
        return t;
      });

  private volatile ScheduledFuture<?> pollTask;

  /** Path to winamp.exe — override via constructor if installed elsewhere. */
  private final String winampExePath;

  // -----------------------------------------------------------------------
  // JNA interface — subset of User32 we need
  // -----------------------------------------------------------------------
  interface User32Ex extends StdCallLibrary {
    User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

    HWND FindWindow(String lpClassName, String lpWindowName);

    LRESULT SendMessage(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam);

    boolean PostMessage(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam);
  }

  // -----------------------------------------------------------------------
  // Constructor
  // -----------------------------------------------------------------------
  /**
   *
   * @param winampExePath absolute path to winamp.exe
   * @param initialVolume initial volume in the Player 0-200 scale
   */
  public WinampMediaPlayer(String winampExePath, int initialVolume) {

    File file = new File(winampExePath);
    if (!file.exists()) {
      file = new File(DEFAULT_WINAMP_EXE_PATH);
      if (!file.exists()) {

        throw new RuntimeException("Cannot find winamp.exe at configured location: ["
            + winampExePath + "], nor at default location: [" + DEFAULT_WINAMP_EXE_PATH
            + "].  Please install Winamp and specify location in application.yml");
      }
    }

    this.winampExePath = winampExePath;
    this.currentVolume = clampVolume(initialVolume);
  }

  // -----------------------------------------------------------------------
  // Player interface — playback
  // -----------------------------------------------------------------------

  /**
   * Loads {@code songPath} into Winamp and starts playback.
   *
   * <p>
   * Algorithm:
   * <ol>
   * <li>If Winamp is not running, launch it.</li>
   * <li>Stop any current playback and clear the playlist.</li>
   * <li>Re-launch Winamp with the file path as a command-line argument — the cleanest, most
   * compatible way to load a single file without IPC pointer marshalling.</li>
   * <li>Wait for the window to appear, then start polling.</li>
   * </ol>
   */
  @Override
  public boolean playSongMedia(String songPath) {

    try {

      // Stop current song and cancel the existing poll task cleanly.
      cancelPollTask();
      callbackFired.set(false);
      songLengthMs = 0L;
      status.set(SongPlayerStatus.STOPPED);

      // Close any existing Winamp instance so we get a clean playlist.
      HWND existing = findWinampWindow();
      if (existing != null) {
        sendCommand(existing, 40001); // WINAMP_FILE_QUIT
        waitForWindowToClose(1500);
      }

      // Launch Winamp with the file as argument — Winamp will enqueue and play it.
      ProcessBuilder pb = new ProcessBuilder(winampExePath, songPath);
      pb.inheritIO();
      pb.start();

      // Wait for Winamp to appear (up to 8 seconds).
      HWND hwnd = waitForWinampWindow(8000);
      if (hwnd == null) {
        LOG.severe("Winamp window did not appear after launch.");
        status.set(SongPlayerStatus.STOPPED);
        return false;
      }

      // Winamp auto-plays when launched with a file argument.
      status.set(SongPlayerStatus.PLAYING);

      // Apply the current volume setting.
      applyVolume(hwnd, currentVolume);

      // Start polling to detect end-of-track.
      startPollTask();

      return true;

    } catch (Exception e) {

      status.set(SongPlayerStatus.STOPPED);
      throw new SongPlayerServiceException(
          "UNABLE TO PLAY: " + songPath + ", error: " + e.getMessage());
    }
  }

  @Override
  public void pause() {

    HWND hwnd = findWinampWindow();
    if (hwnd == null)
      return;

    SongPlayerStatus current = status.get();
    if (current == SongPlayerStatus.PLAYING) {
      sendCommand(hwnd, WINAMP_BUTTON3); // Pause
      status.set(SongPlayerStatus.PAUSED);
    } else if (current == SongPlayerStatus.PAUSED) {
      sendCommand(hwnd, WINAMP_BUTTON2_PAUSE); // Resume
      status.set(SongPlayerStatus.PLAYING);
    }
  }

  @Override
  public void stop() {

    HWND hwnd = findWinampWindow();
    if (hwnd != null) {
      sendCommand(hwnd, WINAMP_BUTTON4); // Stop
    }
    status.set(SongPlayerStatus.STOPPED);
    cancelPollTask();
  }

  @Override
  public void release() {

    cancelPollTask();
    scheduler.shutdownNow();

    HWND hwnd = findWinampWindow();
    if (hwnd != null) {
      sendCommand(hwnd, 40001); // WINAMP_FILE_QUIT
    }
    status.set(SongPlayerStatus.STOPPED);
  }

  // -----------------------------------------------------------------------
  // Player interface — status / position
  // -----------------------------------------------------------------------

  @Override
  public SongPlayerStatus getStatus() {

    // Refresh from Winamp in case it was closed externally.
    HWND hwnd = findWinampWindow();
    if (hwnd == null) {
      status.set(SongPlayerStatus.STOPPED);
      return SongPlayerStatus.STOPPED;
    }
    int raw = (int) sendIPC(hwnd, 0, IPC_ISPLAYING);
    SongPlayerStatus live = rawStatusToEnum(raw);
    status.set(live);
    return live;
  }

  @Override
  public long getElapsedSeconds() {

    HWND hwnd = findWinampWindow();
    if (hwnd == null)
      return 0L;
    // IPC_GETOUTPUTTIME wParam=0 → elapsed ms
    long ms = sendIPC(hwnd, 0, IPC_GETOUTPUTTIME);
    return ms < 0 ? 0L : ms / 1000L;
  }

  @Override
  public long getTotalLengthSeconds() {

    if (songLengthMs > 0)
      return songLengthMs / 1000L;
    HWND hwnd = findWinampWindow();
    if (hwnd == null)
      return 0L;
    // IPC_GETOUTPUTTIME wParam=1 → total length in seconds
    long secs = sendIPC(hwnd, 1, IPC_GETOUTPUTTIME);
    if (secs > 0)
      songLengthMs = secs * 1000L;
    return secs < 0 ? 0L : secs;
  }

  // -----------------------------------------------------------------------
  // Player interface — volume
  // -----------------------------------------------------------------------

  /**
   * Returns volume in the Player 0-200 scale. Queries Winamp if it is running, otherwise returns
   * the last set value.
   */
  @Override
  public int getVolume() {

    HWND hwnd = findWinampWindow();
    if (hwnd == null)
      return currentVolume;
    // IPC_SETVOLUME with wParam = -666 returns the current volume (0-255).
    long raw = sendIPC(hwnd, -666, IPC_SETVOLUME);
    if (raw < 0)
      return currentVolume;
    currentVolume = winampToPlayerVolume((int) raw);
    return currentVolume;
  }

  /**
   * Sets volume in the Player 0-200 scale. Winamp's internal scale is 0-255 where 128 ≈ unity gain.
   */
  @Override
  public void setVolume(int volume) {

    currentVolume = clampVolume(volume);
    HWND hwnd = findWinampWindow();
    if (hwnd != null) {
      applyVolume(hwnd, currentVolume);
    }
  }

  // -----------------------------------------------------------------------
  // Player interface — callback
  // -----------------------------------------------------------------------

  @Override
  public void setOnFinished(Runnable callback) {
    this.onFinished = callback;
  }

  // -----------------------------------------------------------------------
  // Private helpers — Win32 messaging
  // -----------------------------------------------------------------------

  private HWND findWinampWindow() {
    return User32Ex.INSTANCE.FindWindow(WINAMP_WINDOW_CLASS, null);
  }

  /**
   * Sends a WM_COMMAND to Winamp (button press).
   */
  private void sendCommand(HWND hwnd, int command) {
    User32Ex.INSTANCE.PostMessage(hwnd, WM_COMMAND, new WPARAM(command), new LPARAM(0));
  }

  /**
   * Sends a WM_WA_IPC message and returns the LRESULT as a long.
   *
   * @param wParam the data value (int promoted to long for WPARAM)
   * @param ipc the IPC command constant
   */
  private long sendIPC(HWND hwnd, long wParam, int ipc) {
    LRESULT result =
        User32Ex.INSTANCE.SendMessage(hwnd, WM_WA_IPC, new WPARAM(wParam), new LPARAM(ipc));
    return result.longValue();
  }

  private void applyVolume(HWND hwnd, int playerVolume) {
    int winampVolume = playerToWinampVolume(playerVolume);
    sendIPC(hwnd, winampVolume, IPC_SETVOLUME);
  }

  // -----------------------------------------------------------------------
  // Private helpers — volume conversion
  // -----------------------------------------------------------------------

  /**
   * Converts Player scale (0-200) to Winamp scale (0-255). Player 100 → Winamp 128 (unity). Player
   * 200 → Winamp 255 (max).
   */
  private static int playerToWinampVolume(int playerVol) {
    int clamped = Math.max(0, Math.min(200, playerVol));
    if (clamped <= 100) {
      // 0-100 maps to 0-128
      return (int) Math.round(clamped * 128.0 / 100.0);
    } else {
      // 101-200 maps to 129-255
      return 128 + (int) Math.round((clamped - 100) * 127.0 / 100.0);
    }
  }

  /**
   * Converts Winamp scale (0-255) back to Player scale (0-200).
   */
  private static int winampToPlayerVolume(int winampVol) {
    int clamped = Math.max(0, Math.min(255, winampVol));
    if (clamped <= 128) {
      return (int) Math.round(clamped * 100.0 / 128.0);
    } else {
      return 100 + (int) Math.round((clamped - 128) * 100.0 / 127.0);
    }
  }

  private static int clampVolume(int vol) {
    return Math.max(0, Math.min(200, vol));
  }

  // -----------------------------------------------------------------------
  // Private helpers — status conversion
  // -----------------------------------------------------------------------

  private static SongPlayerStatus rawStatusToEnum(int raw) {
    return switch (raw) {
      case 1 -> SongPlayerStatus.PLAYING;
      case 3 -> SongPlayerStatus.PAUSED;
      default -> SongPlayerStatus.STOPPED;
    };
  }

  // -----------------------------------------------------------------------
  // Private helpers — window wait / launch
  // -----------------------------------------------------------------------

  /**
   * Blocks until the Winamp window appears or {@code timeoutMs} elapses.
   *
   * @return the window handle, or {@code null} on timeout
   */
  private HWND waitForWinampWindow(long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      HWND hwnd = findWinampWindow();
      if (hwnd != null)
        return hwnd;
      Thread.sleep(150);
    }
    return null;
  }

  /**
   * Waits up to {@code timeoutMs} for the Winamp window to disappear (after quit).
   */
  private void waitForWindowToClose(long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (findWinampWindow() == null)
        return;
      Thread.sleep(100);
    }
  }

  // -----------------------------------------------------------------------
  // Private helpers — polling thread
  // -----------------------------------------------------------------------

  private void startPollTask() {
    cancelPollTask();
    pollTask = scheduler.scheduleAtFixedRate(this::pollPlaybackState, POLL_INTERVAL_MS,
        POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  private void cancelPollTask() {
    ScheduledFuture<?> t = pollTask;
    if (t != null) {
      t.cancel(false);
      pollTask = null;
    }
  }

  /**
   * Runs on the polling thread. Detects PLAYING → STOPPED transition and fires the
   * {@code onFinished} callback exactly once per song.
   */
  private void pollPlaybackState() {
    try {
      HWND hwnd = findWinampWindow();

      // Winamp was closed externally.
      if (hwnd == null) {
        if (status.getAndSet(SongPlayerStatus.STOPPED) == SongPlayerStatus.PLAYING) {
          fireOnFinished();
        }
        cancelPollTask();
        return;
      }

      int raw = (int) sendIPC(hwnd, 0, IPC_ISPLAYING);
      SongPlayerStatus live = rawStatusToEnum(raw);

      SongPlayerStatus previous = status.getAndSet(live);

      // Fire the callback when transitioning out of PLAYING into STOPPED.
      // We do NOT fire on PLAYING → PAUSED.
      if (previous == SongPlayerStatus.PLAYING && live == SongPlayerStatus.STOPPED) {
        cancelPollTask();
        fireOnFinished();
      }

    } catch (Exception e) {
      LOG.log(Level.FINE, "Poll error (Winamp may have just closed)", e);
    }
  }

  /**
   * Fires the {@code onFinished} callback exactly once. Thread-safe via CAS on
   * {@code callbackFired}.
   */
  private void fireOnFinished() {
    if (callbackFired.compareAndSet(false, true)) {
      Runnable cb = onFinished;
      if (cb != null) {
        try {
          cb.run();
        } catch (Exception e) {
          LOG.log(Level.WARNING, "onFinished callback threw an exception", e);
        }
      }
    }
  }
}
