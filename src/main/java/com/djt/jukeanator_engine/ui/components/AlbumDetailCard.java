package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JPanel;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;

public class AlbumDetailCard extends JPanel {

  private static final long serialVersionUID = 1L;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public AlbumDetailCard(Frame owner, AlbumDto album, ImageLoader imageLoader,
      SongQueueService songQueueService, int priorityCostMultiplier, int threshold1, int threshold2,
      int threshold3, TabNavigator navigator, CreditManager creditManager,
      char incrementCreditsKey) {

    setLayout(new BorderLayout());
    setOpaque(false);

    AlbumViewCard.SongClickListener songClick = song -> {
      if (owner instanceof JukeANatorFrame frame) {
        frame.showAddSongToQueueCard(song);
      }
    };

    AlbumViewCard albumView =
        new AlbumViewCard(album, imageLoader, threshold1, threshold2, threshold3, songClick);

    add(albumView, BorderLayout.CENTER);
    add(buildFooter(navigator), BorderLayout.SOUTH);
  }

  private JPanel buildFooter(TabNavigator navigator) {

    JPanel footer = new JPanel(new BorderLayout(12, 0));
    footer.setOpaque(false);

    //
    // LEFT SIDE BUTTONS
    //
    JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
    buttons.setOpaque(false);

    JButton backButton = createBackButton("← Back", navigator::popToRoot);
    buttons.add(backButton);

    footer.add(buttons, BorderLayout.WEST);

    return footer;
  }

  private JButton createBackButton(String text, Runnable action) {

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
    button.addActionListener(e -> action.run());

    return button;
  }
}
