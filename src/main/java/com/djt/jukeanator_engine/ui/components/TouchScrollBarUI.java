package com.djt.jukeanator_engine.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.AdjustmentListener;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * A wider, touch-friendly {@link javax.swing.JScrollBar} UI — bigger track width, a bigger
 * minimum thumb size, and bigger increment/decrement arrow buttons than the default look and
 * feel's scroll bar, which is sized for mouse precision rather than a fingertip. Supports both
 * vertical and horizontal scroll bars.
 *
 * <p>
 * Painted with a 3D look on a light background: the bar is outlined and filled in a frame color
 * (by default the white of the legacy CD card border, so the bar blends into the card's frame),
 * the track is a recessed groove, and the thumb and arrow buttons are beveled 3D black knobs —
 * the thumb carrying grip ridges and the buttons white chevrons that push in when pressed.
 * Installed on every JFC/Swing scroll pane through {@link ScrollBars} when
 * {@code user-interface.use-big-scrollbars} is on.
 */
class TouchScrollBarUI extends BasicScrollBarUI {

  private static final int THICKNESS = 32; // track width / arrow-button size (includes border)
  private static final int MIN_THUMB_LENGTH = 48;
  private static final int BORDER_WIDTH = 1;
  private static final int KNOB_ARC = 12;
  private static final int BUTTON_ARC = 10;
  private static final int GROOVE_ARC = 12;

  private final Color frameColor;

  /** Creates the UI with the default frame color (the legacy CD card's white border). */
  TouchScrollBarUI() {
    this(ColorTheme.get().legacyPanelBorder);
  }

  /**
   * Creates the UI with the given frame color — the bar's outline and the background around the
   * track groove and buttons. Pass the color of the surrounding panel's border so the bar reads as
   * part of that frame.
   */
  TouchScrollBarUI(Color frameColor) {
    this.frameColor = frameColor;
  }

  @Override
  protected void configureScrollBarColors() {
    trackColor = frameColor;
    thumbColor = ColorTheme.get().scrollKnobMid;
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    scrollbar.setBackground(frameColor);
    scrollbar.setBorder(BorderFactory.createLineBorder(frameColor, BORDER_WIDTH));
  }

  private boolean isVertical() {
    return scrollbar.getOrientation() == JScrollBar.VERTICAL;
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension d = super.getPreferredSize(c);
    return isVertical() ? new Dimension(THICKNESS, d.height) : new Dimension(d.width, THICKNESS);
  }

  @Override
  protected Dimension getMinimumThumbSize() {
    int inner = THICKNESS - 2 * BORDER_WIDTH;
    return isVertical() ? new Dimension(inner, MIN_THUMB_LENGTH)
        : new Dimension(MIN_THUMB_LENGTH, inner);
  }

  @Override
  protected JButton createDecreaseButton(int orientation) {
    return new KnobArrowButton(orientation);
  }

  @Override
  protected JButton createIncreaseButton(int orientation) {
    return new KnobArrowButton(orientation);
  }

  // ── Track: recessed groove ────────────────────────────────────────────────

  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(frameColor);
    g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);

    // Inset slightly from the buttons along the scroll direction, and by a pixel across it.
    float x, y, w, h;
    if (isVertical()) {
      x = trackBounds.x + 1;
      y = trackBounds.y + 2;
      w = trackBounds.width - 2;
      h = trackBounds.height - 4;
    } else {
      x = trackBounds.x + 2;
      y = trackBounds.y + 1;
      w = trackBounds.width - 4;
      h = trackBounds.height - 2;
    }
    if (w <= 0 || h <= 0) {
      g2.dispose();
      return;
    }
    RoundRectangle2D groove = new RoundRectangle2D.Float(x, y, w, h, GROOVE_ARC, GROOVE_ARC);

    // Fill: shaded on the top/left side, lit toward the bottom/right — reads as sunken. The shades
    // are derived from the frame color (white gives gray to white, red gives dark red to red), so
    // the knob's background always matches the card's theme.
    float[] fractions = {0f, 0.4f, 1f};
    Color[] colors = {grooveShade(), scale(frameColor, 0xf4 / 255f), frameColor};
    g2.setPaint(isVertical()
        ? new LinearGradientPaint(x, 0, x + w, 0, fractions, colors)
        : new LinearGradientPaint(0, y, 0, y + h, fractions, colors));
    g2.fill(groove);

    // Inner shadow: dark along the top/left rim fading to a white highlight on the bottom/right.
    g2.setStroke(new BasicStroke(1.5f));
    Color rimHighlight =
        new Color(frameColor.getRed(), frameColor.getGreen(), frameColor.getBlue(), 200);
    g2.setPaint(new GradientPaint(x, y, new Color(0, 0, 0, 140), x + w, y + h, rimHighlight));
    g2.draw(new RoundRectangle2D.Float(x + 0.75f, y + 0.75f, w - 1.5f, h - 1.5f, GROOVE_ARC,
        GROOVE_ARC));
    g2.setStroke(new BasicStroke(1f));
    g2.setPaint(new GradientPaint(x, y, new Color(0, 0, 0, 50), x + w * 0.5f, y + h * 0.5f,
        new Color(0, 0, 0, 0)));
    g2.draw(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, h - 4, GROOVE_ARC, GROOVE_ARC));
    g2.dispose();
  }

  /**
   * The groove's shaded edge: the theme's gray for the default white frame, otherwise the frame
   * color darkened by the same ratio (gray 0xbd against white 0xff).
   */
  private Color grooveShade() {
    Color shade = ColorTheme.get().scrollGrooveShade;
    if (Color.WHITE.equals(frameColor)) {
      return shade;
    }
    return scale(frameColor, shade.getRed() / 255f);
  }

  /** Multiplies each RGB channel by {@code factor}, clamped to 0..255. */
  private static Color scale(Color c, float factor) {
    return new Color(clamp(c.getRed() * factor), clamp(c.getGreen() * factor),
        clamp(c.getBlue() * factor));
  }

  private static int clamp(float v) {
    return Math.max(0, Math.min(255, Math.round(v)));
  }

  // ── Thumb: 3D black knob with grip ridges ─────────────────────────────────

  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
    if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    boolean vertical = isVertical();
    int x, y, w, h;
    if (vertical) {
      x = thumbBounds.x + 2;
      y = thumbBounds.y + 1;
      w = thumbBounds.width - 4;
      h = thumbBounds.height - 2;
    } else {
      x = thumbBounds.x + 1;
      y = thumbBounds.y + 2;
      w = thumbBounds.width - 2;
      h = thumbBounds.height - 4;
    }
    // Gradient runs across the bar's thickness so the knob looks rounded like a rod.
    Paint fill = knobGradient(x, y, vertical ? x + w : x, vertical ? y : y + h,
        isThumbRollover());
    paintKnob(g2, x, y, w, h, KNOB_ARC, fill, false);
    paintGripRidges(g2, x, y, w, h, vertical);
    g2.dispose();
  }

  private static void paintGripRidges(Graphics2D g2, int x, int y, int w, int h,
      boolean vertical) {
    Color groove = Color.BLACK;
    Color lip = new Color(0x77, 0x77, 0x77);
    int cx = x + w / 2;
    int cy = y + h / 2;
    g2.setStroke(new BasicStroke(1f));
    for (int off = -5; off <= 5; off += 5) {
      if (vertical) {
        int half = Math.max(4, w / 4);
        g2.setColor(groove);
        g2.drawLine(cx - half, cy + off, cx + half, cy + off);
        g2.setColor(lip);
        g2.drawLine(cx - half, cy + off + 1, cx + half, cy + off + 1);
      } else {
        int half = Math.max(4, h / 4);
        g2.setColor(groove);
        g2.drawLine(cx + off, cy - half, cx + off, cy + half);
        g2.setColor(lip);
        g2.drawLine(cx + off + 1, cy - half, cx + off + 1, cy + half);
      }
    }
  }

  // ── Shared knob painting (thumb and arrow buttons) ────────────────────────

  private static Paint knobGradient(float x1, float y1, float x2, float y2, boolean lifted) {
    ColorTheme t = ColorTheme.get();
    Color light = lifted ? t.scrollKnobLight.brighter() : t.scrollKnobLight;
    Color mid = lifted ? t.scrollKnobMid.brighter() : t.scrollKnobMid;
    Color dark = lifted ? t.scrollKnobDark.brighter() : t.scrollKnobDark;
    if (x1 == x2 && y1 == y2) {
      return mid;
    }
    return new LinearGradientPaint(x1, y1, x2, y2, new float[] {0f, 0.5f, 1f},
        new Color[] {light, mid, dark});
  }

  /**
   * Paints a beveled 3D knob: gradient body, a light rim on the top/left and dark rim on the
   * bottom/right (swapped when {@code pressed}), and a black outline.
   */
  private static void paintKnob(Graphics2D g2, int x, int y, int w, int h, int arc, Paint fill,
      boolean pressed) {
    if (w <= 2 || h <= 2) {
      return;
    }
    int a = Math.min(arc, Math.min(w, h));
    g2.setPaint(fill);
    g2.fill(new RoundRectangle2D.Float(x, y, w, h, a, a));

    Color highlight = new Color(255, 255, 255, 120);
    Color shadow = new Color(0, 0, 0, 230);
    g2.setStroke(new BasicStroke(1f));
    g2.setPaint(pressed ? new GradientPaint(x, y, shadow, x + w, y + h, highlight)
        : new GradientPaint(x, y, highlight, x + w, y + h, shadow));
    g2.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, w - 3f, h - 3f, a - 2, a - 2));

    g2.setColor(Color.BLACK);
    g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, a, a));
  }

  // ── Arrow buttons ─────────────────────────────────────────────────────────

  /** A 3D black knob button with a white chevron pointing in {@code direction}. */
  private final class KnobArrowButton extends JButton {
    private static final long serialVersionUID = 1L;
    private final int direction;

    KnobArrowButton(int direction) {
      this.direction = direction;
      setContentAreaFilled(false);
      setFocusPainted(false);
      setBorderPainted(false);
      setFocusable(false);
      setOpaque(false);
      setBorder(null);
    }

    @Override
    public Dimension getPreferredSize() {
      int inner = THICKNESS - 2 * BORDER_WIDTH;
      return new Dimension(inner, inner);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      g2.setColor(frameColor);
      g2.fillRect(0, 0, w, h);

      boolean atEnd = isAtEnd();
      boolean pressed = !atEnd && getModel().isArmed() && getModel().isPressed();
      paintKnob(g2, 0, 0, w, h, BUTTON_ARC, buttonGradient(w, h), pressed);

      // Chevron; nudged down/right while pressed so the button appears pushed in.
      float shift = pressed ? 1f : 0f;
      float cx = w / 2f + shift;
      float cy = h / 2f + shift;
      float s = Math.min(w, h) * 0.2f;
      Path2D chevron = new Path2D.Float();
      switch (direction) {
        case SwingConstants.NORTH -> {
          chevron.moveTo(cx - s, cy + s / 2);
          chevron.lineTo(cx, cy - s / 2);
          chevron.lineTo(cx + s, cy + s / 2);
        }
        case SwingConstants.SOUTH -> {
          chevron.moveTo(cx - s, cy - s / 2);
          chevron.lineTo(cx, cy + s / 2);
          chevron.lineTo(cx + s, cy - s / 2);
        }
        case SwingConstants.WEST -> {
          chevron.moveTo(cx + s / 2, cy - s);
          chevron.lineTo(cx - s / 2, cy);
          chevron.lineTo(cx + s / 2, cy + s);
        }
        default -> { // EAST
          chevron.moveTo(cx - s / 2, cy - s);
          chevron.lineTo(cx + s / 2, cy);
          chevron.lineTo(cx - s / 2, cy + s);
        }
      }
      g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // At the end of the track only the chevron changes: a darker shade of its normal color.
      Color chevronColor = ColorTheme.get().scrollChevron;
      g2.setColor(isEnabled() && !atEnd ? chevronColor : scale(chevronColor, 0.5f));
      g2.draw(chevron);
      g2.dispose();
    }

    /**
     * Whether the thumb is already at this button's end of the track, so pressing it can't
     * scroll any further; its chevron is then drawn in a darker, "disabled" shade.
     */
    private boolean isAtEnd() {
      if (scrollbar == null) {
        return false;
      }
      BoundedRangeModel m = scrollbar.getModel();
      return this == incrButton ? m.getValue() + m.getExtent() >= m.getMaximum()
          : m.getValue() <= m.getMinimum();
    }

    /**
     * The button body's gradient: 3D black for the default white frame, otherwise the frame's own
     * hue (e.g. 3D red on an explicit album's red frame).
     */
    private Paint buttonGradient(int w, int h) {
      ColorTheme t = ColorTheme.get();
      Color light, mid, dark;
      if (Color.WHITE.equals(frameColor)) {
        light = t.scrollKnobLight;
        mid = t.scrollKnobMid;
        dark = t.scrollKnobDark;
      } else {
        light = scale(frameColor, 1.3f);
        mid = frameColor;
        dark = scale(frameColor, 0.45f);
      }
      return new LinearGradientPaint(0, 0, w, h, new float[] {0f, 0.5f, 1f},
          new Color[] {light, mid, dark});
    }
  }

  // ── Repaint the arrow buttons as the thumb reaches / leaves either end ────

  private final AdjustmentListener endStateRepainter = e -> {
    if (incrButton != null) {
      incrButton.repaint();
    }
    if (decrButton != null) {
      decrButton.repaint();
    }
  };

  @Override
  protected void installListeners() {
    super.installListeners();
    scrollbar.addAdjustmentListener(endStateRepainter);
  }

  @Override
  protected void uninstallListeners() {
    scrollbar.removeAdjustmentListener(endStateRepainter);
    super.uninstallListeners();
  }
}
