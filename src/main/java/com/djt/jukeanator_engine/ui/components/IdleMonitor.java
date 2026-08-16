package com.djt.jukeanator_engine.ui.components;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.Timer;

public class IdleMonitor {

  private static final long DEFAULT_IDLE_TIMEOUT_MS = 60_000;

  /**
   * How long (ms) to suppress the onActive callback after the screensaver is triggered. This
   * prevents stale AWT events that are already queued — or the OS-level repaint/focus events
   * produced when the ScreenSaverWindow becomes visible — from immediately dismissing the
   * screensaver.
   */
  private static final long ACTIVATION_GRACE_PERIOD_MS = 2_000;

  private final long idleTimeoutMs;

  private long lastActivity = System.currentTimeMillis();

  /** Timestamp of the most recent onIdle call, or 0 if never fired. */
  private long lastIdleFiredAt = 0;

  private final Timer timer;

  public IdleMonitor(Runnable onIdle, Runnable onActive) {
    this(DEFAULT_IDLE_TIMEOUT_MS, onIdle, onActive);
  }

  public IdleMonitor(long idleTimeoutMs, Runnable onIdle, Runnable onActive) {

    this.idleTimeoutMs = idleTimeoutMs;

    Toolkit.getDefaultToolkit().addAWTEventListener(e -> {

      if (e instanceof MouseEvent || e instanceof KeyEvent || e instanceof MouseWheelEvent) {

        lastActivity = System.currentTimeMillis();

        // Suppress onActive during the grace period immediately after the
        // screensaver was activated so that queued / system-generated events
        // cannot dismiss it before the user sees it.
        boolean inGracePeriod = lastIdleFiredAt > 0
            && (System.currentTimeMillis() - lastIdleFiredAt) < ACTIVATION_GRACE_PERIOD_MS;

        if (!inGracePeriod) {
          onActive.run();
        }
      }

    }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
        | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

    timer = new Timer(1000, e -> {

      if (System.currentTimeMillis() - lastActivity >= idleTimeoutMs) {

        lastIdleFiredAt = System.currentTimeMillis();
        onIdle.run();
      }
    });

    timer.start();
  }
}
