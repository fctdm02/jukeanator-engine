package com.djt.jukeanator_engine.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * It supports two gestures so it works in both touch-driver modes (see {@link TouchInput}):
 * <ul>
 * <li>Press and hold: the password is shown while the finger stays down and is masked again on
 * release. This needs "Mouse Emulation" mode, where the press arrives on touch-down.</li>
 * <li>Quick tap: the password is shown for {@link #TAP_REVEAL_MS} and then masked again. This also
 * covers "Click on Touch" mode, where press and release arrive together on lift.</li>
 * </ul>
 *
 * <p>
 * The component does not touch the password field itself. The owner supplies a callback that is
 * told whenever the reveal state changes and updates its field accordingly. Painted with Java2D so
 * it does not depend on an emoji or icon font being installed.
 */
public class PasswordPeekButton extends JComponent {

  private static final long serialVersionUID = 1L;

  /** How long a quick tap reveals the password. */
  public static final int TAP_REVEAL_MS = 3000;
  /** A press held at least this long counts as press-and-hold; the reveal ends on release. */
  public static final int HOLD_THRESHOLD_MS = 400;

  private final transient Consumer<Boolean> onRevealChanged;
  private final transient Runnable onInteraction;
  private final Timer hideTimer = new Timer(TAP_REVEAL_MS, e -> conceal());

  private boolean revealed = false;
  private long pressedAt;

  /**
   * @param onRevealChanged called with {@code true} when the password should be shown and
   *        {@code false} when it should be masked again
   * @param onInteraction called on every press (for example to reset an overlay timeout); may be
   *        {@code null}
   */
  public PasswordPeekButton(Consumer<Boolean> onRevealChanged, Runnable onInteraction) {
    this.onRevealChanged = onRevealChanged;
    this.onInteraction = onInteraction;
    hideTimer.setRepeats(false);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e))
          return;
        pressedAt = System.currentTimeMillis();
        hideTimer.stop();
        setRevealed(true);
        if (PasswordPeekButton.this.onInteraction != null)
          PasswordPeekButton.this.onInteraction.run();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e))
          return;
        long heldMs = System.currentTimeMillis() - pressedAt;
        if (heldMs >= HOLD_THRESHOLD_MS) {
          conceal();
        } else {
          hideTimer.restart();
        }
      }
    });
  }

  /** True while the password is being shown. */
  public boolean isRevealed() {
    return revealed;
  }

  /** Masks the password immediately (no-op if it is already masked). */
  public void conceal() {
    hideTimer.stop();
    setRevealed(false);
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
