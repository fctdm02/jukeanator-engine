package com.djt.jukeanator_engine.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

public class JukeANatorFrame extends JFrame {

  private static final long serialVersionUID = -5819744874210041265L;

  private final DefaultListModel<String> genresListModel = new DefaultListModel<>();
  private final DefaultListModel<String> queueListModel = new DefaultListModel<>();

  private final JList<String> genresList = new JList<>(genresListModel);
  private final JList<String> queueList = new JList<>(queueListModel);

  private final JLabel albumArtLabel = new JLabel();
  private final JLabel songLabel = new JLabel("", SwingConstants.CENTER);
  private final JLabel artistLabel = new JLabel("", SwingConstants.CENTER);
  private final JLabel albumLabel = new JLabel("", SwingConstants.CENTER);

  private final JButton showQueueButton = new JButton("Show Queue");

  public JukeANatorFrame() {
    initialize();
  }

  private void initialize() {

    setTitle("JukeANator");
    setUndecorated(true);
    setBackground(Color.BLACK);
    getContentPane().setLayout(new BorderLayout());

    //
    // CENTER
    //
    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.setBackground(Color.BLACK);

    albumArtLabel.setHorizontalAlignment(SwingConstants.CENTER);
    centerPanel.add(albumArtLabel, BorderLayout.CENTER);

    JPanel songInfoPanel = new JPanel();
    songInfoPanel.setBackground(Color.BLACK);
    songInfoPanel.setLayout(new BoxLayout(songInfoPanel, BoxLayout.Y_AXIS));

    songLabel.setForeground(Color.CYAN);
    songLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));

    artistLabel.setForeground(Color.WHITE);
    artistLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));

    albumLabel.setForeground(Color.LIGHT_GRAY);
    albumLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 24));

    songInfoPanel.add(songLabel);
    songInfoPanel.add(artistLabel);
    songInfoPanel.add(albumLabel);

    centerPanel.add(songInfoPanel, BorderLayout.SOUTH);

    getContentPane().add(centerPanel, BorderLayout.CENTER);

    //
    // LEFT: GENRES
    //
    JPanel genresPanel = new JPanel(new BorderLayout());
    genresPanel.setBackground(Color.DARK_GRAY);
    genresPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

    JLabel genresLabel = new JLabel("GENRES");
    genresLabel.setForeground(Color.WHITE);
    genresLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));

    genresList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));

    genresPanel.add(genresLabel, BorderLayout.NORTH);
    genresPanel.add(new JScrollPane(genresList), BorderLayout.CENTER);

    getContentPane().add(genresPanel, BorderLayout.WEST);

    //
    // RIGHT: QUEUE
    //
    JPanel queuePanel = new JPanel(new BorderLayout());
    queuePanel.setBackground(Color.DARK_GRAY);

    JLabel queueLabel = new JLabel("QUEUE");
    queueLabel.setForeground(Color.WHITE);
    queueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));

    queueList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));

    queuePanel.add(queueLabel, BorderLayout.NORTH);
    queuePanel.add(new JScrollPane(queueList), BorderLayout.CENTER);

    showQueueButton.addActionListener(e -> toggleQueueVisibility(queuePanel));

    queuePanel.add(showQueueButton, BorderLayout.SOUTH);

    queuePanel.setVisible(true);

    getContentPane().add(queuePanel, BorderLayout.EAST);
  }

  private void toggleQueueVisibility(JPanel queuePanel) {
    queuePanel.setVisible(!queuePanel.isVisible());
    revalidate();
    repaint();
  }

  public void showFullscreen() {

    GraphicsDevice gd =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice();

    gd.setFullScreenWindow(this);
  }

  public void setGenres(List<String> genres) {

    SwingUtilities.invokeLater(() -> {
      genresListModel.clear();
      if (genres != null) {
        genres.forEach(genresListModel::addElement);
      }
    });
  }

  public void setQueue(List<SongQueueEntryDto> queue) {

    SwingUtilities.invokeLater(() -> {

      queueListModel.clear();

      if (queue != null) {

        queue.forEach(q ->
            queueListModel.addElement(q.getName())
        );
      }
    });
  }

  public void setNowPlaying(NowPlayingSongDto songDto) {

    SwingUtilities.invokeLater(() -> {

      if (songDto == null) {
        clearNowPlaying();
        return;
      }

      songLabel.setText(songDto.getSong());
      artistLabel.setText(songDto.getArtist());
      albumLabel.setText(songDto.getAlbum());

      loadAlbumArt(songDto.getCoverArtUrl());
    });
  }

  private void clearNowPlaying() {
    songLabel.setText("");
    artistLabel.setText("");
    albumLabel.setText("");
    albumArtLabel.setIcon(null);
  }

  private void loadAlbumArt(String coverArtPath) {

    try {
      if (coverArtPath == null || coverArtPath.isBlank()) {
        albumArtLabel.setIcon(null);
        return;
      }
      
      Path path = Paths.get(coverArtPath);
      URL imageUrl = path.toUri().toURL();      

      ImageIcon icon = new ImageIcon(imageUrl);

      Image scaled = icon.getImage()
          .getScaledInstance(500, 500, Image.SCALE_SMOOTH);

      albumArtLabel.setIcon(new ImageIcon(scaled));

    } catch (Exception e) {
      albumArtLabel.setIcon(null);
    }
  }
}