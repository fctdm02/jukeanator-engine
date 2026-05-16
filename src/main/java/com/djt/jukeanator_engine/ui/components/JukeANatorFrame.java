package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;

public class JukeANatorFrame extends JFrame {

  private static final long serialVersionUID = 1L;
  private final JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties;

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
  // GENRE TAB
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
  private final DefaultListModel<String> genresListModel = new DefaultListModel<>();

  // ============================================================
  // QUEUE TAB
  // ============================================================
  private final CardLayout queueCardLayout = new CardLayout();
  private final JPanel queueRootPanel = new JPanel(queueCardLayout);
  private final JPanel queueDetailsPanel = new JPanel(new BorderLayout());
  private final JLabel queueDetailsCoverArt = new JLabel();
  private final JLabel queueDetailsSong = new JLabel();
  private final JLabel queueDetailsArtist = new JLabel();
  private final JLabel queueDetailsAlbum = new JLabel();
  private final DefaultListModel<SongQueueEntryDto> queueListModel = new DefaultListModel<>();
  private final JList<SongQueueEntryDto> queueList = new JList<>(queueListModel);  

  // ============================================================
  // NOW PLAYING
  // ============================================================
  private SongQueueEntryDto currentlyPlaying;
  private final JLabel albumArtLabel = new JLabel();
  private final JLabel songLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel artistLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel albumLabel = new JLabel("", SwingConstants.LEFT);
  
  // ============================================================
  // SONG CREDITS
  // ============================================================
  private final int creditsPer;
  private final int fiveBonusCredits;
  private final int tenBonusCredits; 

  // ============================================================
  // CONSTRUCTOR
  // ============================================================
  public JukeANatorFrame(JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties) {

    this.jukeANatorUserInterfaceProperties = jukeANatorUserInterfaceProperties;
    
    this.creditsPer = this.jukeANatorUserInterfaceProperties.getCreditsPer();
    this.fiveBonusCredits = this.jukeANatorUserInterfaceProperties.getFiveBonusCredits();
    this.tenBonusCredits = this.jukeANatorUserInterfaceProperties.getTenBonusCredits();

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

    //
    // FORCE SINGLE ROW
    //
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

    //
    // CUSTOM UI
    //
    tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {

      @Override
      protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {

        int count = Math.max(tabPane.getTabCount(), 1);

        //
        // USE AVAILABLE WIDTH
        //
        int availableWidth = tabPane.getWidth();

        //
        // ACCOUNT FOR INSETS
        //
        availableWidth -= 4;

        //
        // EVEN DISTRIBUTION
        //
        return availableWidth / count;
      }

      @Override
      protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {

        return 96;
      }

      @Override
      protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y,
          int w, int h, boolean isSelected) {

        if (isSelected) {

          //
          // ACTIVE TAB
          //
          g.setColor(new Color(70, 70, 70));
          g.fillRect(x, y, w, h);

          //
          // TOP HIGHLIGHT
          //
          g.setColor(Color.WHITE);
          g.fillRect(x, y, w, 3);

        } else {

          //
          // INACTIVE TAB
          //
          g.setColor(Color.BLACK);
          g.fillRect(x, y, w, h);
        }

        //
        // TAB SEPARATOR
        //
        g.setColor(new Color(180, 180, 180));
        g.drawLine(x, y, x, y + h);
      }

      @Override
      protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w,
          int h, boolean isSelected) {

        //
        // OUTER BORDER
        //
        g.setColor(new Color(180, 180, 180));
        g.drawRect(x, y, w, h);
      }

      @Override
      protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
          int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {

        //
        // REMOVE SWING FOCUS BORDER
        //
      }

