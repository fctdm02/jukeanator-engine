package com.djt.jukeanator_engine.ui.components;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Draws a small translucent circle where the screen is touched, similar to the touch feedback
 * Windows shows for USB touch screens. Enabled by {@code user-interface.touch-indicator}, for
 * legacy serial touch screens whose driver only emulates a mouse, so Windows shows no feedback.
 *
 * <p>
 * Installed as the frame's {@link javax.swing.JRootPane} glass pane, so it paints above every
 * card and overlay. {@link #contains(int, int)} always returns false, so the overlay never
 * receives mouse events and never changes the cursor; taps reach the components beneath it
 * unchanged. A global {@link java.awt.event.AWTEventListener} watches left-button presses, drags
 * and releases in the frame. The circle appears on press, follows the finger while it is held, and
 * fades out after release.
 */
public final class TouchIndicatorOverlay extends JComponent {

  private static final long serialVersionUID = 1L;

  private static final int DIAMETER = 48;
  private static final int PAD = 1; // anti-aliasing margin when repainting
  private static final float CENTER_OPACITY = 0.25f;
  // Radial gradient stops (center to edge) and relative strength at each: stays bright near the
  // center, then tapers smoothly to fully transparent at the edge.
  private static final float[] GLOW_FRACTIONS = {0f, 0.3f, 0.6f, 0.85f, 1f};
  private static final float[] GLOW_STRENGTHS = {1f, 0.85f, 0.45f, 0.12f, 0f};
  private static final long FADE_NANOS = 300_000_000L;
  private static final int FRAME_MILLIS = 16;

  private final JFrame frame;
  private final Timer fadeTimer;

  private Point center; // overlay coordinates; null when nothing to draw
  private boolean pressed;
  private float alpha;
  private long fadeStartNanos;

  private TouchIndicatorOverlay(JFrame frame) {
    this.frame = frame;
    setOpaque(false);
    this.fadeTimer = new Timer(FRAME_MILLIS, e -> stepFade());
    this.fadeTimer.setCoalesce(true);
  }

  /** Installs the overlay as {@code frame}'s glass pane and starts listening for touches. */
  public static TouchIndicatorOverlay install(JFrame frame) {
    TouchIndicatorOverlay overlay = new TouchIndicatorOverlay(frame);
    frame.setGlassPane(overlay);
    overlay.setVisible(true);
    Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
      if (event instanceof MouseEvent mouseEvent) {
        overlay.onMouseEvent(mouseEvent);
      }
    }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    return overlay;
  }

  /** Never a hit-test target, so events and cursors go to the components underneath. */
  @Override
  public boolean contains(int x, int y) {
    return false;
  }

  private void onMouseEvent(MouseEvent e) {
    Component source = e.getComponent();
    if (source == null || source == this || !SwingUtilities.isLeftMouseButton(e)
        || !SwingUtilities.isDescendingFrom(source, frame)) {
      return;
    }
    switch (e.getID()) {
      case MouseEvent.MOUSE_PRESSED -> {
        fadeTimer.stop();
        pressed = true;
        alpha = 1f;
        moveTo(SwingUtilities.convertPoint(source, e.getPoint(), this));
      }
      case MouseEvent.MOUSE_DRAGGED -> {
        if (pressed) {
          moveTo(SwingUtilities.convertPoint(source, e.getPoint(), this));
        }
      }
      case MouseEvent.MOUSE_RELEASED -> {
        // AWT can report the same release twice (frame and lightweight target); fade once.
        if (pressed) {
          pressed = false;
          fadeStartNanos = System.nanoTime();
          fadeTimer.start();
        }
      }
      default -> {
        // ignore clicks, moves, enters and exits
      }
    }
  }

  private void moveTo(Point p) {
    repaintCircle();
    center = p;
    repaintCircle();
  }

  private void stepFade() {
    float t = (System.nanoTime() - fadeStartNanos) / (float) FADE_NANOS;
    alpha = Math.max(0f, 1f - t);
    repaintCircle();
    if (alpha <= 0f) {
      fadeTimer.stop();
      center = null;
    }
  }

  private void repaintCircle() {
    if (center != null) {
      int r = DIAMETER / 2 + PAD;
      repaint(center.x - r, center.y - r, 2 * r, 2 * r);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (center == null || alpha <= 0f) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int x = center.x - DIAMETER / 2;
      int y = center.y - DIAMETER / 2;

      // Soft glow: whitest at the touch point, fading smoothly to fully transparent at the edge,
      // with no border. The fade-out alpha scales every stop.
      Color[] colors = new Color[GLOW_FRACTIONS.length];
      for (int i = 0; i < colors.length; i++) {
        int a = Math.round(255 * CENTER_OPACITY * GLOW_STRENGTHS[i] * alpha);
        colors[i] = new Color(255, 255, 255, a);
      }
      g2.setPaint(new RadialGradientPaint(center.x, center.y, DIAMETER / 2f, GLOW_FRACTIONS,
          colors));
      g2.fillOval(x, y, DIAMETER, DIAMETER);
    } finally {
      g2.dispose();
    }
  }
}
