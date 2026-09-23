package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * A wider, touch-friendly {@link javax.swing.JScrollBar} UI — bigger track width, a bigger
 * minimum thumb size, and bigger increment/decrement arrow buttons than the default look and
 * feel's scroll bar, which is sized for mouse precision rather than a fingertip. Supports both
 * vertical and horizontal scroll bars, and outlines the whole bar in the same gray as the thumb so
 * it reads as distinct from the black content area beside it. Used by
 * {@link LegacyAlbumGridPanel}'s per-album track-list scroll pane.
 */
class TouchScrollBarUI extends BasicScrollBarUI {

  private static final int THICKNESS = 32; // track width / arrow-button size (includes border)
  private static final int MIN_THUMB_LENGTH = 48;
  private static final int BORDER_WIDTH = 1;

  @Override
  protected void configureScrollBarColors() {
    trackColor = Color.BLACK;
    thumbColor = ColorTheme.get().textSecondary;
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    scrollbar.setBorder(BorderFactory.createLineBorder(thumbColor, BORDER_WIDTH));
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
        int inner = THICKNESS - 2 * BORDER_WIDTH;
        return new Dimension(inner, inner);
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
    if (isVertical()) {
      int arc = Math.max(6, thumbBounds.width - 8);
      g2.fillRoundRect(thumbBounds.x + 4, thumbBounds.y + 2, thumbBounds.width - 8,
          thumbBounds.height - 4, arc, arc);
    } else {
      int arc = Math.max(6, thumbBounds.height - 8);
      g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 4, thumbBounds.width - 4,
          thumbBounds.height - 8, arc, arc);
    }
    g2.dispose();
  }
}
