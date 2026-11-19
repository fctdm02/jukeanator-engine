package com.djt.jukeanator_engine.ui.components;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicPasswordFieldUI;
import javax.swing.text.Element;
import javax.swing.text.PasswordView;
import javax.swing.text.View;

/**
 * Password field with a built-in "peek" eye icon painted inside its right edge, so an operator
 * typing on the on-screen keyboard can check for mistyped characters.
 *
 * <p>
 * The icon is part of the field itself: the field reserves room for it on the right (on top of
 * whatever border is set, see {@link #getInsets()}), so the text never runs under it and the field
 * occupies the same footprint as an ordinary text field with the same border.
 *
 * <p>
 * The password is shown only while the finger (or mouse button) is held down on the icon and is
 * masked again the moment it is released. This relies on the touch driver's "Mouse Emulation"
 * mode, where the press arrives on touch-down (see {@link TouchInput}). The release is also
 * watched for globally, so it is caught even when AWT delivers it to another component, and a
 * reveal is capped at {@link #MAX_REVEAL_MS} in case no release is reported at all.
 *
 * <p>
 * The icon is painted with Java2D so it does not depend on an emoji or icon font being installed.
 * It is sized from the field's font, so it scales with the text: the icon is about as tall as the
 * caret, sits half a character-height from the text area, and the border's own right padding is
 * the gap between the icon and the field's edge.
 */
public class PeekPasswordField extends JPasswordField {

  private static final long serialVersionUID = 1L;

  /** Upper bound on a reveal, in case the release is never reported. */
  public static final int MAX_REVEAL_MS = 15000;

  /** Icon width relative to its height (the eye is slightly wider than tall). */
  private static final double ICON_ASPECT = 1.15;
  /** Gap between the text area and the icon, relative to the icon height. */
  private static final double ICON_GAP = 0.5;

  /**
   * Echo character. Only its advance width is used (so caret and click positions stay right); each
   * one is painted as a filled circle instead of the font's small bullet glyph.
   */
  private static final char DOT_CHAR = '•';
  /** Dot diameter relative to the echo character's advance width (the rest is the gap). */
  private static final float DOT_FILL = 0.8f;

  /** Backstop only: masks the password after {@link #MAX_REVEAL_MS} if no release was seen. */
  private final Timer safetyTimer = new Timer(MAX_REVEAL_MS, e -> conceal());
  /**
   * Catches the release wherever AWT delivers it, not only on this field. Installed only while a
   * press on the icon is in progress.
   */
  private final transient AWTEventListener releaseWatcher = event -> {
    if (event.getID() == MouseEvent.MOUSE_RELEASED && event instanceof MouseEvent me
        && SwingUtilities.isLeftMouseButton(me)) {
      conceal();
    }
  };

  private transient Runnable onPeekPressed;
  private boolean revealed;
  private boolean releaseWatcherInstalled;
  /** The echo character in use before the current reveal, restored when it ends. */
  private char maskChar;

  public PeekPasswordField() {
    this(0);
  }

