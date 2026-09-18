package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * A wider, touch-friendly {@link javax.swing.JScrollBar} UI — bigger track width, a bigger
 * minimum thumb size, and bigger increment/decrement arrow buttons than the default look and
 * feel's scroll bar, which is sized for mouse precision rather than a fingertip. Used by
 * {@link LegacyAlbumGridPanel}'s per-album track-list scroll pane.
 */
class TouchScrollBarUI extends BasicScrollBarUI {

  private static final int THICKNESS = 32; // track width / arrow-button size
  private static final int MIN_THUMB_LENGTH = 48;

  @Override
  protected void configureScrollBarColors() {
    trackColor = Color.BLACK;
    thumbColor = ColorTheme.get().textSecondary;
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension d = super.getPreferredSize(c);
    return new Dimension(THICKNESS, d.height);
  }

  @Override
  protected Dimension getMinimumThumbSize() {
    return new Dimension(THICKNESS, MIN_THUMB_LENGTH);
  }

  @Override
  protected JButton createDecreaseButton(int orientation) {
    return touchArrowButton(orientation);
  }

  @Override
  protected JButton createIncreaseButton(int orientation) {
    return touchArrowButton(orientation);
  }

  private JButton touchArrowButton(int orientation) {
    BasicArrowButton btn = new BasicArrowButton(orientation, Color.BLACK,
        ColorTheme.get().detailHeaderBorder, ColorTheme.get().textSecondary,
        ColorTheme.get().textPrimary) {
      private static final long serialVersionUID = 1L;

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(THICKNESS, THICKNESS);
      }
    };
    btn.setBorder(null);
    return btn;
  }

  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setColor(trackColor);
    g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
    g2.dispose();
  }

  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
    if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(isThumbRollover() ? ColorTheme.get().textPrimary : thumbColor);
    int arc = Math.max(6, thumbBounds.width - 8);
    g2.fillRoundRect(thumbBounds.x + 4, thumbBounds.y + 2, thumbBounds.width - 8,
        thumbBounds.height - 4, arc, arc);
    g2.dispose();
  }
}
