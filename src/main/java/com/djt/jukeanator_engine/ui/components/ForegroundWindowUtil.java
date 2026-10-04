package com.djt.jukeanator_engine.ui.components;

import java.awt.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector.OSType;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Forces a Swing window into the Windows foreground at startup.
 *
 * <p>
 * Windows' foreground-lock rules only let a process activate its own window if it is already the
 * foreground process or received the last input event. At startup that is the command prompt that
 * launched the application (or Winamp, which is launched before the UI), so {@link Window#toFront()}
 * and {@link Window#requestFocus()} are silently ignored and the UI stays behind the console. This
 * class works around the lock with the two standard Win32 techniques: attaching to the foreground
 * window's input queue via {@code AttachThreadInput}, and, if that is not enough, synthesizing an
 * Alt key tap so this process counts as having received the last input event.
 *
 * <p>
 * A no-op on non-Windows platforms (see {@link #isApplicable()}); any native failure is logged and
 * swallowed.
 */
final class ForegroundWindowUtil {

  private static final Logger log = LoggerFactory.getLogger(ForegroundWindowUtil.class);

  private static final boolean IS_WINDOWS =
      OperatingSystemDetector.getOperatingSystem() == OSType.WINDOWS;

  private static final int SW_SHOW = 5;
  private static final int SW_RESTORE = 9;
  private static final byte VK_MENU = 0x12;
  private static final int KEYEVENTF_EXTENDEDKEY = 0x0001;
  private static final int KEYEVENTF_KEYUP = 0x0002;

  // JNA interface — subset of User32 we need. Loaded lazily, on first use, so it is never
  // touched on non-Windows platforms.
  interface User32Fg extends StdCallLibrary {
    User32Fg INSTANCE = Native.load("user32", User32Fg.class);

    HWND GetForegroundWindow();

    boolean SetForegroundWindow(HWND hWnd);

    boolean BringWindowToTop(HWND hWnd);

    boolean ShowWindow(HWND hWnd, int nCmdShow);

    boolean IsIconic(HWND hWnd);

    int GetWindowThreadProcessId(HWND hWnd, Pointer lpdwProcessId);

    boolean AttachThreadInput(int idAttach, int idAttachTo, boolean fAttach);

    void keybd_event(byte bVk, byte bScan, int dwFlags, Pointer dwExtraInfo);
  }

  private ForegroundWindowUtil() {}

  /**
   * Whether the startup foreground workarounds apply on this platform. Only Windows has the
   * foreground lock; on macOS, toggling a full-screen window's always-on-top state would also reset
   * the shielding window level that {@code GraphicsDevice#setFullScreenWindow} gives it (hiding it
   * behind the display-capture shield), so callers must skip those workarounds elsewhere.
   */
  static boolean isApplicable() {
    return IS_WINDOWS;
  }

  /**
   * Makes {@code window} the Windows foreground window (activated, on top, with keyboard focus).
   * Must be called on the EDT after the window is displayable.
   */
  static void forceToForeground(Window window) {

    if (!IS_WINDOWS || window == null || !window.isDisplayable()) {
      return;
    }

    try {
      User32Fg user32 = User32Fg.INSTANCE;
      HWND hwnd = new HWND(Native.getWindowPointer(window));

      if (user32.IsIconic(hwnd)) {
        user32.ShowWindow(hwnd, SW_RESTORE);
      }

      HWND foreground = user32.GetForegroundWindow();
      if (hwnd.equals(foreground)) {
        return;
      }

      int currentThread = Kernel32.INSTANCE.GetCurrentThreadId();
      int foregroundThread =
          foreground == null ? 0 : user32.GetWindowThreadProcessId(foreground, null);
      boolean attached = foregroundThread != 0 && foregroundThread != currentThread
          && user32.AttachThreadInput(currentThread, foregroundThread, true);

      try {
        user32.ShowWindow(hwnd, SW_SHOW);
        user32.BringWindowToTop(hwnd);
        user32.SetForegroundWindow(hwnd);

        if (!hwnd.equals(user32.GetForegroundWindow())) {
          // Still locked out: a synthetic Alt tap makes this process the one that received the
          // last input event, which lifts the foreground lock for the next SetForegroundWindow.
          user32.keybd_event(VK_MENU, (byte) 0, KEYEVENTF_EXTENDEDKEY, null);
          user32.keybd_event(VK_MENU, (byte) 0, KEYEVENTF_EXTENDEDKEY | KEYEVENTF_KEYUP, null);
          user32.BringWindowToTop(hwnd);
          user32.SetForegroundWindow(hwnd);
        }
      } finally {
        if (attached) {
          user32.AttachThreadInput(currentThread, foregroundThread, false);
        }
      }

      log.info("forceToForeground: '{}' is foreground: {}", windowName(window),
          hwnd.equals(user32.GetForegroundWindow()));

    } catch (Throwable t) {
      log.warn("forceToForeground: could not bring '{}' to the foreground: {}",
          windowName(window), t.toString());
    }
  }

  private static String windowName(Window window) {
    if (window instanceof java.awt.Frame frame) {
      return frame.getTitle();
    }
    if (window instanceof java.awt.Dialog dialog) {
      return dialog.getTitle();
    }
    return window.getClass().getSimpleName();
  }
}
