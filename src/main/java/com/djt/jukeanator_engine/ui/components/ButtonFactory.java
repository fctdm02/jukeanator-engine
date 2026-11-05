package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JButton;

public final class ButtonFactory {

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
        super.paintComponent(g);
      }
    };

    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeNavBtn));
    button.setForeground(Color.WHITE);
    installPressedForeground(button, Color.WHITE);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(LayoutTheme.get().navBtnW, LayoutTheme.get().navBtnH));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    return button;
  }

  /**
   * Returns true while the button is held down with the pointer still over it — the same state in
   * which releasing would fire the action.
   */
  static boolean isPressed(AbstractButton button) {
    ButtonModel model = button.getModel();
    return model.isArmed() && model.isPressed();
  }

  /**
   * Paints the shared blue navigation-button face: rounded gradient fill plus accent border. While
   * pressed the face lightens 25% toward white and the border takes the idle face gradient, so a
   * touchscreen tap gives clear visual feedback.
   *
   * @param g2 graphics to paint with (anti-aliasing is expected to be enabled by the caller)
   * @param button the button being painted
   * @param hovered whether the pointer is currently over the button
   */
  static void paintNavButtonBackground(Graphics2D g2, AbstractButton button, boolean hovered) {

    ColorTheme theme = ColorTheme.get();
    boolean pressed = isPressed(button);

    Color top;
    Color bottom;
    if (pressed) {
      top = theme.navBtnPressedTop;
      bottom = theme.navBtnPressedBottom;
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
    g2.fillRoundRect(0, 0, w, h, 8, 8);

    // Pressed: the border takes the idle face gradient, so the lighter face reads as "pushed in".
    if (pressed) {
      g2.setPaint(new GradientPaint(0, 0, theme.navBtnGradTop, 0, h, theme.navBtnGradBottom));
    } else {
      g2.setColor(theme.accentBlue);
    }
    g2.setStroke(new java.awt.BasicStroke(1.5f));
    g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
  }

  /**
   * Swaps the button's text color to {@link ColorTheme#navBtnPressedText} while it is pressed and
   * back to {@code normalForeground} on release. Done from a model listener rather than inside
   * {@code paintComponent} so that changing the foreground never triggers a repaint loop.
   *
   * @param button the button to watch
   * @param normalForeground the text color to use when the button is not pressed
   */
  static void installPressedForeground(AbstractButton button, Color normalForeground) {
    button.getModel().addChangeListener(e -> {
      Color wanted = isPressed(button) ? ColorTheme.get().navBtnPressedText : normalForeground;
      if (!wanted.equals(button.getForeground())) {
        button.setForeground(wanted);
      }
    });
  }
}
