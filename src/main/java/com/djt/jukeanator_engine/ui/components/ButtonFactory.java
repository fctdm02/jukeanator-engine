package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;

public final class ButtonFactory {

  /** Corner radius of the blue navigation buttons. */
  private static final int NAV_ARC = 8;

  /**
   * How far a pressed navigation button's label moves down. The button shape itself stays put —
   * these buttons have no outer shadow to sink into and a fixed size.
   */
  private static final int NAV_LABEL_SHIFT = 1;

  public static JButton createNavigationButton(String text) {

    JButton button = new JButton(text) {

      private static final long serialVersionUID = 1L;
      private boolean hovered = false;

      {
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
        paintNavButtonBackground(g2, this, hovered);
        g2.dispose();
        paintLabelShifted(g, this, super::paintComponent);
      }
    };

    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeNavBtn));
    button.setForeground(Color.WHITE);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(LayoutTheme.get().navBtnW, LayoutTheme.get().navBtnH));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    return button;
  }

  /**
   * Creates a blue text button sized to its label (plus padding), used for the buttons on the Admin
   * Panel's in-window cards. Same face, hover and pressed look as {@link #createNavigationButton};
   * a disabled button is painted grey.
   *
   * @param text the button label
   * @param font the label font
   */
  static JButton createTextButton(String text, Font font) {

    JButton button = new JButton(text) {

      private static final long serialVersionUID = 1L;
      private boolean hovered = false;

      {
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
        if (isEnabled()) {
          paintNavButtonBackground(g2, this, hovered);
        } else {
          ColorTheme theme = ColorTheme.get();
          int w = getWidth();
          int h = getHeight();
          g2.setColor(theme.btnGreyFace);
          g2.fillRoundRect(0, 0, w, h, NAV_ARC, NAV_ARC);
          g2.setColor(theme.btnGreyBorder);
          g2.setStroke(new java.awt.BasicStroke(1.5f));
          g2.drawRoundRect(0, 0, w - 1, h - 1, NAV_ARC, NAV_ARC);
        }
        g2.dispose();
        paintLabelShifted(g, this, super::paintComponent);
      }
    };

    button.setFont(font);
    button.setForeground(ColorTheme.get().textPrimary);
    button.setContentAreaFilled(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setBorder(new EmptyBorder(6, 18, 6, 18));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    return button;
  }

  /**
   * Paints the shared blue navigation-button face: rounded gradient fill plus accent border. While
   * pressed (see {@link PressedEffect}) the face and border darken slightly and an inset shadow
   * appears inside the top and side edges, so the button reads as pushed into the screen.
   *
   * @param g2 graphics to paint with (anti-aliasing is expected to be enabled by the caller)
   * @param button the button being painted
   * @param hovered whether the pointer is currently over the button
   */
  static void paintNavButtonBackground(Graphics2D g2, AbstractButton button, boolean hovered) {

    ColorTheme theme = ColorTheme.get();
    boolean pressed = PressedEffect.isPressed(button);

    Color top;
    Color bottom;
    if (pressed) {
      top = PressedEffect.darken(theme.navBtnGradTop);
      bottom = PressedEffect.darken(theme.navBtnGradBottom);
    } else if (hovered) {
      top = theme.navBtnHoverTop;
      bottom = theme.navBtnHoverBottom;
    } else {
      top = theme.navBtnGradTop;
      bottom = theme.navBtnGradBottom;
    }

    int w = button.getWidth();
    int h = button.getHeight();
    g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
    g2.fillRoundRect(0, 0, w, h, NAV_ARC, NAV_ARC);

    if (pressed) {
      PressedEffect.paintInsetShadow(g2, 0, 0, w, h, NAV_ARC);
    }

    g2.setColor(pressed ? PressedEffect.darken(theme.accentBlue) : theme.accentBlue);
    g2.setStroke(new java.awt.BasicStroke(1.5f));
    g2.drawRoundRect(0, 0, w - 1, h - 1, NAV_ARC, NAV_ARC);
  }

  /**
   * Paints a navigation button's look-and-feel label (via {@code superPaint}, normally
   * {@code super::paintComponent}), moved down by {@link #NAV_LABEL_SHIFT} while the button is
   * pressed.
   */
  static void paintLabelShifted(Graphics g, AbstractButton button, Consumer<Graphics> superPaint) {
    Graphics labelGraphics = g.create();
    try {
      if (PressedEffect.isPressed(button)) {
        labelGraphics.translate(0, NAV_LABEL_SHIFT);
      }
      superPaint.accept(labelGraphics);
    } finally {
      labelGraphics.dispose();
    }
  }
}
