package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

public class AlbumDetailCard extends JPanel {

  private static final long serialVersionUID = 1L;

  private static final int TIMEOUT_SECONDS = 120;

  private static final Color BG_DARK = new Color(10, 10, 10);
  private static final Color BG_FOOTER      = new Color(18, 18, 26);
  private static final Color BTN_NORMAL     = new Color(60, 60, 75);
  private static final Color BTN_HOVER      = new Color(90, 90, 110);
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);
  
  private int secondsRemaining = TIMEOUT_SECONDS;
  private final Timer countdownTimer;
  private final JLabel timeoutLabel = new JLabel();
  private final JProgressBar timeoutBar = new JProgressBar(0, TIMEOUT_SECONDS);

  public AlbumDetailCard(Frame owner, AlbumDto album, ImageLoader imageLoader,
      SongQueueService songQueueService, int normalPlayCost, int priorityCost, int threshold1,
      int threshold2, int threshold3, boolean enableBigScrollBars, TabNavigator navigator) {

    setLayout(new BorderLayout());
    setBackground(BG_DARK);

    AlbumViewPanel.SongClickListener songClick = song -> {
      secondsRemaining = TIMEOUT_SECONDS;
      updateTimeout();
      AddSongToQueueDialog.show(owner, song, imageLoader, normalPlayCost, priorityCost,
          () -> songQueueService
              .addSongToQueue(new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 0)),
          () -> songQueueService
              .addSongToQueue(new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 1)));
    };
    
    int numSongs = album.getSongs().size();
    int albumNormalPlayCost = normalPlayCost * numSongs;
    int albumPriorityCost = priorityCost * numSongs; 

    AlbumViewPanel.AlbumClickListener albumClick = clicked -> {
      secondsRemaining = TIMEOUT_SECONDS;
      updateTimeout();
      AddAlbumToQueueDialog.show(owner, clicked, imageLoader, albumNormalPlayCost, albumPriorityCost,
          () -> songQueueService
              .addAlbumToQueue(new AddAlbumToQueueRequest(clicked.getAlbumId(), 0)),
          () -> songQueueService
              .addAlbumToQueue(new AddAlbumToQueueRequest(clicked.getAlbumId(), 1)));
    };

    AlbumViewPanel albumView = new AlbumViewPanel(album, imageLoader, threshold1, threshold2,
        threshold3, enableBigScrollBars, songClick, albumClick);

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
    footer.setBackground(BG_FOOTER);

    //
    // LEFT SIDE BUTTONS
    //
    JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
    buttons.setOpaque(false);

    JButton backButton = createFooterButton("← BACK", navigator::popToRoot);
    buttons.add(backButton);

    //
    // TIMEOUT
    //
    JPanel timeoutSection = new JPanel(new BorderLayout(8, 0));
    timeoutSection.setOpaque(false);

    timeoutBar.setValue(TIMEOUT_SECONDS);
    timeoutBar.setForeground(new Color(0, 210, 255));
    timeoutBar.setBackground(new Color(40, 40, 55));
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

  private JButton createFooterButton(String text, Runnable action) {

    JButton button = new JButton(text);
    button.setFont(button.getFont().deriveFont(Font.BOLD, 18f));
    button.setForeground(Color.WHITE);
    button.setBackground(BTN_NORMAL);
    button.setFocusPainted(false);
    button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    button.addActionListener(e -> action.run());
    button.addMouseListener(new java.awt.event.MouseAdapter() {

      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {

        button.setBackground(BTN_HOVER);
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {

        button.setBackground(BTN_NORMAL);
      }
    });

    return button;
  }
  
  private void updateTimeout() {
    timeoutBar.setValue(secondsRemaining);
    timeoutLabel.setText("Closes in " + secondsRemaining + "s");
  }
}
