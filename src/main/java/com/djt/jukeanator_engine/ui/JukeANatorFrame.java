package com.djt.jukeanator_engine.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;

public class JukeANatorFrame extends JFrame {

  private static final long serialVersionUID = -5819744874210041265L;

  private final DefaultListModel<String> genresListModel;
  private final JList<String> genresList;

  private JLabel nowPlayingValueLabel;

  public JukeANatorFrame() {

    genresListModel = new DefaultListModel<>();
    genresList = new JList<>(genresListModel);

    initialize();
  }

  private void initialize() {

    setTitle("JukeANator");
    setSize(800, 600);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    //
    // Top panel
    //
    JPanel topPanel = new JPanel(new BorderLayout());

    JLabel titleLabel = new JLabel("JukeANator", SwingConstants.CENTER);
    titleLabel.setFont(new Font("Arial", Font.BOLD, 24));

    //
    // Now Playing panel (upper right)
    //
    JPanel nowPlayingPanel = new JPanel(new BorderLayout());

    JLabel nowPlayingLabel = new JLabel("Now Playing:");
    nowPlayingLabel.setFont(new Font("Arial", Font.BOLD, 14));

    nowPlayingValueLabel = new JLabel("");
    nowPlayingValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

    nowPlayingPanel.add(nowPlayingLabel, BorderLayout.NORTH);
    nowPlayingPanel.add(nowPlayingValueLabel, BorderLayout.CENTER);

    topPanel.add(titleLabel, BorderLayout.CENTER);
    topPanel.add(nowPlayingPanel, BorderLayout.EAST);

    add(topPanel, BorderLayout.NORTH);

    //
    // Genres panel
    //
    JPanel genresPanel = new JPanel(new BorderLayout());

    JLabel genresLabel = new JLabel("Genres");
    genresLabel.setFont(new Font("Arial", Font.BOLD, 16));

    JScrollPane genresScrollPane = new JScrollPane(genresList);

    genresPanel.add(genresLabel, BorderLayout.NORTH);
    genresPanel.add(genresScrollPane, BorderLayout.CENTER);

    add(genresPanel, BorderLayout.CENTER);
  }

  /**
   * Sets the genres displayed in the list.
   *
   * @param genres list of genre names
   */
  public void setGenres(List<String> genres) {

    genresListModel.clear();

    if (genres != null) {
      genres.forEach(genresListModel::addElement);
    }
  }

  /**
   * Sets the currently playing song.
   *
   * @param songDto current song queue entry
   */
  public void setNowPlaying(NowPlayingSongDto songDto) {

    if (songDto == null) {
      nowPlayingValueLabel.setText("");
      return;
    }

    String songName = songDto.getSong();
    nowPlayingValueLabel.setText(songName);
  }
}