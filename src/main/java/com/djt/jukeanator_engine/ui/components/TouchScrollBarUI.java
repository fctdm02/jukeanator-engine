package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * A wide, touch-friendly vertical scroll bar UI.
 *
 * <p>
 * Designed for touchscreen jukebox monitors where a standard 12-px scroll bar is too narrow to tap
 * reliably. Features:
 * <ul>
 * <li>{@value #BAR_WIDTH}px total width.</li>
 * <li>Large arrow buttons ({@value #BUTTON_HEIGHT}px tall) with clearly visible chevron
 * glyphs.</li>
 * <li>Rounded thumb with a subtle accent highlight.</li>
 * <li>Entire track + thumb area rendered in the jukebox dark palette.</li>
 * </ul>
 *
 * <p>
 * Apply to any {@link javax.swing.JScrollBar}:
 * 
 * <pre>
 * 
 * scrollBar.setUI(new TouchScrollBarUI());
 * scrollBar.setPreferredSize(new Dimension(TouchScrollBarUI.BAR_WIDTH, 0));
 * </pre>
 */
public class TouchScrollBarUI extends BasicScrollBarUI {

  // ── Dimensions ────────────────────────────────────────────────────────────
  /** Total width of the scroll bar (and height of each arrow button). */
  public static final int BAR_WIDTH = 48;
  public static final int BUTTON_HEIGHT = 48;
  private static final int THUMB_INSET = 4;
  private static final int THUMB_ARC = 8;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color BG_TRACK = new Color(28, 28, 38);
  private static final Color COLOR_THUMB = new Color(70, 70, 95);
  private static final Color COLOR_THUMB_HOT = new Color(0, 180, 220);
  private static final Color COLOR_BTN_BG = new Color(40, 40, 55);
  private static final Color COLOR_BTN_HOT = new Color(0, 160, 200);
  private static final Color COLOR_ARROW = Color.WHITE;
  private static final Color COLOR_BORDER = new Color(80, 80, 100);

  // ── Track ─────────────────────────────────────────────────────────────────
  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setColor(BG_TRACK);
    g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);

    // Left edge separator line
    g2.setColor(COLOR_BORDER);
    g2.drawLine(trackBounds.x, trackBounds.y, trackBounds.x, trackBounds.y + trackBounds.height);

    g2.dispose();
  }

  // ── Thumb ─────────────────────────────────────────────────────────────────
  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {

    if (thumbBounds.isEmpty() || !scrollbar.isEnabled())
      return;

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    boolean hot = isThumbRollover();
    Color fill = hot ? COLOR_THUMB_HOT : COLOR_THUMB;

    int x = thumbBounds.x + THUMB_INSET;
    int y = thumbBounds.y + 2;
    int w = thumbBounds.width - (THUMB_INSET * 2);
    int h = thumbBounds.height - 4;

    g2.setColor(fill);
    g2.fillRoundRect(x, y, w, h, THUMB_ARC, THUMB_ARC);

    // Subtle top highlight
    g2.setColor(new Color(255, 255, 255, 40));
    g2.fillRoundRect(x, y, w, h / 3, THUMB_ARC, THUMB_ARC);

    g2.dispose();
  }

  // ── Arrow buttons ─────────────────────────────────────────────────────────
  @Override
  protected JButton createDecreaseButton(int orientation) {
    return new TouchArrowButton(orientation, BAR_WIDTH, BUTTON_HEIGHT);
  }

  @Override
  protected JButton createIncreaseButton(int orientation) {
    return new TouchArrowButton(orientation, BAR_WIDTH, BUTTON_HEIGHT);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return new Dimension(BAR_WIDTH, BAR_WIDTH);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ARROW BUTTON
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Square touch-sized button that paints a centred filled triangle (up or down). Orientation:
   * NORTH = decrease (scroll up), SOUTH = increase (scroll down).
   */
  private static class TouchArrowButton extends JButton {

    private static final long serialVersionUID = 1L;

    private final int orientation;
    private boolean hovered = false;

    TouchArrowButton(int orientation, int width, int height) {

      this.orientation = orientation;
      setPreferredSize(new Dimension(width, height));
      setMinimumSize(new Dimension(width, height));
      setMaximumSize(new Dimension(width, height));
      setFocusPainted(false);
      setBorderPainted(false);
      setContentAreaFilled(false);
      setOpaque(false);

      addMouseListener(new java.awt.event.MouseAdapter() {
        @Override
        public void mouseEntered(java.awt.event.MouseEvent e) {
          hovered = true;
          repaint();
        }

        @Override
        public void mouseExited(java.awt.event.MouseEvent e) {
          hovered = false;
          repaint();
        }
      });
    }

    @Override
    protected void paintComponent(Graphics g) {

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();

      // Background
      g2.setColor(hovered ? COLOR_BTN_HOT : COLOR_BTN_BG);
      g2.fillRect(0, 0, w, h);

      // Separator line between button and track
      g2.setColor(COLOR_BORDER);
      if (orientation == javax.swing.SwingConstants.NORTH) {
        g2.drawLine(0, h - 1, w, h - 1);
      } else {
        g2.drawLine(0, 0, w, 0);
      }

      // Filled triangle centred in the button
      g2.setColor(hovered ? Color.BLACK : COLOR_ARROW);

      int cx = w / 2;
      int cy = h / 2;
      int aw = 14; // half-width of triangle base
      int ah = 9; // height of triangle

      int[] xPts, yPts;
      if (orientation == javax.swing.SwingConstants.NORTH) {
        // pointing up
        xPts = new int[] {cx, cx + aw, cx - aw};
        yPts = new int[] {cy - ah, cy + ah, cy + ah};
      } else {
        // pointing down
        xPts = new int[] {cx, cx + aw, cx - aw};
        yPts = new int[] {cy + ah, cy - ah, cy - ah};
      }

      g2.fillPolygon(xPts, yPts, 3);
      g2.dispose();
    }
  }
}