      @Override
      protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {

        //
        // REMOVE CONTENT BORDER
        //
      }
    });

    tabs.setBackground(Color.BLACK);
    tabs.setForeground(Color.WHITE);
    tabs.setBorder(null);

    //
    // REMOVE GAP BETWEEN TABS
    //
    tabs.setOpaque(true);

    //
    // CONTENT PANELS
    //
    tabs.addTab("HOME", buildPlaceholderPanel());
    tabs.addTab("SEARCH", buildSearchPanel());
    tabs.addTab("HOT HERE", buildPlaceholderPanel());
    tabs.addTab("GENRES", buildGenresPanel());
    tabs.addTab("QUEUE", buildQueuePanel());
    tabs.addTab("ADMIN", buildPlaceholderPanel());

    //
    // CUSTOM TAB COMPONENTS
    //
    tabs.setTabComponentAt(0, new JukeboxTabComponent("HOME", "⌂", new Color(255, 120, 120)));
    tabs.setTabComponentAt(1, new JukeboxTabComponent("SEARCH", "⌕", new Color(0, 220, 255)));
    tabs.setTabComponentAt(2, new JukeboxTabComponent("HOT HERE", "🔥", new Color(255, 80, 120)));
    tabs.setTabComponentAt(3, new JukeboxTabComponent("GENRES", "▣", Color.WHITE));
    tabs.setTabComponentAt(4, new JukeboxTabComponent("QUEUE", "♫", new Color(0, 255, 180)));
    tabs.setTabComponentAt(5, new JukeboxTabComponent("ADMIN", "⚙", new Color(255, 220, 0)));

    return tabs;
  }

  private class JukeboxTabComponent extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Color accentColor;

    public JukeboxTabComponent(String title, String iconText, Color accentColor) {

      this.accentColor = accentColor;
      setOpaque(false);
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setBorder(new EmptyBorder(8, 20, 8, 20));

      //
      // ICON
      //
      JLabel iconLabel = new JLabel(iconText);
      iconLabel.setAlignmentX(CENTER_ALIGNMENT);
      iconLabel.setForeground(accentColor);
      iconLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));

      //
      // TEXT
      //
      JLabel textLabel = new JLabel(title);
      textLabel.setAlignmentX(CENTER_ALIGNMENT);
      textLabel.setForeground(accentColor);
      textLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));

      add(iconLabel);
      add(Box.createVerticalStrut(4));
      add(textLabel);
    }

    @Override
    protected void paintComponent(Graphics g) {

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      JTabbedPane pane = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, this);
      if (pane != null) {

        int index = pane.indexOfTabComponent(this);
        boolean selected = pane.getSelectedIndex() == index;
        if (selected) {

          //
          // GLOW EFFECT
          //
          g2.setColor(
              new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 60));

          g2.fillRoundRect(6, 6, getWidth() - 12, getHeight() - 12, 18, 18);

          //
          // BORDER
          //
          g2.setColor(new Color(220, 220, 220));

          g2.drawRoundRect(6, 6, getWidth() - 13, getHeight() - 13, 18, 18);
        }
      }

      g2.dispose();

      super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {

      return new Dimension(200, 92);
    }
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
  // QUEUE PANEL
  // ============================================================
  private JPanel buildQueuePanel() {

    //
    // LIST PANEL
    //
    JPanel queueListPanel = new JPanel(new BorderLayout());
    queueListPanel.setBackground(BG_DARK);
    queueListPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

    queueList.setBackground(Color.BLACK);
    queueList.setForeground(Color.WHITE);
    queueList.setSelectionBackground(ACCENT_BLUE);
    queueList.setSelectionForeground(Color.BLACK);
    queueList.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    queueList.setFixedCellHeight(60);
    queueList.setCellRenderer(new QueueListCellRenderer());
    queueList.addListSelectionListener(e -> {

      if (!e.getValueIsAdjusting()) {

        SongQueueEntryDto song = queueList.getSelectedValue();
        if (song != null) {
          showQueueSongDetails(song);
        }
      }
    });
    queueListPanel.add(queueList, BorderLayout.CENTER);

    //
    // DETAILS PANEL
    //
    queueDetailsPanel.setBackground(BG_DARK);

    //
    // ROOT
    //
    queueRootPanel.removeAll();
    queueRootPanel.add(queueListPanel, "LIST");
    queueRootPanel.add(queueDetailsPanel, "DETAILS");

    queueCardLayout.show(queueRootPanel, "LIST");

    return queueRootPanel;
  }

  // ============================================================
  // QUEUE SONG DETAILS
  // ============================================================
  private void showQueueSongDetails(SongQueueEntryDto song) {

    queueDetailsPanel.removeAll();

    //
    // BACK BUTTON
    //
    JButton backButton = new JButton("BACK");
    backButton.setPreferredSize(new Dimension(180, 60));
    backButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
    backButton.setForeground(Color.WHITE);
    backButton.setBackground(Color.BLACK);

    backButton.addActionListener(e -> {

      queueCardLayout.show(queueRootPanel, "LIST");
    });

    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    topPanel.setOpaque(false);
    topPanel.add(backButton);

    //
    // DETAILS CONTENT
    //
    JPanel contentPanel = new JPanel();
    contentPanel.setBackground(BG_DARK);
    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
    contentPanel.setBorder(new EmptyBorder(40, 40, 40, 40));

    //
    // COVER ART
    //
    queueDetailsCoverArt.setAlignmentX(CENTER_ALIGNMENT);

    try {

      if (song.getCoverArtPath() != null) {

        Path path = Paths.get(song.getCoverArtPath());
        URL imageUrl = path.toUri().toURL();

        ImageIcon icon = new ImageIcon(imageUrl);

        Image scaled = icon.getImage().getScaledInstance(320, 320, Image.SCALE_SMOOTH);

        queueDetailsCoverArt.setIcon(new ImageIcon(scaled));

      } else {

        queueDetailsCoverArt.setIcon(null);
      }

    } catch (Exception e) {

      queueDetailsCoverArt.setIcon(null);
    }

    //
    // SONG INFO
    //
    queueDetailsSong.setText(song.getSongName());
    queueDetailsSong.setForeground(Color.CYAN);
    queueDetailsSong.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
    queueDetailsSong.setAlignmentX(CENTER_ALIGNMENT);

    queueDetailsArtist.setText(song.getArtistName());
    queueDetailsArtist.setForeground(Color.WHITE);
    queueDetailsArtist.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
    queueDetailsArtist.setAlignmentX(CENTER_ALIGNMENT);

    queueDetailsAlbum.setText(song.getAlbumName());
    queueDetailsAlbum.setForeground(TEXT_SECONDARY);
    queueDetailsAlbum.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
    queueDetailsAlbum.setAlignmentX(CENTER_ALIGNMENT);

    contentPanel.add(queueDetailsCoverArt);
    contentPanel.add(Box.createVerticalStrut(30));
    contentPanel.add(queueDetailsSong);
    contentPanel.add(Box.createVerticalStrut(15));
    contentPanel.add(queueDetailsArtist);
    contentPanel.add(Box.createVerticalStrut(10));
    contentPanel.add(queueDetailsAlbum);

    //
    // ASSEMBLE
    //
    queueDetailsPanel.add(topPanel, BorderLayout.NORTH);
    queueDetailsPanel.add(contentPanel, BorderLayout.CENTER);

    queueDetailsPanel.revalidate();
    queueDetailsPanel.repaint();

    queueCardLayout.show(queueRootPanel, "DETAILS");
  }

  private class QueueListCellRenderer extends JPanel
      implements javax.swing.ListCellRenderer<SongQueueEntryDto> {

    private static final long serialVersionUID = 1L;
    
    private final JLabel cover = new JLabel();
    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final JLabel priorityBadge = new JLabel();

    public QueueListCellRenderer() {

      setLayout(new BorderLayout(12, 0));
      setBorder(new EmptyBorder(8, 10, 8, 10));

      // cover art
      cover.setPreferredSize(new Dimension(56, 56));
      cover.setHorizontalAlignment(SwingConstants.CENTER);

      // text
      JPanel textPanel = new JPanel();
      textPanel.setOpaque(false);
      textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

      title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
      subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

      title.setForeground(Color.WHITE);
      subtitle.setForeground(TEXT_SECONDARY);

      textPanel.add(title);
      textPanel.add(subtitle);

      // priority badge
      priorityBadge.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
      priorityBadge.setHorizontalAlignment(SwingConstants.CENTER);
      priorityBadge.setPreferredSize(new Dimension(40, 40));
      priorityBadge.setOpaque(true);

      add(cover, BorderLayout.WEST);
      add(textPanel, BorderLayout.CENTER);
      add(priorityBadge, BorderLayout.EAST);
    }

    @Override
    public java.awt.Component getListCellRendererComponent(JList<? extends SongQueueEntryDto> list,
        SongQueueEntryDto value, int index, boolean isSelected, boolean cellHasFocus) {

      // -------------------------
      // TEXT
      // -------------------------
      title.setText(value.getSongName());
      subtitle.setText(value.getArtistName() + " • " + value.getAlbumName());

      // -------------------------
      // PRIORITY INDICATOR
      // -------------------------
      int p = value.getPriority() == null ? 0 : value.getPriority();

      if (p >= 8) {
        priorityBadge.setText("🔥");
        priorityBadge.setBackground(new Color(220, 60, 60));
        priorityBadge.setForeground(Color.WHITE);
      } else if (p >= 4) {
        priorityBadge.setText("⬆");
        priorityBadge.setBackground(new Color(255, 160, 0));
        priorityBadge.setForeground(Color.BLACK);
      } else {
        priorityBadge.setText("•");
        priorityBadge.setBackground(new Color(80, 80, 80));
        priorityBadge.setForeground(Color.WHITE);
      }

      // -------------------------
      // COVER ART
      // -------------------------
      try {
        if (value.getCoverArtPath() != null) {
          Path path = Paths.get(value.getCoverArtPath());
          ImageIcon icon = new ImageIcon(path.toUri().toURL());

          Image scaled = icon.getImage().getScaledInstance(56, 56, Image.SCALE_SMOOTH);
          cover.setIcon(new ImageIcon(scaled));
        } else {
          cover.setIcon(null);
        }
      } catch (Exception e) {
        cover.setIcon(null);
      }

      // -------------------------
      // PLAYING NOW + SELECTION HIGHLIGHT
      // -------------------------
      boolean isPlaying =
          currentlyPlaying != null && currentlyPlaying.getSongName().equals(value.getSongName())
              && currentlyPlaying.getArtistName().equals(value.getArtistName());

      if (isPlaying) {
        setBackground(new Color(0, 210, 255)); // ACCENT_BLUE
        title.setForeground(Color.BLACK);
        subtitle.setForeground(Color.BLACK);
      } else if (isSelected) {
        setBackground(new Color(40, 40, 50));
        title.setForeground(Color.WHITE);
        subtitle.setForeground(TEXT_SECONDARY);
      } else {
        setBackground(Color.BLACK);
        title.setForeground(Color.WHITE);
        subtitle.setForeground(TEXT_SECONDARY);
      }

      setOpaque(true);
      return this;
    }
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

    JLabel creditDescription = new JLabel(buildCreditsDescription());
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

  private String buildCreditsDescription() {

    int oneDollarCredits = creditsPer;
    int fiveDollarCredits = (5 * creditsPer) + fiveBonusCredits;
    int tenDollarCredits = (10 * creditsPer) + tenBonusCredits;

    return String.format(
        "1$=%dcr | 5$=%dcr | 10$=%dcr",
        oneDollarCredits,
        fiveDollarCredits,
        tenDollarCredits);
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
        queue.forEach(queueListModel::addElement);
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

      songLabel.setText(songDto.getSongName());
      artistLabel.setText(songDto.getArtistName());
      albumLabel.setText(songDto.getAlbumName());
      loadAlbumArt(songDto.getCoverArtPath());

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
      Image image = icon.getImage();
      Image scaled = image.getScaledInstance(96, 96, Image.SCALE_SMOOTH);
      albumArtLabel.setIcon(new ImageIcon(scaled));

    } catch (Exception e) {

      albumArtLabel.setIcon(null);
    }
  }
  
  public void setCurrentlyPlaying(SongQueueEntryDto song) {
    this.currentlyPlaying = song;

    SwingUtilities.invokeLater(() -> queueList.repaint());
  }  
}
