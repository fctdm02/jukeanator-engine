package com.djt.jukeanator_engine.ui.components;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

/**
 * The open-caret (chevron) page up / page down button used under the result columns
 * ({@link ResultsColumnPanel}) and the album track list ({@link AlbumViewCard}).
 *
 * <p>
 * Idle it is just a white chevron. Hovering adds a faint highlight behind it. While pressed it gets
 * the shared {@link PressedEffect} motif: a recessed rounded face with an inset shadow, and the
 * chevron drops {@link #PRESS_DROP} px. The chevron colour never changes, except to the faint
 * disabled tint on the first / last page.
 */
final class ChevronButton {

  /** Corner radius of the hover / pressed face. */
  private static final int ARC = 8;

  /** How far a pressed chevron moves down. */
  private static final int PRESS_DROP = 2;

  private static final String HOVER_PROPERTY = "jukeanator.chevronHovered";

  private ChevronButton() {
  }

  /**
   * Creates a chevron page button.
   *
   * @param up {@code true} for the "previous / up" chevron, {@code false} for "next / down"
   * @param size preferred button size
   */
  static JButton create(boolean up, Dimension size) {

    JButton btn = new JButton() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        ColorTheme theme = ColorTheme.get();
        int w = getWidth();
        int h = getHeight();
        boolean enabled = isEnabled();
        boolean pressed = enabled && PressedEffect.isPressed(this);
        boolean hovered =
            enabled && !pressed && Boolean.TRUE.equals(getClientProperty(HOVER_PROPERTY));

        // Face — none when idle, faint when hovered, recessed when pressed
        if (pressed) {
          g2.setColor(theme.chevronPressedBg);
          g2.fillRoundRect(0, 0, w, h, ARC, ARC);
          PressedEffect.paintInsetShadow(g2, 0, 0, w, h, ARC);
        } else if (hovered) {
          g2.setColor(theme.chevronHoverBg);
          g2.fillRoundRect(0, 0, w, h, ARC, ARC);
        }

        // Chevron — wide open caret, dropped slightly while pressed
        g2.setColor(enabled ? theme.textPrimary : theme.trackNavDisabledTint);
        g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int leftX = Math.round(w * 0.32f);
        int rightX = w - leftX;
        int centerX = w / 2;
        int paddingY = Math.round(h * 0.34f);
        int drop = pressed ? PRESS_DROP : 0;
        int topY = paddingY + drop;
        int bottomY = h - paddingY + drop;

        if (up) {
          g2.drawLine(leftX, bottomY, centerX, topY);
          g2.drawLine(centerX, topY, rightX, bottomY);
        } else {
          g2.drawLine(leftX, topY, centerX, bottomY);
          g2.drawLine(centerX, bottomY, rightX, topY);
        }

        g2.dispose();
      }
    };

    btn.setOpaque(false);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setPreferredSize(size);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    btn.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        btn.putClientProperty(HOVER_PROPERTY, Boolean.TRUE);
        btn.repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        btn.putClientProperty(HOVER_PROPERTY, null);
        btn.repaint();
      }
    });

    return btn;
  }
}
