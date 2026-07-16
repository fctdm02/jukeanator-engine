package com.djt.jukeanator_engine.domain.songplayer.service.utils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
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
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
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
 *
 * <p>
 * <b>Process lifecycle:</b> winamp.exe is launched once, at construction time, and is not killed
 * again until {@link #release()} (application shutdown). Loading a new song between plays does
 * not restart the process — it clears the playlist and loads the file into the already-running
 * instance via the WM_WA_IPC / WM_COPYDATA messaging API, which is far faster than relaunching.
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
  private static final int WM_COPYDATA = 0x004A; // Win32 WM_COPYDATA

  // -----------------------------------------------------------------------
  // WM_COMMAND button identifiers
  // -----------------------------------------------------------------------
  private static final int WINAMP_BUTTON2_PAUSE = 40046; // Play / Resume
  private static final int WINAMP_BUTTON3 = 40047; // Pause toggle
  private static final int WINAMP_BUTTON4 = 40048; // Stop

  // -----------------------------------------------------------------------
  // WM_WA_IPC command values (lParam)
  // -----------------------------------------------------------------------
  private static final int IPC_PLAYFILE = 100; // sent via WM_COPYDATA — enqueues a file only;
                                                // does NOT change playback state on its own
  private static final int IPC_DELETE = 101; // clears Winamp's internal playlist
  private static final int IPC_STARTPLAY = 102; // starts playback — equivalent to pressing Play
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

  /**
   * Poll-thread-private view of the last observed status, used only for PLAYING → STOPPED edge
   * detection. This is deliberately kept separate from {@link #status}: {@link #getStatus()} is
   * called from other threads (e.g. REST status polling) and overwrites {@link #status} as a
   * side effect, which previously could race with this poll loop and "steal" the transition
   * before the poll loop observed it — permanently suppressing the {@code onFinished} callback.
   * Only {@link #pollPlaybackState()} reads/writes this field, so it is safe as a plain field.
   */
  private SongPlayerStatus lastPolledStatus = SongPlayerStatus.STOPPED;

  private volatile Runnable onFinished;
  private volatile int currentVolume; // 0-200 (Player contract)
  private volatile long songLengthMs = 0L;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "winamp-poll");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
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

    LRESULT SendMessage(HWND hWnd, int uMsg, WPARAM wParam, COPYDATASTRUCT lParam);

    boolean PostMessage(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam);
  }

  /**
   * Win32 {@code COPYDATASTRUCT}, used to pass a file path to a running Winamp instance via
   * {@code WM_COPYDATA} (see {@link #IPC_PLAYFILE}).
   */
  public static class COPYDATASTRUCT extends Structure {
    public ULONG_PTR dwData;
    public int cbData;
    public Pointer lpData;

    @Override
    protected List<String> getFieldOrder() {
      return Arrays.asList("dwData", "cbData", "lpData");
    }
  }

  // -----------------------------------------------------------------------
  // Constructor
  // -----------------------------------------------------------------------
  /**
   * Locates winamp.exe and launches it immediately so it is already warm by the time the first
   * song is queued. The process is not killed again until {@link #release()}.
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

    this.winampExePath = file.getPath();
    this.currentVolume = clampVolume(initialVolume);

    try {
      HWND hwnd = launchWinamp();
      if (hwnd == null) {
        throw new RuntimeException(
            "Winamp window did not appear after launch at startup (waited 8s).");
      }
      applyVolume(hwnd, currentVolume);

      // launchWinamp() may have attached to a Winamp instance left running from a previous
      // (e.g. crashed/killed) application run rather than starting a fresh one — Winamp is
      // single-instance, so relaunching it just hands off to the existing window. That leftover
      // instance can still have an old song loaded (playing, paused, or already finished) that
      // this session never told it to play, and our end-of-track poll loop only runs while a
      // song started via playSongMedia() is active — so a leftover song finishing on its own
      // would never be noticed and the queue would never advance. Force a clean, known STOPPED
      // state here so this session's queue processing starts from scratch. This does not
      // restart the winamp.exe process, so it only costs two IPC calls, once, at startup.
      sendCommand(hwnd, WINAMP_BUTTON4); // Stop
      sendIPC(hwnd, 0, IPC_DELETE); // Clear playlist
      status.set(SongPlayerStatus.STOPPED);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while launching winamp.exe at startup", e);
    }
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
   * <li>If Winamp is not running (e.g. the window was closed externally), relaunch it. Otherwise
   * reuse the already-running process — restarting it for every song was the slow part.</li>
   * <li>Clear the playlist via {@code IPC_DELETE} so only the requested song plays.</li>
   * <li>Load and play the file via {@code WM_COPYDATA} / {@code IPC_PLAYFILE}.</li>
   * <li>Start polling.</li>
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
      lastPolledStatus = SongPlayerStatus.STOPPED;

      // Reuse the running Winamp instance; only relaunch if it was closed externally.
      HWND hwnd = ensureWinampRunning();
      if (hwnd == null) {
        LOG.severe("Winamp window did not appear after launch.");
        status.set(SongPlayerStatus.STOPPED);
        return false;
      }

      // Clear the playlist, load the new file, then explicitly start playback — IPC_PLAYFILE
      // only enqueues the file and does not change Winamp's playback state on its own.
      sendIPC(hwnd, 0, IPC_DELETE);
      sendPlayFile(hwnd, songPath);
      sendIPC(hwnd, 0, IPC_STARTPLAY);

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

  /**
   * Loads {@code songPath} into the given (already-running) Winamp instance's playlist via
   * {@code WM_COPYDATA} / {@code IPC_PLAYFILE}. This only enqueues the file — it does not start
   * playback, so callers must follow up with {@code IPC_STARTPLAY}.
   */
  private void sendPlayFile(HWND hwnd, String songPath) {
    byte[] pathBytes = (songPath + "\0").getBytes();

    // SendMessage (unlike PostMessage) blocks until Winamp's window proc has processed
    // WM_COPYDATA and copied the data out, so it is safe to free the native memory as soon as
    // the call returns.
    try (Memory mem = new Memory(pathBytes.length)) {
      mem.write(0, pathBytes, 0, pathBytes.length);

      COPYDATASTRUCT cds = new COPYDATASTRUCT();
      cds.dwData = new ULONG_PTR(IPC_PLAYFILE);
      cds.cbData = pathBytes.length;
      cds.lpData = mem;

      User32Ex.INSTANCE.SendMessage(hwnd, WM_COPYDATA, new WPARAM(0), cds);
    }
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
   * Returns the running Winamp window, relaunching the process if it was closed externally (e.g.
   * the user closed the window). Does nothing if Winamp is already running.
   */
  private HWND ensureWinampRunning() throws InterruptedException {
    HWND hwnd = findWinampWindow();
    if (hwnd != null) {
      return hwnd;
    }
    LOG.warning("Winamp window not found — relaunching winamp.exe.");
    return launchWinamp();
  }

  /**
   * Starts winamp.exe and waits (up to 8 seconds) for its window to appear.
   *
   * @return the window handle, or {@code null} on timeout
   */
  private HWND launchWinamp() throws InterruptedException {
    try {
      ProcessBuilder pb = new ProcessBuilder(winampExePath);
      pb.inheritIO();
      pb.start();
    } catch (java.io.IOException e) {
      throw new SongPlayerServiceException("Unable to launch winamp.exe at [" + winampExePath
          + "], error: " + e.getMessage());
    }
    return waitForWinampWindow(8000);
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
        status.set(SongPlayerStatus.STOPPED);
        if (lastPolledStatus == SongPlayerStatus.PLAYING) {
          lastPolledStatus = SongPlayerStatus.STOPPED;
          cancelPollTask();
          fireOnFinished();
        }
        return;
      }

      int raw = (int) sendIPC(hwnd, 0, IPC_ISPLAYING);
      SongPlayerStatus live = rawStatusToEnum(raw);

      // Belt-and-suspenders: if Winamp still reports "playing" but playback position has
      // already reached (or passed) the track length, treat it as finished. This guards
      // against IPC_ISPLAYING not flipping to stopped promptly at end-of-track.
      if (live == SongPlayerStatus.PLAYING && hasReachedEndOfTrack(hwnd)) {
        live = SongPlayerStatus.STOPPED;
      }

      status.set(live);

      // Edge-detect on our own poll-thread-private view, NOT on the shared `status` field.
      // `status` can also be written by getStatus() from other threads (e.g. REST status
      // reads); using it for edge detection let those reads silently steal the transition
      // before this loop observed it, so the onFinished callback would never fire.
      SongPlayerStatus previous = lastPolledStatus;
      lastPolledStatus = live;

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
   * Returns {@code true} when the current playback position has reached or passed the known
   * track length. Used as a secondary end-of-track signal alongside IPC_ISPLAYING.
   */
  private boolean hasReachedEndOfTrack(HWND hwnd) {
    long lengthMs = songLengthMs;
    if (lengthMs <= 0) {
      long lengthSec = sendIPC(hwnd, 1, IPC_GETOUTPUTTIME);
      if (lengthSec <= 0)
        return false;
      lengthMs = lengthSec * 1000L;
      songLengthMs = lengthMs;
    }
    long posMs = sendIPC(hwnd, 0, IPC_GETOUTPUTTIME);
    return posMs >= 0 && posMs >= lengthMs;
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
