package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

public class JukeANatorFrame extends JFrame {

  private static final long serialVersionUID = 1L;

  // ============================================================
  // COLORS
  // ============================================================
  private static final Color BG_DARK = new Color(10, 10, 10);
  private static final Color BG_PANEL = new Color(22, 22, 28);
  private static final Color BG_SEARCH = new Color(32, 32, 40);
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);

  // ============================================================
  // DATA MODELS
  // ============================================================
  private final DefaultListModel<String> genresListModel = new DefaultListModel<>();
  private final DefaultListModel<String> queueListModel = new DefaultListModel<>();

  // ============================================================
  // LISTS
  // ============================================================
  private final JList<String> queueList = new JList<>(queueListModel);

  // ============================================================
  // GENRES PAGINATION
  // ============================================================
  private static final int GENRES_PER_PAGE = 12;
  private final JPanel genresRootPanel = new JPanel(new CardLayout());
  private final JPanel genresGridPanel = new JPanel(new GridLayout(2, 6, 20, 20));
  private final JPanel genresPaginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
  private final CardLayout genresCardLayout = new CardLayout();
  private final JPanel genresContentPanel = new JPanel(genresCardLayout);
  private final JPanel genreDetailsPanel = new JPanel(new BorderLayout());
  private int currentGenresPage = 0;
  private final Map<String, ImageIcon> genreIconCache = new HashMap<>();

  // ============================================================
  // NOW PLAYING
  // ============================================================
  private final JLabel albumArtLabel = new JLabel();
  private final JLabel songLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel artistLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel albumLabel = new JLabel("", SwingConstants.LEFT);

  // ============================================================
  // CONSTRUCTOR
  // ============================================================
  public JukeANatorFrame() {
    initialize();
  }

  // ============================================================
  // INITIALIZE
  // ============================================================
  private void initialize() {

    setTitle("JukeANator");
    setUndecorated(true);
    setBackground(Color.BLACK);
    getContentPane().setBackground(BG_DARK);
    getContentPane().setLayout(new BorderLayout());

    //
    // TOP 10%
    //
    JPanel topPanel = buildTopPanel();
    topPanel.setPreferredSize(new Dimension(100, 110));
    getContentPane().add(topPanel, BorderLayout.NORTH);

    //
    // BOTTOM 90%
    //
    JTabbedPane contentPanelTabs = buildContentPanelTabs();
    getContentPane().add(contentPanelTabs, BorderLayout.CENTER);
  }

  // ============================================================
  // TABS
  // ============================================================
  private JTabbedPane buildContentPanelTabs() {

    JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
    tabs.setBackground(BG_PANEL);
    tabs.setForeground(TEXT_PRIMARY);
    tabs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    tabs.addTab("Home", buildPlaceholderPanel());
    tabs.addTab("Search", buildSearchPanel());
    tabs.addTab("Hot Here", buildPlaceholderPanel());
    tabs.addTab("Genres", buildGenresPanel());
    tabs.addTab("Queue", buildPlaceholderPanel());
    tabs.addTab("Admin", buildPlaceholderPanel());

    return tabs;
  }

  // ============================================================
  // SEARCH PANEL
  // ============================================================
  private JPanel buildSearchPanel() {

    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(BG_DARK);

    //
    // HERO PANEL
    //
    JPanel heroPanel = new JPanel(new GridBagLayout());
    heroPanel.setPreferredSize(new Dimension(100, 320));
    heroPanel.setBackground(new Color(25, 25, 35));
    heroPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_BLUE));

    JLabel heroLabel = new JLabel("Search for your favorite music.");
    heroLabel.setForeground(TEXT_PRIMARY);
    heroLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
    heroPanel.add(heroLabel);

    //
    // KEYBOARD
    //
    JPanel keyboardWrapper = new JPanel(new GridBagLayout());
    keyboardWrapper.setBackground(BG_SEARCH);
    keyboardWrapper.add(buildKeyboardPanel());

    root.add(heroPanel, BorderLayout.NORTH);
    root.add(keyboardWrapper, BorderLayout.CENTER);

    return root;
  }

  private JPanel buildKeyboardPanel() {

    JPanel panel = new JPanel();
    panel.setOpaque(false);
    panel.setBorder(new EmptyBorder(30, 50, 30, 50));
    panel.setLayout(new GridLayout(4, 1, 8, 8));
    panel.add(buildKeyboardRow("QWERTYUIOP"));
    panel.add(buildKeyboardRow("ASDFGHJKL"));
    panel.add(buildKeyboardRow("ZXCVBNM"));
    panel.add(buildBottomKeyboardRow());

    return panel;
  }

  private JPanel buildKeyboardRow(String chars) {

    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
    row.setOpaque(false);

    for (char c : chars.toCharArray()) {

      JButton button = createKeyboardButton(String.valueOf(c));
      row.add(button);
    }

    return row;
  }

  private JPanel buildBottomKeyboardRow() {

    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
    row.setOpaque(false);
    row.add(createWideButton("CLEAR", 180));
    row.add(createWideButton("SPACE", 320));
    row.add(createWideButton("⌫", 100));

    return row;
  }

  private JButton createKeyboardButton(String text) {

    JButton button = new JButton(text);
    button.setPreferredSize(new Dimension(70, 60));
    styleKeyboardButton(button);

    return button;
  }

  private JButton createWideButton(String text, int width) {

    JButton button = new JButton(text);
    button.setPreferredSize(new Dimension(width, 60));
    styleKeyboardButton(button);

    return button;
  }

  private void styleKeyboardButton(JButton button) {

    button.setFocusPainted(false);
    button.setBackground(new Color(70, 70, 80));
    button.setForeground(TEXT_PRIMARY);
    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    button.setBorder(BorderFactory.createLineBorder(ACCENT_BLUE, 1));
  }

  // ============================================================
  // GENRES PANEL
  // ============================================================
  private JPanel buildGenresPanel() {

    genresRootPanel.setBackground(BG_DARK);
    genresContentPanel.setBackground(BG_DARK);
    genresGridPanel.setBackground(BG_DARK);

    JPanel pageWrapper = new JPanel(new BorderLayout());
    pageWrapper.setBackground(BG_DARK);
    pageWrapper.setBorder(new EmptyBorder(30, 40, 20, 40));
    pageWrapper.add(genresGridPanel, BorderLayout.CENTER);

    genresPaginationPanel.removeAll();
    genresPaginationPanel.setOpaque(false);

    int pageCount = (int) Math.ceil(genresListModel.size() / (double) GENRES_PER_PAGE);
    for (int i = 0; i < pageCount; i++) {

      JButton dotButton = new JButton("●");
      dotButton.setForeground(Color.WHITE);
      dotButton.setBackground(BG_DARK);
      dotButton.setBorderPainted(false);
      dotButton.setFocusPainted(false);
      dotButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));

      final int pageIndex = i;
      dotButton.addActionListener(e -> {

        currentGenresPage = pageIndex;
        refreshGenresPage();
      });

      genresPaginationPanel.add(dotButton);
    }

    JButton nextButton = new JButton(">");
    nextButton.setPreferredSize(new Dimension(120, 60));
    nextButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
    nextButton.setForeground(Color.WHITE);
    nextButton.setBackground(Color.BLACK);
    nextButton.addActionListener(e -> {

      int totalPages = (int) Math.ceil(genresListModel.size() / (double) GENRES_PER_PAGE);
      currentGenresPage++;
      if (currentGenresPage >= totalPages) {

        currentGenresPage = 0;
      }
      refreshGenresPage();
    });

    JPanel bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.setOpaque(false);
    bottomPanel.add(genresPaginationPanel, BorderLayout.CENTER);
    bottomPanel.add(nextButton, BorderLayout.EAST);

    JPanel genresPagePanel = new JPanel(new BorderLayout());
    genresPagePanel.setBackground(BG_DARK);
    genresPagePanel.add(pageWrapper, BorderLayout.CENTER);
    genresPagePanel.add(bottomPanel, BorderLayout.SOUTH);

    genresContentPanel.removeAll();
    genreDetailsPanel.setBackground(BG_DARK);
    genresContentPanel.add(genresPagePanel, "GRID");
    genresContentPanel.add(genreDetailsPanel, "DETAILS");
    genresCardLayout.show(genresContentPanel, "GRID");
    genresRootPanel.removeAll();
    genresRootPanel.add(genresContentPanel, BorderLayout.CENTER);

    refreshGenresPage();

    return genresRootPanel;
  }

  // ============================================================
  // REFRESH GENRES PAGE
  // ============================================================
  private void refreshGenresPage() {

    genresGridPanel.removeAll();
    int start = currentGenresPage * GENRES_PER_PAGE;
    int end = Math.min(start + GENRES_PER_PAGE, genresListModel.size());
    for (int i = start; i < end; i++) {

      String genre = genresListModel.get(i);
      genresGridPanel.add(buildGenreTile(genre));
    }
    genresGridPanel.revalidate();
    genresGridPanel.repaint();
  }

  // ============================================================
  // GENRE TILE
  // ============================================================
  private JPanel buildGenreTile(String genre) {

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(Color.BLACK);
    panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    JLabel imageLabel = new JLabel();
    imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

    try {

      ImageIcon cached = genreIconCache.get(genre);
      if (cached != null) {

        imageLabel.setIcon(cached);

      } else {

        String resourceName = genre + ".png";
        URL resource = getClass().getResource(resourceName);
        if (resource != null) {

          ImageIcon icon = new ImageIcon(resource);
          Image scaled = icon.getImage().getScaledInstance(240, 240, Image.SCALE_SMOOTH);
          ImageIcon scaledIcon = new ImageIcon(scaled);

          genreIconCache.put(genre, scaledIcon);
          imageLabel.setIcon(scaledIcon);

        } else {
          imageLabel.setText(genre);
        }
      }
    } catch (Exception e) {
      imageLabel.setText(genre);
    }

    JLabel textLabel = new JLabel(genre.toUpperCase(), SwingConstants.CENTER);

    textLabel.setForeground(Color.WHITE);
    textLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
    textLabel.setBorder(new EmptyBorder(10, 0, 10, 0));

    panel.add(imageLabel, BorderLayout.CENTER);
    panel.add(textLabel, BorderLayout.SOUTH);

    panel.addMouseListener(new java.awt.event.MouseAdapter() {

      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {

        showGenreDetails(genre);
      }
    });

    return panel;
  }

  // ============================================================
  // GENRE DETAILS
  // ============================================================
  private void showGenreDetails(String genre) {

    genreDetailsPanel.removeAll();

    JPanel detailsPanel = new JPanel(new GridBagLayout());
    detailsPanel.setBackground(BG_DARK);

    JLabel label = new JLabel("Genre Details: " + genre);
    label.setForeground(Color.WHITE);
    label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));

    detailsPanel.add(label);

    JButton backButton = new JButton("BACK");
    backButton.setPreferredSize(new Dimension(180, 60));
    backButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
    backButton.setForeground(Color.WHITE);
    backButton.setBackground(Color.BLACK);
    backButton.addActionListener(e -> {

      genresCardLayout.show(genresContentPanel, "GRID");
    });

    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    topPanel.setOpaque(false);
    topPanel.add(backButton);

    genreDetailsPanel.add(topPanel, BorderLayout.NORTH);
    genreDetailsPanel.add(detailsPanel, BorderLayout.CENTER);
    genreDetailsPanel.revalidate();
    genreDetailsPanel.repaint();

    genresCardLayout.show(genresContentPanel, "DETAILS");
  }

  // ============================================================
  // TOP PANEL
  // ============================================================
  private JPanel buildTopPanel() {

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(BG_PANEL);
    panel.setBorder(new EmptyBorder(10, 20, 10, 20));

    //
    // LEFT : CREDITS
    //
    JPanel creditsPanel = new JPanel();
    creditsPanel.setBackground(Color.BLACK);
    creditsPanel.setOpaque(true);
    creditsPanel.setBorder(null);
    creditsPanel.setPreferredSize(new Dimension(240, 100));
    creditsPanel.setLayout(new BoxLayout(creditsPanel, BoxLayout.Y_AXIS));
    
    JLabel creditsTitle = new JLabel("CREDITS: 12");
    creditsTitle.setForeground(Color.YELLOW);
    creditsTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));

    JLabel creditDescription = new JLabel("1$=3cr | 5$=18cr | 10$=40cr");
    creditDescription.setForeground(Color.WHITE);
    creditDescription.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));

    creditsPanel.add(creditsTitle);
    creditsPanel.add(Box.createVerticalStrut(5));
    creditsPanel.add(creditDescription);

    //
    // CENTER : BANNER
    //
    JPanel bannerPanel = new JPanel(new GridBagLayout());
    bannerPanel.setBackground(Color.BLACK);

    JLabel bannerLabel = new JLabel("");
    bannerLabel.setForeground(TEXT_SECONDARY);
    bannerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    bannerPanel.add(bannerLabel);

    //
    // RIGHT : NOW PLAYING
    //
    JPanel nowPlayingPanel = buildNowPlayingPanel();
    panel.add(creditsPanel, BorderLayout.WEST);
    panel.add(bannerPanel, BorderLayout.CENTER);
    panel.add(nowPlayingPanel, BorderLayout.EAST);

    return panel;
  }

  // ============================================================
  // NOW PLAYING PANEL
  // ============================================================
  private JPanel buildNowPlayingPanel() {

    JPanel panel = new JPanel(new BorderLayout(10, 0));
    panel.setBackground(Color.BLACK);
    panel.setOpaque(true);
    panel.setBorder(null);
    
    //
    // TEXT PANEL
    //
    JPanel textPanel = new JPanel();
    textPanel.setOpaque(false);
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

    JLabel nowPlayingTitle = new JLabel("NOW PLAYING:");
    nowPlayingTitle.setForeground(Color.YELLOW);
    nowPlayingTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

    songLabel.setForeground(Color.CYAN);
    songLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    artistLabel.setForeground(TEXT_PRIMARY);
    artistLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
    albumLabel.setForeground(TEXT_SECONDARY);
    albumLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

    textPanel.add(nowPlayingTitle);
    textPanel.add(Box.createVerticalStrut(4));
    textPanel.add(songLabel);
    textPanel.add(artistLabel);
    textPanel.add(albumLabel);

    //
    // COVER ART
    //
    albumArtLabel.setPreferredSize(new Dimension(96, 96));
    albumArtLabel.setHorizontalAlignment(SwingConstants.CENTER);
    albumArtLabel.setBorder(null);

    panel.add(textPanel, BorderLayout.CENTER);
    panel.add(albumArtLabel, BorderLayout.EAST);

    return panel;
  }

  // ============================================================
  // PLACEHOLDER
  // ============================================================
  private JPanel buildPlaceholderPanel() {

    JPanel panel = new JPanel(new CardLayout());
    panel.setBackground(BG_DARK);

    JLabel label = new JLabel("NOT IMPLEMENTED");
    label.setForeground(TEXT_SECONDARY);
    label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
    label.setHorizontalAlignment(SwingConstants.CENTER);

    panel.add(label);

    return panel;
  }

  // ============================================================
  // FULLSCREEN
  // ============================================================
  public void showFullscreen() {

    GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

    gd.setFullScreenWindow(this);
  }

  // ============================================================
  // GENRE LIST
  // ============================================================
  public void setGenres(List<String> genres) {

    SwingUtilities.invokeLater(() -> {

      genresListModel.clear();

      if (genres != null) {

        genres.forEach(genresListModel::addElement);
      }

      refreshGenresPage();
    });
  }

  // ============================================================
  // SONG QUEUE LIST
  // ============================================================
  public void setQueue(List<SongQueueEntryDto> queue) {

    SwingUtilities.invokeLater(() -> {

      queueListModel.clear();
      if (queue != null) {

        queue.forEach(q -> queueListModel.addElement(q.getName()));
      }
    });
  }

  // ============================================================
  // NOW PLAYING
  // ============================================================
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

  // ============================================================

  private void clearNowPlaying() {

    songLabel.setText("");
    artistLabel.setText("");
    albumLabel.setText("");
    albumArtLabel.setIcon(null);
  }

  // ============================================================

  private void loadAlbumArt(String coverArtPath) {

    try {

      if (coverArtPath == null || coverArtPath.isBlank()) {

        albumArtLabel.setIcon(null);

        return;
      }

      Path path = Paths.get(coverArtPath);
      URL imageUrl = path.toUri().toURL();
      ImageIcon icon = new ImageIcon(imageUrl);
      Image image = icon.getImage();
      Image scaled = image.getScaledInstance(96, 96, Image.SCALE_SMOOTH);
      albumArtLabel.setIcon(new ImageIcon(scaled));

    } catch (Exception e) {

      albumArtLabel.setIcon(null);
    }
  }
}