  public PeekPasswordField(int columns) {
    super(columns);
    // Sets the dot pitch; the dots themselves are painted larger (see DotPasswordView)
    setEchoChar(DOT_CHAR);
    safetyTimer.setRepeats(false);

    MouseAdapter peekMouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && isOverIcon(e.getX()))
          onPress();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e))
          conceal();
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        setCursor(isOverIcon(e.getX()) ? KioskCursors.hand() : KioskCursors.text());
      }
    };
    addMouseListener(peekMouse);
    addMouseMotionListener(peekMouse);
  }

  /**
   * @param onPeekPressed called on every press of the eye icon (for example to reset an overlay
   *        timeout); may be {@code null}
   */
  public void setOnPeekPressed(Runnable onPeekPressed) {
    this.onPeekPressed = onPeekPressed;
  }

  /** True while the password is being shown. */
  public boolean isRevealed() {
    return revealed;
  }

  /** Masks the password immediately (no-op if it is already masked). */
  public void conceal() {
    safetyTimer.stop();
    uninstallReleaseWatcher();
    if (revealed) {
      revealed = false;
      setEchoChar(maskChar);
      repaint();
    }
  }

  @Override
  public void removeNotify() {
    conceal();
    super.removeNotify();
  }

  private void onPress() {
    if (onPeekPressed != null)
      onPeekPressed.run();
    installReleaseWatcher();
    if (!revealed) {
      maskChar = getEchoChar();
      revealed = true;
      setEchoChar((char) 0);
      repaint();
    }
    safetyTimer.restart();
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

  // ── Large dots ───────────────────────────────────────────────────────────

  /**
   * Installs a password UI whose view paints each masked character as a large filled dot, roughly a
   * quarter of the line height across, centred on the middle of the caret.
   */
  @Override
  public void updateUI() {
    setUI(new BasicPasswordFieldUI() {
      @Override
      public View create(Element elem) {
        return new DotPasswordView(elem);
      }
    });
  }

  private static final class DotPasswordView extends PasswordView {

    DotPasswordView(Element elem) {
      super(elem);
    }

    @Override
    protected float drawEchoCharacter(Graphics2D g, float x, float y, char c) {
      FontMetrics fm = g.getFontMetrics();
      float advance = fm.charWidth(c);
      float d = advance * DOT_FILL;
      float cx = x + advance / 2f;
      // Middle of the caret, which spans ascent above the baseline to descent below it
      float cy = y - (fm.getAscent() - fm.getDescent()) / 2f;

      Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.fill(new Ellipse2D.Float(cx - d / 2f, cy - d / 2f, d, d));
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
      return x + advance;
    }
  }

  // ── Layout: reserve room for the icon inside the field ───────────────────

  /** Icon height, taken from the font so the icon is about as tall as the caret. */
  private int iconHeight() {
    FontMetrics fm = getFontMetrics(getFont());
    return fm.getHeight();
  }

  private int iconWidth() {
    return (int) Math.round(iconHeight() * ICON_ASPECT);
  }

  private int iconGap() {
    return (int) Math.round(iconHeight() * ICON_GAP);
  }

  /** Extra right inset (gap plus icon) added to the border's own insets. */
  private int iconInset() {
    return getFont() == null ? 0 : iconGap() + iconWidth();
  }

  /** The border's insets plus room on the right for the icon, so text never runs under it. */
  @Override
  public Insets getInsets() {
    Insets in = super.getInsets();
    return new Insets(in.top, in.left, in.bottom, in.right + iconInset());
  }

  @Override
  public Insets getInsets(Insets insets) {
    Insets in = super.getInsets(insets);
    in.right += iconInset();
    return in;
  }

  /** Where the icon is painted: right-aligned inside the border's padding, vertically centered. */
  private Rectangle iconBounds() {
    int h = iconHeight();
    int w = iconWidth();
    int borderRight = super.getInsets().right;
    return new Rectangle(getWidth() - borderRight - w, (getHeight() - h) / 2, w, h);
  }

  /** The press target: from halfway into the gap before the icon to the field's right edge. */
  private boolean isOverIcon(int x) {
    return getFont() != null && x >= iconBounds().x - iconGap() / 2;
  }

  // ── Painting ─────────────────────────────────────────────────────────────

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      paintEye(g2, iconBounds());
    } finally {
      g2.dispose();
    }
  }

  /**
   * Eye outline with a ringed iris; while masked, a diagonal slash from top-left to bottom-right
   * cuts through it (separated from the eye by a thin gap in the field's background colour).
   */
  private void paintEye(Graphics2D g2, Rectangle r) {

    double h = r.height;
    float stroke = (float) Math.max(1.5, h * 0.09);
    double pad = stroke / 2.0;
    double left = r.x + pad;
    double right = r.x + r.width - pad;
    double cx = r.x + r.width / 2.0;
    double cy = r.y + h / 2.0;
    // Quadratic control points at +/-0.62h put the eye's top and bottom at +/-0.31h
    double ctrl = h * 0.62;

    Color color = revealed ? ColorTheme.get().accentBlue : getForeground();
    BasicStroke pen = new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    g2.setStroke(pen);
    g2.setColor(color);

    Path2D eye = new Path2D.Double();
    eye.moveTo(left, cy);
    eye.quadTo(cx, cy - ctrl, right, cy);
    eye.quadTo(cx, cy + ctrl, left, cy);
    eye.closePath();
    g2.draw(eye);

    double iris = h * 0.19;
    g2.draw(new Ellipse2D.Double(cx - iris, cy - iris, iris * 2, iris * 2));

    if (!revealed) {
      double inset = r.width * 0.12;
      Line2D slash = new Line2D.Double(r.x + inset, r.y + pad, r.x + r.width - inset,
          r.y + h - pad);
      // Background-coloured halo first, so the slash reads as cutting through the eye
      g2.setColor(getBackground());
      g2.setStroke(new BasicStroke(stroke * 2.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
      g2.draw(slash);
      g2.setColor(color);
      g2.setStroke(pen);
      g2.draw(slash);
    }
  }
}
