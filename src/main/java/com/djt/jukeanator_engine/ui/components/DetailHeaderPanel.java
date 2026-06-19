package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class DetailHeaderPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Colours — sourced from ColorTheme.get() ──────────────────────────────

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public DetailHeaderPanel(String buttonText, Runnable onBack, Icon image, String fallbackText,
      String title, String subtitle) {
    this(buttonText, onBack, image, fallbackText, title, subtitle, null);
  }

  public DetailHeaderPanel(String buttonText, Runnable onBack, Icon image, String fallbackText,
      String title, String subtitle, JPanel eastPanel) {

    setLayout(new BorderLayout(16, 0));
    setOpaque(false);

    setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTheme.get().detailHeaderBorder),
        new EmptyBorder(12, 16, 12, 16)));

    //
    // BACK BUTTON
    //
    if (buttonText != null && onBack != null) {

      JButton backBtn = createBackButton(buttonText);
      backBtn.addActionListener(e -> onBack.run());

      add(backBtn, BorderLayout.WEST);
    }

    //
    // IMAGE
    //
    JLabel imageLabel = new JLabel();
    imageLabel.setPreferredSize(
        new Dimension(LayoutTheme.get().detailHeaderImageW, LayoutTheme.get().detailHeaderImageH));
    imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
    imageLabel.setVerticalAlignment(SwingConstants.CENTER);
    imageLabel.setOpaque(true);
    imageLabel.setBackground(ColorTheme.get().detailHeaderImageBg);
    imageLabel.setBorder(BorderFactory.createLineBorder(ColorTheme.get().detailHeaderBorder, 1));

    if (image != null) {

      imageLabel.setIcon(image);

    } else {

      imageLabel.setText(fallbackText);
      imageLabel.setForeground(ColorTheme.get().detailHeaderImageFg);
      imageLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32)); // fallback symbol — not scaled
    }

    //
    // TEXT
    //
    JPanel textBlock = new JPanel();
    textBlock.setOpaque(false);
    textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));

    JLabel titleLabel = new JLabel(title != null ? title : "");
    titleLabel.setForeground(ColorTheme.get().textPrimary);
    titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeDetailTitle));

    JLabel subtitleLabel = new JLabel(subtitle != null ? subtitle : "");
    subtitleLabel.setForeground(ColorTheme.get().textSecondary);
    subtitleLabel
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeDetailSubtitle));

    textBlock.add(titleLabel);
    textBlock.add(Box.createVerticalStrut(4));
    textBlock.add(subtitleLabel);

    //
    // CLUSTER
    //
    JPanel infoCluster = new JPanel(new BorderLayout(12, 0));
    infoCluster.setOpaque(false);

    infoCluster.add(imageLabel, BorderLayout.WEST);
    infoCluster.add(textBlock, BorderLayout.CENTER);

    add(infoCluster, BorderLayout.CENTER);

    if (eastPanel != null) {
      eastPanel.setOpaque(false);
      add(eastPanel, BorderLayout.EAST);
    }
  }

  private JButton createBackButton(String text) {

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

        // Gradient fill — brighter on hover
        Color top = hovered ? ColorTheme.get().navBtnHoverTop : ColorTheme.get().navBtnGradTop;
        Color bottom =
            hovered ? ColorTheme.get().navBtnHoverBottom : ColorTheme.get().navBtnGradBottom;
        g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

        // Accent border
        g2.setColor(ColorTheme.get().accentBlue);
        g2.setStroke(new java.awt.BasicStroke(1.5f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        g2.dispose();
        super.paintComponent(g);
      }
    };

    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeNavBtn));
    button.setForeground(ColorTheme.get().textPrimary);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(
        new Dimension(LayoutTheme.get().detailBackBtnW, LayoutTheme.get().detailBackBtnH));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    return button;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Frosted-glass backing plate matching GenrePanel genre tile resting state
    g2.setColor(ColorTheme.get().bgFrostedGlassRest);
    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);

    // Subtle perimeter highlight ring
    g2.setColor(ColorTheme.get().bgFrostedGlassRing);
    g2.setStroke(new java.awt.BasicStroke(1.0f));
    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);

    g2.dispose();
    super.paintComponent(g);
  }
}
