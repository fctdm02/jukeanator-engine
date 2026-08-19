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
   * How long (ms) to suppress the onActive callback for passive mouse-motion events (move/enter/
   * exit) after the screensaver is triggered. This prevents the synthetic MOUSE_ENTERED/
   * MOUSE_MOVED event that Swing fires when the fullscreen ScreenSaverWindow appears under the
   * cursor from immediately dismissing it. Deliberate input — a press, click, wheel turn, or key
   * press — is never suppressed, so a real touch/click dismisses the screensaver with no lag.
   */
  private static final long ACTIVATION_GRACE_PERIOD_MS = 2_000;

  private long lastActivity = System.currentTimeMillis();

  /** Timestamp of the most recent onIdle call, or 0 if never fired. */
  private long lastIdleFiredAt = 0;

  private final Timer timer;

  public IdleMonitor(Runnable onIdle, Runnable onActive) {
    this(DEFAULT_IDLE_TIMEOUT_MS, onIdle, onActive);
  }

  public IdleMonitor(long idleTimeoutMs, Runnable onIdle, Runnable onActive) {

    Toolkit.getDefaultToolkit().addAWTEventListener(e -> {

      if (e instanceof MouseEvent || e instanceof KeyEvent || e instanceof MouseWheelEvent) {

        lastActivity = System.currentTimeMillis();

        // Only passive motion (no press/click/key behind it) is treated as
        // possibly a stale/synthetic event; deliberate input dismisses
        // instantly, with no grace-period lag.
        int id = ((AWTEvent) e).getID();
        boolean isPassiveMotion = id == MouseEvent.MOUSE_MOVED || id == MouseEvent.MOUSE_ENTERED
            || id == MouseEvent.MOUSE_EXITED;

        boolean inGracePeriod = isPassiveMotion && lastIdleFiredAt > 0
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
