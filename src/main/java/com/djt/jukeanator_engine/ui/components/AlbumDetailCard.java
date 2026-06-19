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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;

public class AlbumDetailCard extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Timeout — sourced from LayoutTheme ───────────────────────────────────
  // Previously: private static final int TIMEOUT_SECONDS = 120;

  private int secondsRemaining = LayoutTheme.get().overlayTimeoutSeconds;
  private final Timer countdownTimer;
  private final JLabel timeoutLabel = new JLabel();
  private final JProgressBar timeoutBar =
      new JProgressBar(0, LayoutTheme.get().overlayTimeoutSeconds);

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
      secondsRemaining = LayoutTheme.get().overlayTimeoutSeconds;
      updateTimeout();
      if (owner instanceof JukeANatorFrame frame) {
        frame.showAddSongToQueueCard(song);
      }
    };


    AlbumViewCard albumView =
        new AlbumViewCard(album, imageLoader, threshold1, threshold2, threshold3, songClick);

    add(albumView, BorderLayout.CENTER);
    add(buildFooter(navigator), BorderLayout.SOUTH);

    countdownTimer = new Timer(1000, e -> {
      if (--secondsRemaining <= 0)
        navigator.popToRoot();
      else
        updateTimeout();
    });
    countdownTimer.start();
  }

  /** Must be called when the card is removed from view so the timer doesn't leak. */
  public void dismiss() {
    countdownTimer.stop();
  }

  private JPanel buildFooter(TabNavigator navigator) {

    JPanel footer = new JPanel(new BorderLayout(12, 0));
    footer.setOpaque(false);

    //
    // LEFT SIDE BUTTONS
    //
    JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
    buttons.setOpaque(false);

    JButton backButton = createBackButton("← BACK", navigator::popToRoot);
    buttons.add(backButton);

    //
    // TIMEOUT
    //
    JPanel timeoutSection = new JPanel(new BorderLayout(8, 0));
    timeoutSection.setOpaque(false);

    timeoutBar.setValue(LayoutTheme.get().overlayTimeoutSeconds);
    timeoutBar.setForeground(ColorTheme.get().accentBlue);
    timeoutBar.setOpaque(false);
    timeoutBar.setBorderPainted(false);
    timeoutBar.setStringPainted(false);

    timeoutLabel.setForeground(ColorTheme.get().textSecondary);

    updateTimeout();

    timeoutSection.add(timeoutBar, BorderLayout.CENTER);
    timeoutSection.add(timeoutLabel, BorderLayout.EAST);

    footer.add(buttons, BorderLayout.WEST);
    footer.add(timeoutSection, BorderLayout.CENTER);

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

  private void updateTimeout() {
    timeoutBar.setValue(secondsRemaining);
    timeoutLabel.setText("Closes in " + secondsRemaining + "s");
  }
}
