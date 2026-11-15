package com.djt.jukeanator_engine.ui.components;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Eye icon placed beside a password field that temporarily reveals the password, so an operator
 * typing on the on-screen keyboard can check for mistyped characters.
 *
 * <p>
 * The password is shown only while the finger (or mouse button) is held down on the icon and is
 * masked again the moment it is released. This relies on the touch driver's "Mouse Emulation"
 * mode, where the press arrives on touch-down (see {@link TouchInput}).
 *
 * <p>
 * The release is also watched for globally, so it is caught even when AWT delivers it to another
 * component, and a reveal is capped at {@link #MAX_REVEAL_MS} in case no release is reported at
 * all.
 *
 * <p>
 * The component does not touch the password field itself. The owner supplies a callback that is
 * told whenever the reveal state changes and updates its field accordingly. Painted with Java2D so
 * it does not depend on an emoji or icon font being installed.
 */
public class PasswordPeekButton extends JComponent {

  private static final long serialVersionUID = 1L;

  /** Upper bound on a reveal, in case the release is never reported. */
  public static final int MAX_REVEAL_MS = 15000;

  private final transient Consumer<Boolean> onRevealChanged;
  private final transient Runnable onInteraction;
  /** Backstop only: masks the password after {@link #MAX_REVEAL_MS} if no release was seen. */
  private final Timer safetyTimer = new Timer(MAX_REVEAL_MS, e -> conceal());
  /**
   * Catches the release wherever AWT delivers it, not only on this component. Installed only while
   * a press is in progress.
   */
  private final transient AWTEventListener releaseWatcher = event -> {
    if (event.getID() == MouseEvent.MOUSE_RELEASED && event instanceof MouseEvent me
        && SwingUtilities.isLeftMouseButton(me)) {
      onRelease();
    }
  };

  private boolean revealed = false;
  private boolean releaseWatcherInstalled = false;

  /**
   * @param onRevealChanged called with {@code true} when the password should be shown and
   *        {@code false} when it should be masked again
   * @param onInteraction called on every press (for example to reset an overlay timeout); may be
   *        {@code null}
   */
  public PasswordPeekButton(Consumer<Boolean> onRevealChanged, Runnable onInteraction) {
    this.onRevealChanged = onRevealChanged;
    this.onInteraction = onInteraction;
    safetyTimer.setRepeats(false);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e))
          onPress();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e))
          onRelease();
      }
    });
  }

  /** True while the password is being shown. */
  public boolean isRevealed() {
    return revealed;
  }

  /** Masks the password immediately (no-op if it is already masked). */
  public void conceal() {
    safetyTimer.stop();
    uninstallReleaseWatcher();
    setRevealed(false);
  }

  private void onPress() {
    if (onInteraction != null)
      onInteraction.run();
    installReleaseWatcher();
    setRevealed(true);
    safetyTimer.restart();
  }

  /** Any left-button release ends the reveal (a duplicate report of it is a harmless no-op). */
  private void onRelease() {
    conceal();
  }

  private void installReleaseWatcher() {
    if (!releaseWatcherInstalled) {
      Toolkit.getDefaultToolkit().addAWTEventListener(releaseWatcher, AWTEvent.MOUSE_EVENT_MASK);
      releaseWatcherInstalled = true;
    }
  }

  private void uninstallReleaseWatcher() {
    if (releaseWatcherInstalled) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(releaseWatcher);
      releaseWatcherInstalled = false;
    }
  }

  private void setRevealed(boolean value) {
    if (revealed == value)
      return;
    revealed = value;
    onRevealChanged.accept(value);
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int w = getWidth();
    int h = getHeight();

    // Background and border to match the field it sits beside
    g2.setColor(ColorTheme.get().bgFieldDark);
    g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
    Color accent = revealed ? ColorTheme.get().accentBlue : ColorTheme.get().textPrimary;
    g2.setColor(accent);
    g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

    // Eye outline (two arcs) and pupil
    float stroke = Math.max(1.5f, h / 24f);
    g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    double eyeW = w * 0.48;
    double eyeH = h * 0.28;
    double cx = w / 2.0;
    double cy = h / 2.0;
    Path2D eye = new Path2D.Double();
    eye.moveTo(cx - eyeW / 2, cy);
    eye.quadTo(cx, cy - eyeH, cx + eyeW / 2, cy);
    eye.quadTo(cx, cy + eyeH, cx - eyeW / 2, cy);
    eye.closePath();
    g2.draw(eye);

    double pupil = h * 0.14;
    g2.fill(new Ellipse2D.Double(cx - pupil / 2, cy - pupil / 2, pupil, pupil));

    // Slash while masked
    if (!revealed) {
      double s = h * 0.23;
      g2.draw(new Line2D.Double(cx - s, cy + s, cx + s, cy - s));
    }
    g2.dispose();
  }
}
