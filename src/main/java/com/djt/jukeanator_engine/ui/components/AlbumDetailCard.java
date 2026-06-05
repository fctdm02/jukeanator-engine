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

  private static final int TIMEOUT_SECONDS = 120;
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);

  private int secondsRemaining = TIMEOUT_SECONDS;
  private final Timer countdownTimer;
  private final JLabel timeoutLabel = new JLabel();
  private final JProgressBar timeoutBar = new JProgressBar(0, TIMEOUT_SECONDS);

  public AlbumDetailCard(Frame owner, AlbumDto album, ImageLoader imageLoader,
      SongQueueService songQueueService, int priorityCostMultiplier, int threshold1, int threshold2,
      int threshold3, TabNavigator navigator, CreditManager creditManager,
      char incrementCreditsKey) {

    setLayout(new BorderLayout());
    setOpaque(false);

    AlbumViewPanel.SongClickListener songClick = song -> {
      secondsRemaining = TIMEOUT_SECONDS;
      updateTimeout();
      AddSongToQueueDialog.show(owner, song, imageLoader, priorityCostMultiplier, songQueueService,
          creditManager, incrementCreditsKey);
    };

    AlbumViewPanel.AlbumClickListener albumClick = clicked -> {

      secondsRemaining = TIMEOUT_SECONDS;
      updateTimeout();

      AddAlbumToQueueDialog.show(owner, clicked, imageLoader, priorityCostMultiplier,
          songQueueService, creditManager, incrementCreditsKey);
    };

    AlbumViewPanel albumView = new AlbumViewPanel(album, imageLoader, threshold1, threshold2,
        threshold3, songClick, albumClick);

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

    timeoutBar.setValue(TIMEOUT_SECONDS);
    timeoutBar.setForeground(new Color(0, 210, 255));
    timeoutBar.setOpaque(false);
    timeoutBar.setBorderPainted(false);
    timeoutBar.setStringPainted(false);

    timeoutLabel.setForeground(TEXT_SECONDARY);

    updateTimeout();

    timeoutSection.add(timeoutBar, BorderLayout.CENTER);
    timeoutSection.add(timeoutLabel, BorderLayout.EAST);

    footer.add(buttons, BorderLayout.WEST);
    footer.add(timeoutSection, BorderLayout.CENTER);

    return footer;
  }

  private JButton createBackButton(String text, Runnable action) {

    // Idle and hover fill colours mirror the sort button's active gradient
    final Color GRAD_TOP = new Color(0, 160, 210);
    final Color GRAD_BOTTOM = new Color(0, 80, 130);
    final Color HOVER_TOP = new Color(0, 190, 240);
    final Color HOVER_BOTTOM = new Color(0, 100, 160);

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
        Color top = hovered ? HOVER_TOP : GRAD_TOP;
        Color bottom = hovered ? HOVER_BOTTOM : GRAD_BOTTOM;
        g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

        // Accent border
        g2.setColor(ACCENT_BLUE);
        g2.setStroke(new java.awt.BasicStroke(1.5f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        g2.dispose();
        super.paintComponent(g);
      }
    };

    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    button.setForeground(Color.WHITE);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(140, 52));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> action.run());

    return button;
  }

  private void updateTimeout() {
    timeoutBar.setValue(secondsRemaining);
    timeoutLabel.setText("Closes in " + secondsRemaining + "s");
  }
}
