package com.djt.jukeanator_engine.domain.songplayer.service.utils;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;
import com.djt.jukeanator_engine.domain.songplayer.exception.PlayerUnavailableException;
import com.djt.jukeanator_engine.domain.songplayer.exception.SongPlayerServiceException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
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
 *
 * <p>
 * <b>Crash / hang recovery:</b> every IPC call uses {@code SendMessageTimeout}, so a hung Winamp
 * (e.g. its decoder wedged on a corrupt file) can never block the calling thread indefinitely. A
 * call that is not answered within the configured timeout kills winamp.exe; so does a song whose
 * playback position stops advancing for the configured stall time. The song involved is reported
 * through {@link #setOnPlaybackFailed}, and the next {@link #playSongMedia} relaunches Winamp. A
 * relaunch whose main window never appears (e.g. Winamp stuck on a post-crash prompt) is killed
 * rather than left running, and further launch attempts back off for {@link #LAUNCH_BACKOFF_MS}.
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

  // SendMessageTimeout flags: don't let other sent messages run on the calling thread while it
  // waits, and return immediately (without waiting out the timeout) if Windows already considers
  // the target window hung.
  private static final int SMTO_BLOCK = 0x0001;
  private static final int SMTO_ABORTIFHUNG = 0x0002;

  // -----------------------------------------------------------------------
  // WM_COMMAND button identifiers
  // -----------------------------------------------------------------------
  private static final int WINAMP_BUTTON_PAUSE = 40046; // Pause toggle (pauses when playing,
                                                         // resumes when paused)
  private static final int WINAMP_BUTTON_STOP = 40047; // Stop

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
  private static final long POLL_INTERVAL_MS = 750L;

  private static final int DEFAULT_IPC_TIMEOUT_MS = 2000;
  private static final int DEFAULT_STALL_SECONDS = 15;

  /** How long to wait for Winamp's main window after launching winamp.exe. */
  private static final long LAUNCH_WAIT_MS = 8000L;

  /** After a failed launch, further launch attempts are skipped for this long. */
  private static final long LAUNCH_BACKOFF_MS = 10_000L;

  /** How long to wait for a killed winamp.exe to actually exit. */
  private static final long KILL_WAIT_SECONDS = 5L;

  // Win32 HIGH_PRIORITY_CLASS (winbase.h) — not consistently re-exported across jna-platform
  // versions, so we use the raw constant value directly.
  private static final int HIGH_PRIORITY_CLASS = 0x00000080;

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

  /**
   * Poll-thread-private stall detection: the last observed playback position and when it last
   * changed. Reset by {@link #playSongMedia} before the poll task is (re)started.
   */
  private long lastPositionMs = -1L;
  private long lastPositionChangeMs = 0L;

  private volatile Runnable onFinished;
  private volatile Consumer<String> onPlaybackFailed;
  private volatile int currentVolume; // 0-200 (Player contract)
  private volatile long songLengthMs = 0L;

  /** The song most recently passed to {@link #playSongMedia}, reported if it breaks playback. */
  private volatile String currentSongPath;
  private final AtomicBoolean failureReported = new AtomicBoolean(false);

  /** Launch attempts are skipped until this time (epoch ms) after a failed launch. */
  private volatile long launchBackoffUntil = 0L;

  private final int ipcTimeoutMs;
  private final long stallMs;

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

    boolean PostMessage(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam);

    // lpdwResult is a PDWORD_PTR (pointer-sized), so LongByReference is large enough on both
    // 32-bit and 64-bit JVMs.
    LRESULT SendMessageTimeout(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam, int fuFlags,
        int uTimeout, LongByReference lpdwResult);

    LRESULT SendMessageTimeout(HWND hWnd, int uMsg, WPARAM wParam, COPYDATASTRUCT lParam,
        int fuFlags, int uTimeout, LongByReference lpdwResult);

    boolean IsWindow(HWND hWnd);

    int GetWindowThreadProcessId(HWND hWnd, IntByReference lpdwProcessId);
  }

  /**
   * Thrown by {@link #sendIPC} / {@link #sendPlayFile} when Winamp did not answer within the IPC
   * timeout. By the time it is thrown, the hung winamp.exe has already been killed.
   */
  private static class WinampUnresponsiveException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    WinampUnresponsiveException(String message) {
      super(message);
    }
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
    this(winampExePath, initialVolume, DEFAULT_IPC_TIMEOUT_MS, DEFAULT_STALL_SECONDS);
  }

  /**
   * @param winampExePath absolute path to winamp.exe
   * @param initialVolume initial volume in the Player 0-200 scale
   * @param ipcTimeoutMs an IPC call not answered within this many ms means Winamp is hung
   * @param stallSeconds a song reported as playing whose position has not advanced for this many
   *        seconds is treated as stalled; 0 or less disables stall detection
   */
  public WinampMediaPlayer(String winampExePath, int initialVolume, int ipcTimeoutMs,
      int stallSeconds) {

    this.ipcTimeoutMs = ipcTimeoutMs > 0 ? ipcTimeoutMs : DEFAULT_IPC_TIMEOUT_MS;
    this.stallMs = stallSeconds * 1000L;

    File file = findWinampExe(winampExePath);
    if (file == null) {
      File appDirFile = resolveAppDirWinampExe();
      throw new RuntimeException("Cannot find winamp.exe at configured location: ["
          + winampExePath + "], at default location: [" + DEFAULT_WINAMP_EXE_PATH
          + "], nor alongside the application at: ["
          + (appDirFile != null ? appDirFile.getPath() : "<unresolvable>")
          + "].  Please install Winamp and specify location in application.yml");
    }

    this.winampExePath = file.getPath();
    this.currentVolume = clampVolume(initialVolume);

    try {
      // If winamp.exe is already running (e.g. left over from a previous app run), attach to
      // that instance instead of spawning another winamp.exe process.
      // A leftover instance that turns out to be hung has already been killed by
      // resetWinamp(), so fall through to launching a fresh one.
      HWND hwnd = findWinampWindow();
      if (hwnd != null && !resetWinamp(hwnd)) {
        hwnd = null;
      }
      if (hwnd == null) {
        hwnd = launchWinamp();
        if (hwnd == null) {
          throw new RuntimeException("Winamp window did not appear after launch at startup (waited "
              + (LAUNCH_WAIT_MS / 1000) + "s).");
        }
        if (!resetWinamp(hwnd)) {
          throw new RuntimeException("Winamp stopped responding right after launch at startup.");
        }
      }
      status.set(SongPlayerStatus.STOPPED);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while launching winamp.exe at startup", e);
    }
  }

  /**
   * Applies the current volume and forces Winamp into a clean, known STOPPED state with an empty
   * playlist.
   *
   * <p>
   * The window may belong to a Winamp instance left running from a previous (e.g. crashed/killed)
   * application run rather than one we just launched. That leftover instance can still have an old
   * song loaded (playing, paused, or already finished) that this session never told it to play, and
   * our end-of-track poll loop only runs while a song started via playSongMedia() is active — so a
   * leftover song finishing on its own would never be noticed and the queue would never advance.
   * This does not restart the winamp.exe process, so it only costs a few IPC calls, once, at
   * startup.
   *
   * @return {@code false} if Winamp did not respond (it has then already been killed)
   */
  private boolean resetWinamp(HWND hwnd) {
    try {
      applyVolume(hwnd, currentVolume);
      sendCommand(hwnd, WINAMP_BUTTON_STOP); // Stop
      sendIPC(hwnd, 0, IPC_DELETE); // Clear playlist
      return true;
    } catch (WinampUnresponsiveException e) {
      return false;
    }
  }

  /**
   * Locates winamp.exe, checking (in order) the configured path, {@link #DEFAULT_WINAMP_EXE_PATH},
   * and a {@code Winamp\winamp.exe} sibling of the running application archive.
   *
   * @param configuredPath the configured {@code song-player.winamp-exe-path}, may be {@code null}
   * @return the first location that exists, or {@code null} if winamp.exe cannot be found
   */
  public static File findWinampExe(String configuredPath) {

    if (configuredPath != null && !configuredPath.isBlank()) {
      File configured = new File(configuredPath);
      if (configured.isFile()) {
        return configured;
      }
    }
    File defaultFile = new File(DEFAULT_WINAMP_EXE_PATH);
    if (defaultFile.isFile()) {
      return defaultFile;
    }
    File appDirFile = resolveAppDirWinampExe();
    if (appDirFile != null && appDirFile.isFile()) {
      return appDirFile;
    }
    return null;
  }

  /**
   * Resolves {@code Winamp\winamp.exe} as a sibling of the directory containing the running
   * application archive (e.g. {@code C:\kiosk\jukeanator-engine-0.0.1-SNAPSHOT.war}), mirroring
   * the deployment layout where {@code config}, {@code data}, and {@code Winamp} all live next to
   * the WAR. Returns {@code null} if the application's own location cannot be determined (e.g.
   * running from an IDE's exploded classes directory).
   */
  private static File resolveAppDirWinampExe() {
    try {
      File codeSource =
          new File(WinampMediaPlayer.class.getProtectionDomain().getCodeSource().getLocation()
              .toURI());
      File appDir = codeSource.isFile() ? codeSource.getParentFile() : codeSource;
      if (appDir == null) {
        return null;
      }
      return new File(appDir, "Winamp" + File.separator + "winamp.exe");
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Unable to resolve application directory for Winamp fallback lookup",
          e);
      return null;
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
   *
   * @throws PlayerUnavailableException if Winamp is not running and could not be relaunched, or
   *         was found hung before the song was loaded; the song itself is not at fault and should
   *         be retried
   * @throws SongPlayerServiceException if Winamp failed while loading or starting this song
   */
  @Override
  public boolean playSongMedia(String songPath) {

    boolean songLoaded = false;
    try {

      // Stop current song and cancel the existing poll task cleanly.
      cancelPollTask();
      callbackFired.set(false);
      songLengthMs = 0L;
      status.set(SongPlayerStatus.STOPPED);
      lastPolledStatus = SongPlayerStatus.STOPPED;
      lastPositionMs = -1L;
      lastPositionChangeMs = System.currentTimeMillis();
      currentSongPath = songPath;
      failureReported.set(false);

      // Reuse the running Winamp instance; only relaunch if it was closed externally.
      HWND hwnd = ensureWinampRunning();
      if (hwnd == null) {
        throw new PlayerUnavailableException(
            "Winamp is not running and could not be relaunched; unable to play: " + songPath);
      }

      // Clear the playlist, load the new file, then explicitly start playback — IPC_PLAYFILE
      // only enqueues the file and does not change Winamp's playback state on its own.
      sendIPC(hwnd, 0, IPC_DELETE);
      songLoaded = true;
      sendPlayFile(hwnd, songPath);
      sendIPC(hwnd, 0, IPC_STARTPLAY);

      status.set(SongPlayerStatus.PLAYING);

      // Apply the current volume setting.
      applyVolume(hwnd, currentVolume);

      // Start polling to detect end-of-track.
      startPollTask();

      return true;

    } catch (PlayerUnavailableException e) {

      status.set(SongPlayerStatus.STOPPED);
      throw e;

    } catch (WinampUnresponsiveException e) {

      // Winamp has already been killed. If it hung before this song was even handed to it, the
      // song is not to blame.
      status.set(SongPlayerStatus.STOPPED);
      if (!songLoaded) {
        throw new PlayerUnavailableException(
            "Winamp was hung before " + songPath + " could be loaded; it has been killed", e);
      }
      throw new SongPlayerServiceException(
          "UNABLE TO PLAY: " + songPath + ", error: " + e.getMessage(), e);

    } catch (InterruptedException e) {

      Thread.currentThread().interrupt();
      status.set(SongPlayerStatus.STOPPED);
      throw new PlayerUnavailableException(
          "Interrupted while relaunching Winamp; unable to play: " + songPath, e);

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
      sendCommand(hwnd, WINAMP_BUTTON_PAUSE); // Pause
      status.set(SongPlayerStatus.PAUSED);
    } else if (current == SongPlayerStatus.PAUSED) {
      sendCommand(hwnd, WINAMP_BUTTON_PAUSE); // Resume (same toggle button)
      status.set(SongPlayerStatus.PLAYING);
    }
  }

  @Override
  public void stop() {

    HWND hwnd = findWinampWindow();
    if (hwnd != null) {
      sendCommand(hwnd, WINAMP_BUTTON_STOP); // Stop
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
    try {
      int raw = (int) sendIPC(hwnd, 0, IPC_ISPLAYING);
      SongPlayerStatus live = rawStatusToEnum(raw);
      status.set(live);
      return live;
    } catch (WinampUnresponsiveException e) {
      // The hung winamp.exe has been killed; report STOPPED so the queue moves on and the next
      // playSongMedia() relaunches it.
      status.set(SongPlayerStatus.STOPPED);
      return SongPlayerStatus.STOPPED;
    }
  }

  @Override
  public long getElapsedSeconds() {

    HWND hwnd = findWinampWindow();
    if (hwnd == null)
      return 0L;
    try {
      // IPC_GETOUTPUTTIME wParam=0 → elapsed ms
      long ms = sendIPC(hwnd, 0, IPC_GETOUTPUTTIME);
      return ms < 0 ? 0L : ms / 1000L;
    } catch (WinampUnresponsiveException e) {
      return 0L;
    }
  }

  @Override
  public long getTotalLengthSeconds() {

    if (songLengthMs > 0)
      return songLengthMs / 1000L;
    HWND hwnd = findWinampWindow();
    if (hwnd == null)
      return 0L;
    try {
      // IPC_GETOUTPUTTIME wParam=1 → total length in seconds
      long secs = sendIPC(hwnd, 1, IPC_GETOUTPUTTIME);
      if (secs > 0)
        songLengthMs = secs * 1000L;
      return secs < 0 ? 0L : secs;
    } catch (WinampUnresponsiveException e) {
      return 0L;
    }
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
    try {
      // IPC_SETVOLUME with wParam = -666 returns the current volume (0-255).
      long raw = sendIPC(hwnd, -666, IPC_SETVOLUME);
      if (raw < 0)
        return currentVolume;
      currentVolume = winampToPlayerVolume((int) raw);
      return currentVolume;
    } catch (WinampUnresponsiveException e) {
      return currentVolume;
    }
  }

  /**
   * Sets volume in the Player 0-200 scale. Winamp's internal scale is 0-255 where 128 ≈ unity gain.
   */
  @Override
  public void setVolume(int volume) {

    currentVolume = clampVolume(volume);
    HWND hwnd = findWinampWindow();
    if (hwnd != null) {
      try {
        applyVolume(hwnd, currentVolume);
      } catch (WinampUnresponsiveException e) {
        // Winamp has been killed; the volume is re-applied when the next song starts.
      }
    }
  }

  // -----------------------------------------------------------------------
  // Player interface — callback
  // -----------------------------------------------------------------------

  @Override
  public void setOnFinished(Runnable callback) {
    this.onFinished = callback;
  }

  @Override
  public void setOnPlaybackFailed(Consumer<String> callback) {
    this.onPlaybackFailed = callback;
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
   * Sends a WM_WA_IPC message and returns Winamp's reply.
   *
   * @param wParam the data value (int promoted to long for WPARAM)
   * @param ipc the IPC command constant
   * @return Winamp's reply, or -1 if the Winamp window no longer exists
   * @throws WinampUnresponsiveException if Winamp did not answer within the IPC timeout
   */
  private long sendIPC(HWND hwnd, long wParam, int ipc) {
    LongByReference result = new LongByReference();
    LRESULT ok = User32Ex.INSTANCE.SendMessageTimeout(hwnd, WM_WA_IPC, new WPARAM(wParam),
        new LPARAM(ipc), SMTO_BLOCK | SMTO_ABORTIFHUNG, ipcTimeoutMs, result);
    if (ok.longValue() == 0) {
      handleFailedSend(hwnd, "IPC " + ipc);
      return -1L;
    }
    // Winamp is a 32-bit process, so its replies are 32-bit signed values (e.g. -1).
    return (int) result.getValue();
  }

  /**
   * Called when {@code SendMessageTimeout} fails. If the window is simply gone (Winamp exited
   * between FindWindow and the send), returns normally. Otherwise Winamp is hung: the song being
   * played (if any) is reported, winamp.exe is killed, and {@link WinampUnresponsiveException} is
   * thrown.
   */
  private void handleFailedSend(HWND hwnd, String what) {
    if (!User32Ex.INSTANCE.IsWindow(hwnd)) {
      return;
    }
    String message = "Winamp did not respond to " + what + " within " + ipcTimeoutMs
        + " ms; treating it as hung";
    LOG.warning(message);
    // A poll task only exists while a song is playing or paused, so only then is a song to blame.
    if (pollTask != null) {
      reportPlaybackFailure("Winamp stopped responding");
    }
    killWinampProcesses("not responding to " + what);
    throw new WinampUnresponsiveException(message);
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

    // Windows copies WM_COPYDATA data into Winamp's address space when the message is sent, so it
    // is safe to free the native memory as soon as the call returns (even on a timeout).
    try (Memory mem = new Memory(pathBytes.length)) {
      mem.write(0, pathBytes, 0, pathBytes.length);

      COPYDATASTRUCT cds = new COPYDATASTRUCT();
      cds.dwData = new ULONG_PTR(IPC_PLAYFILE);
      cds.cbData = pathBytes.length;
      cds.lpData = mem;

      LongByReference result = new LongByReference();
      LRESULT ok = User32Ex.INSTANCE.SendMessageTimeout(hwnd, WM_COPYDATA, new WPARAM(0), cds,
          SMTO_BLOCK | SMTO_ABORTIFHUNG, ipcTimeoutMs, result);
      if (ok.longValue() == 0) {
        handleFailedSend(hwnd, "IPC_PLAYFILE");
      }
    }
  }

  /**
   * Forcibly kills every running winamp.exe: the process owning the Winamp main window, plus any
   * process whose image name matches the configured winamp.exe (which also catches instances that
   * never created a main window, e.g. stuck on a post-crash prompt). Waits briefly for them to exit.
   */
  private synchronized void killWinampProcesses(String reason) {

    Set<Long> pids = new LinkedHashSet<>();
    HWND hwnd = findWinampWindow();
    if (hwnd != null) {
      IntByReference pid = new IntByReference();
      User32Ex.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
      if (pid.getValue() != 0) {
        pids.add((long) pid.getValue());
      }
    }
    String exeName = new File(winampExePath).getName();
    ProcessHandle.allProcesses()
        .filter(ph -> ph.info().command()
            .map(cmd -> new File(cmd).getName().equalsIgnoreCase(exeName)).orElse(false))
        .forEach(ph -> pids.add(ph.pid()));

    List<ProcessHandle> alive = pids.stream().map(ProcessHandle::of).flatMap(Optional::stream)
        .filter(ProcessHandle::isAlive).toList();
    if (alive.isEmpty()) {
      return;
    }

    LOG.warning("Killing " + exeName + " (pid " + pids + "): " + reason);
    alive.forEach(ProcessHandle::destroyForcibly);
    for (ProcessHandle ph : alive) {
      try {
        ph.onExit().get(KILL_WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        LOG.warning(exeName + " (pid " + ph.pid() + ") did not exit within " + KILL_WAIT_SECONDS
            + "s of being killed");
      }
    }
  }

  /**
   * Reports the current song through {@link #onPlaybackFailed}, at most once per song.
   */
  private void reportPlaybackFailure(String reason) {
    String path = currentSongPath;
    if (path == null || !failureReported.compareAndSet(false, true)) {
      return;
    }
    LOG.warning("Song broke playback (" + reason + "): " + path);
    Consumer<String> cb = onPlaybackFailed;
    if (cb != null) {
      try {
        cb.accept(path);
      } catch (Exception e) {
        LOG.log(Level.WARNING, "onPlaybackFailed callback threw an exception", e);
      }
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
   * the user closed the window, it crashed, or it was killed as hung). Does nothing if Winamp is
   * already running.
   *
   * @return the window handle, or {@code null} if the relaunch failed or is still backing off
   *         after an earlier failed launch
   */
  private HWND ensureWinampRunning() throws InterruptedException {
    HWND hwnd = findWinampWindow();
    if (hwnd != null) {
      return hwnd;
    }
    if (System.currentTimeMillis() < launchBackoffUntil) {
      return null;
    }
    LOG.warning("Winamp window not found — relaunching winamp.exe.");
    return launchWinamp();
  }

  /**
   * Starts winamp.exe and waits (up to {@link #LAUNCH_WAIT_MS}) for its window to appear. Only
   * called when no Winamp main window exists, so any winamp.exe still running at that point has no
   * main window (e.g. stuck on a post-crash prompt or hung during startup) and is killed first, so
   * duplicate processes never accumulate. On failure, further launches are skipped for
   * {@link #LAUNCH_BACKOFF_MS}.
   *
   * @return the window handle, or {@code null} on failure
   */
  private synchronized HWND launchWinamp() throws InterruptedException {

    killWinampProcesses("relaunching, but the running instance has no Winamp main window");
    try {
      ProcessBuilder pb = new ProcessBuilder(winampExePath);
      pb.inheritIO();
      Process process = pb.start();
      raisePriority(process.pid());
    } catch (java.io.IOException e) {
      LOG.log(Level.SEVERE, "Unable to launch winamp.exe at [" + winampExePath + "]", e);
      launchBackoffUntil = System.currentTimeMillis() + LAUNCH_BACKOFF_MS;
      return null;
    }

    HWND hwnd = waitForWinampWindow(LAUNCH_WAIT_MS);
    if (hwnd == null) {
      LOG.severe("Winamp window did not appear within " + (LAUNCH_WAIT_MS / 1000)
          + "s of launching winamp.exe; killing it and retrying in " + (LAUNCH_BACKOFF_MS / 1000)
          + "s.");
      killWinampProcesses("main window did not appear after launch");
      launchBackoffUntil = System.currentTimeMillis() + LAUNCH_BACKOFF_MS;
    }
    return hwnd;
  }

  /**
   * Raises winamp.exe's process priority to {@code HIGH_PRIORITY_CLASS} so playback (and our
   * frequent WM_WA_IPC polling) is not starved by other foreground work on the machine. Best
   * effort — failure just leaves Winamp at its default priority.
   */
  private void raisePriority(long pid) {
    HANDLE handle = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_SET_INFORMATION, false, (int) pid);
    if (handle == null) {
      LOG.warning("Unable to open winamp.exe (pid " + pid + ") to raise its process priority.");
      return;
    }
    try {
      if (!Kernel32.INSTANCE.SetPriorityClass(handle, new DWORD(HIGH_PRIORITY_CLASS))) {
        LOG.warning("Unable to set winamp.exe (pid " + pid + ") to high priority.");
      }
    } finally {
      Kernel32.INSTANCE.CloseHandle(handle);
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

      // Winamp was closed externally, crashed, or was killed as hung by another thread.
      if (hwnd == null) {
        status.set(SongPlayerStatus.STOPPED);
        if (lastPolledStatus == SongPlayerStatus.PLAYING) {
          reportPlaybackFailure("winamp.exe exited while the song was playing");
          lastPolledStatus = SongPlayerStatus.STOPPED;
          cancelPollTask();
          fireOnFinished();
        }
        return;
      }

      int raw = (int) sendIPC(hwnd, 0, IPC_ISPLAYING);
      SongPlayerStatus live = rawStatusToEnum(raw);

      if (live == SongPlayerStatus.PLAYING) {
        long posMs = sendIPC(hwnd, 0, IPC_GETOUTPUTTIME);

        // Belt-and-suspenders: if Winamp still reports "playing" but playback position has
        // already reached (or passed) the track length, treat it as finished. This guards
        // against IPC_ISPLAYING not flipping to stopped promptly at end-of-track.
        if (hasReachedEndOfTrack(hwnd, posMs)) {
          live = SongPlayerStatus.STOPPED;
        } else if (hasStalled(posMs)) {
          onPlaybackStalled(posMs);
          return;
        }
      } else {
        // Time spent paused (or stopped) does not count towards a stall.
        lastPositionChangeMs = System.currentTimeMillis();
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

    } catch (WinampUnresponsiveException e) {
      // sendIPC() has already reported the song and killed the hung winamp.exe; advance the queue
      // (the next playSongMedia() relaunches Winamp).
      status.set(SongPlayerStatus.STOPPED);
      lastPolledStatus = SongPlayerStatus.STOPPED;
      cancelPollTask();
      fireOnFinished();
    } catch (Throwable t) {
      // Must catch Throwable, not just Exception: scheduleAtFixedRate silently and
      // permanently stops future executions of this task if anything escapes the Runnable
      // uncaught, which would orphan end-of-track detection for the rest of the app's
      // lifetime. Logged at WARNING (not FINE) so a failure here is actually visible instead
      // of silently starving the queue.
      LOG.log(Level.WARNING, "Poll error (Winamp may have just closed)", t);
    }
  }

  /**
   * Returns {@code true} when the current playback position has reached or passed the known
   * track length. Used as a secondary end-of-track signal alongside IPC_ISPLAYING.
   */
  private boolean hasReachedEndOfTrack(HWND hwnd, long posMs) {
    long lengthMs = songLengthMs;
    if (lengthMs <= 0) {
      long lengthSec = sendIPC(hwnd, 1, IPC_GETOUTPUTTIME);
      if (lengthSec <= 0)
        return false;
      lengthMs = lengthSec * 1000L;
      songLengthMs = lengthMs;
    }
    return posMs >= 0 && posMs >= lengthMs;
  }

  /**
   * Returns {@code true} when Winamp reports PLAYING but the playback position has not changed for
   * {@link #stallMs} (e.g. its decoder is wedged on a corrupt file without hanging Winamp itself).
   */
  private boolean hasStalled(long posMs) {
    long now = System.currentTimeMillis();
    if (posMs != lastPositionMs) {
      lastPositionMs = posMs;
      lastPositionChangeMs = now;
      return false;
    }
    return stallMs > 0 && now - lastPositionChangeMs >= stallMs;
  }

  /**
   * Handles a stalled song: reports it, kills winamp.exe (a wedged decoder may not honor a Stop
   * command, and would leave Winamp reporting PLAYING forever), and advances the queue. The next
   * playSongMedia() relaunches Winamp.
   */
  private void onPlaybackStalled(long posMs) {
    LOG.warning("Playback stalled at " + posMs + " ms (no progress for " + (stallMs / 1000)
        + "s): " + currentSongPath);
    reportPlaybackFailure("playback stalled");
    killWinampProcesses("playback stalled");
    status.set(SongPlayerStatus.STOPPED);
    lastPolledStatus = SongPlayerStatus.STOPPED;
    cancelPollTask();
    fireOnFinished();
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
