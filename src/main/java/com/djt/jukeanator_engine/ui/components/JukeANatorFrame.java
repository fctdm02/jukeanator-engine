package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.List;
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
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;

public class JukeANatorFrame extends JFrame {

  private static final long serialVersionUID = 1L;

  private final JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties;
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final SongPlayerService songPlayerService;

  private final ImageLoader imageLoader = new ImageLoader();
  private static final int POPULARITY_THRESHOLD_1 = 10;
  private static final int POPULARITY_THRESHOLD_2 = 25;
  private static final int POPULARITY_THRESHOLD_3 = 50;

  // COLORS
  // private static final Color BG_DARK = new Color(10, 10, 10);
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);

  // TOP PANEL
  private JPanel nowPlayingPanel;

  // HOME TAB
  private static final int HOME_GRID_COLS = 4;
  private static final int HOME_GRID_ROWS = 3;
  private static final int HOME_TILE_ART_W = 190;
  private static final int HOME_TILE_ART_H = 190;
  private HomePanel homePanel;

  // SEARCH TAB
  private final boolean enableTypeAheadSearch;
  private SearchPanel searchPanel;

  // HOT HERE TAB
  private HotHerePanel hotHerePanel;

  // GENRE TAB
  private GenrePanel genrePanel;

  // QUEUE TAB
  private final CardLayout queueCardLayout = new CardLayout();
  private final JPanel queueRootPanel = new JPanel(queueCardLayout);
  private final JPanel queueDetailsPanel = new JPanel(new BorderLayout());
  private final JLabel queueDetailsCoverArt = new JLabel();
  private final JLabel queueDetailsSong = new JLabel();
  private final JLabel queueDetailsArtist = new JLabel();
  private final JLabel queueDetailsAlbum = new JLabel();
  private final DefaultListModel<SongQueueEntryDto> queueListModel = new DefaultListModel<>();
  private final JList<SongQueueEntryDto> queueList = new JList<>(queueListModel);


  // NOW PLAYING
  private SongQueueEntryDto nowPlayingSong;
  private final JLabel albumArtLabel = new JLabel();
  private final JLabel songLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel artistLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel albumLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel playStatus = new JLabel();
  private boolean musicPaused = false;


  // SONG CREDITS
  private final int creditsPer;
  private final int fiveBonusCredits;
  private final int tenBonusCredits;


  // CONSTRUCTOR
  public JukeANatorFrame(JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties,
      SongLibraryService songLibraryService, SongQueueService songQueueService,
      SongPlayerService songPlayerService) {

    this.jukeANatorUserInterfaceProperties = jukeANatorUserInterfaceProperties;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;

    this.creditsPer = this.jukeANatorUserInterfaceProperties.getCreditsPer();
    this.fiveBonusCredits = this.jukeANatorUserInterfaceProperties.getFiveBonusCredits();
    this.tenBonusCredits = this.jukeANatorUserInterfaceProperties.getTenBonusCredits();

    this.enableTypeAheadSearch = this.jukeANatorUserInterfaceProperties.isEnableTypeAheadSearch();

    initialize();
  }

  // INITIALIZE
  private void initialize() {

    setTitle("JukeANator");
    setUndecorated(true);
    setBackground(Color.BLACK);

    JPanel contentPane = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Base dark fill first
        g2.setColor(new Color(10, 10, 10));
        g2.fillRect(0, 0, w, h);

        // Diagonal rainbow overlay — top-left to bottom-right,
        // matching the AMI screenshot's warm-left / cool-right spread
        float[] fractions = {0.0f, 0.20f, 0.42f, 0.62f, 0.82f, 1.0f};
        Color[] colors = {new Color(140, 50, 50, 90), // deep red, top-left
            new Color(140, 90, 30, 80), // amber
            new Color(80, 110, 40, 70), // olive
            new Color(30, 100, 110, 70), // teal
            new Color(40, 60, 140, 80), // blue
            new Color(100, 30, 140, 90), // violet, bottom-right
        };
        g2.setPaint(new LinearGradientPaint(new Point2D.Float(0, 0), new Point2D.Float(w, h), // diagonal:
                                                                                              // top-left
                                                                                              // →
                                                                                              // bottom-right
            fractions, colors));
        g2.fillRect(0, 0, w, h);

        g2.dispose();
      }
    };
    contentPane.setOpaque(true);
    setContentPane(contentPane);

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
  // TABS PANEL
  // ============================================================
  private JTabbedPane buildContentPanelTabs() {

    // Make the JTabbedPane content area and tab backgrounds non-opaque globally or locally
    UIManager.put("TabbedPane.tabsOpaque", Boolean.FALSE);
    UIManager.put("TabbedPane.opaque", Boolean.FALSE);
    UIManager.put("TabbedPane.contentOpaque", Boolean.FALSE);

    // Some Swing Look and Feels require setting transparent colors explicitly
    UIManager.put("TabbedPane.unselectedBackground", new Color(0, 0, 0, 0));
    UIManager.put("TabbedPane.background", new Color(0, 0, 0, 0));

    JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        // Do not fill background — let JFrame gradient show through
      }
    };
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

    tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {

      private static final int TAB_WIDTH = 200;
      private static final int SEPARATOR_HEIGHT = 2;

      //
      // FIXED TAB WIDTH
      //
      @Override
      protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
        return TAB_WIDTH;
      }

      @Override
      protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
        return 96;
      }

      //
      // CENTER TABS BY PUSHING THEM IN FROM THE LEFT
      //
      @Override
      protected java.awt.Insets getTabAreaInsets(int tabPlacement) {
        java.awt.Insets base = super.getTabAreaInsets(tabPlacement);
        int totalTabWidth = TAB_WIDTH * tabPane.getTabCount();
        int leftInset = Math.max(0, (tabPane.getWidth() - totalTabWidth) / 2);
        return new java.awt.Insets(base.top, leftInset, base.bottom, base.right);
      }

      @Override
      protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y,
          int w, int h, boolean isSelected) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(java.awt.AlphaComposite.SrcOver);
        if (isSelected) {
          g2.setColor(new Color(255, 255, 255, 35));
        } else {
          g2.setColor(new Color(0, 0, 0, 120));
        }
        g2.fillRect(x, y, w, h);
        g2.dispose();
      }

      @Override
      protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w,
          int h, boolean isSelected) {
        // DO NOTHING
      }

      @Override
      protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
          int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
        // DO NOTHING
      }

      @Override
      protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        // Explicitly clear the content border area to fully transparent
        // so the JFrame gradient shows through
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(java.awt.AlphaComposite.Clear);
        int tabAreaHeight = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        int contentH = tabPane.getHeight() - tabAreaHeight;
        g2.fillRect(0, 0, tabPane.getWidth(), contentH);
        g2.dispose();
      }

      //
      // PAINT THE FULL-WIDTH SEPARATOR OVER THE ENTIRE PANE WIDTH
      // This runs after all other painting so it always appears on top
      //
      @Override
      public void paint(Graphics g, javax.swing.JComponent c) {
        super.paint(g, c);
        int tabAreaHeight = calculateTabAreaHeight(JTabbedPane.BOTTOM, runCount, maxTabHeight);
        int separatorY = tabPane.getHeight() - tabAreaHeight - SEPARATOR_HEIGHT;
        g.setColor(new Color(180, 180, 180));
        g.fillRect(0, separatorY, tabPane.getWidth(), SEPARATOR_HEIGHT);
      }
    });

    tabs.setForeground(Color.WHITE);
    tabs.setBorder(null);
    tabs.setOpaque(false);
    tabs.setBackground(new Color(0, 0, 0, 0));

    homePanel = buildHomePanel();
    tabs.addTab("HOME", homePanel);

    searchPanel = buildSearchPanel();
    tabs.addTab("SEARCH", searchPanel);

    hotHerePanel = buildHotHerePanel();
    tabs.addTab("HOT HERE", hotHerePanel);

    genrePanel = buildGenresPanel();
    tabs.addTab("GENRES", genrePanel);

    tabs.addTab("QUEUE", buildQueuePanel());
    tabs.addTab("ADMIN", buildPlaceholderPanel());

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
        }
      }

      g2.dispose();

      super.paintComponent(g);
    }
  }



  // ============================================================
  // HOME PANEL
  // ============================================================
  private HomePanel buildHomePanel() {

    return new HomePanel(songLibraryService, songQueueService, imageLoader, creditsPer,
        creditsPer * 2, // priority cost placeholder
        POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2, POPULARITY_THRESHOLD_3, HOME_GRID_COLS,
        HOME_GRID_ROWS, HOME_TILE_ART_W, HOME_TILE_ART_H);
  }

  // ============================================================
  // SEARCH PANEL
  // ============================================================
  private SearchPanel buildSearchPanel() {

    return new SearchPanel(songLibraryService, songQueueService, imageLoader, creditsPer,
        creditsPer * 2, // priority cost placeholder
        POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2, POPULARITY_THRESHOLD_3,
        enableTypeAheadSearch, HOME_GRID_COLS, HOME_GRID_ROWS, HOME_TILE_ART_W, HOME_TILE_ART_H);
  }

  // ============================================================
  // HOT HERE PANEL
  // ============================================================
  private HotHerePanel buildHotHerePanel() {

    return new HotHerePanel(songLibraryService, songQueueService, imageLoader, creditsPer,
        creditsPer * 2, // priority cost placeholder
        POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2, POPULARITY_THRESHOLD_3, HOME_GRID_COLS,
        HOME_GRID_ROWS, HOME_TILE_ART_W, HOME_TILE_ART_H);
  }

  // ============================================================
  // GENRE PANEL
  // ============================================================
  private GenrePanel buildGenresPanel() {

    return new GenrePanel(songLibraryService, songQueueService, imageLoader, creditsPer,
        creditsPer * 2, // priority cost placeholder
        POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2, POPULARITY_THRESHOLD_3, HOME_GRID_COLS,
        HOME_GRID_ROWS, HOME_TILE_ART_W, HOME_TILE_ART_H);
  }



  // ============================================================
  // QUEUE PANEL
  // ============================================================
  private JPanel buildQueuePanel() {

    //
    // LIST PANEL
    //
    JPanel queueListPanel = new JPanel(new BorderLayout());
    queueListPanel.setOpaque(false);

    queueList.setOpaque(false);
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
    queueDetailsPanel.setOpaque(false);

    //
    // ROOT
    //
    queueRootPanel.setOpaque(false);
    queueRootPanel.removeAll();
    queueRootPanel.add(queueListPanel, "LIST");
    queueRootPanel.add(queueDetailsPanel, "DETAILS");

    queueCardLayout.show(queueRootPanel, "LIST");

    return queueRootPanel;
  }

  // QUEUE SONG DETAILS
  private void showQueueSongDetails(SongQueueEntryDto songQueueEntryDto) {

    SongDto song = songQueueEntryDto.getSong();

    queueDetailsPanel.removeAll();

    //
    // BACK BUTTON
    //
    JButton backButton = new JButton("← BACK");
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
    contentPanel.setOpaque(false);
    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
    contentPanel.setBorder(new EmptyBorder(40, 40, 40, 40));

    //
    // COVER ART
    //
    queueDetailsCoverArt.setAlignmentX(CENTER_ALIGNMENT);
    try {
      if (song.getCoverArtPath() != null) {
        queueDetailsCoverArt.setIcon(imageLoader.loadImage(song.getCoverArtPath(), 320, 320));
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
      title.setText(value.getSong().getSongName());
      subtitle.setText(value.getSong().getArtistName() + " • " + value.getSong().getAlbumName());

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
        if (value.getSong().getCoverArtPath() != null) {
          cover.setIcon(imageLoader.loadFilesystemImage(value.getSong().getCoverArtPath(), 56, 56));
        } else {
          cover.setIcon(null);
        }
      } catch (Exception e) {
        cover.setIcon(null);
      }

      // -------------------------
      // PLAYING NOW + SELECTION HIGHLIGHT
      // -------------------------
      boolean isPlaying = nowPlayingSong != null && nowPlayingSong.equals(value);

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
    panel.setOpaque(false);
    panel.setBorder(new EmptyBorder(10, 20, 10, 20));

    //
    // LEFT : CREDITS
    //
    JPanel creditsPanel = new JPanel(new BorderLayout(10, 0));
    creditsPanel.setOpaque(true);
    creditsPanel.setBackground(Color.BLACK);
    creditsPanel.setBorder(BorderFactory.createMatteBorder(2, 1, 1, 1, Color.WHITE));
    Dimension sidePanelSize = new Dimension(485, 100);
    creditsPanel.setPreferredSize(sidePanelSize);
    creditsPanel.setMinimumSize(sidePanelSize);
    creditsPanel.setMaximumSize(sidePanelSize);

    //
    // LOCATION LOGO (96x96 — same size as Now Playing cover art)
    //
    JLabel locationLogo = new JLabel();
    locationLogo.setHorizontalAlignment(SwingConstants.CENTER);
    locationLogo.setVerticalAlignment(SwingConstants.CENTER);
    locationLogo.setOpaque(true);
    locationLogo.setBackground(new Color(20, 20, 28));
    locationLogo.setPreferredSize(new Dimension(96, 96));
    locationLogo.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 90), 1));

    // Try JPG first
    ImageIcon logoIcon = imageLoader.loadImage("locationLogo.jpg", 96, 96);

    // Fallback PNG
    if (logoIcon == null) {
      logoIcon = imageLoader.loadImage("locationLogo.png", 96, 96);
    }

    if (logoIcon != null) {
      locationLogo.setIcon(logoIcon);
    } else {
      // Fallback text
      locationLogo.setText("★");
      locationLogo.setForeground(Color.WHITE);
      locationLogo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
    }

    //
    // CREDITS TEXT (stacked labels, same as before)
    //
    JPanel creditsTextPanel = new JPanel();
    creditsTextPanel.setBackground(Color.BLACK);
    creditsTextPanel.setOpaque(true);
    creditsTextPanel.setLayout(new BoxLayout(creditsTextPanel, BoxLayout.Y_AXIS));

    JLabel creditsTitle = new JLabel("CREDITS: 12");
    creditsTitle.setForeground(Color.YELLOW);
    creditsTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));

    JLabel creditDescription = new JLabel(buildCreditsDescription());
    creditDescription.setForeground(Color.WHITE);
    creditDescription.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));

    creditsTextPanel.add(creditsTitle);
    creditsTextPanel.add(Box.createVerticalStrut(5));
    creditsTextPanel.add(creditDescription);

    creditsPanel.add(locationLogo, BorderLayout.WEST);
    creditsPanel.add(creditsTextPanel, BorderLayout.CENTER);

    //
    // CENTER : BANNER WITH LOGO
    //
    JPanel bannerPanel = new JPanel(new GridBagLayout());
    bannerPanel.setOpaque(false);

    JLabel bannerLabel = new JLabel();
    bannerLabel.setOpaque(false);
    bannerLabel.setHorizontalAlignment(SwingConstants.CENTER);
    bannerLabel.setVerticalAlignment(SwingConstants.CENTER);

    // Loaded at scaled dimensions to fit perfectly within the 100px banner row height constraints
    ImageIcon icon = imageLoader.loadImage("JukeANatorLogo.png", 320, 96);
    Image transparentStrippedImage = ImageLoader.createTransparentImage(icon.getImage(), false, 15);
    icon = new ImageIcon(transparentStrippedImage);
    bannerLabel.setIcon(icon);
    bannerPanel.add(bannerLabel);

    //
    // RIGHT : NOW PLAYING
    //
    nowPlayingPanel = buildNowPlayingPanel();
    JPanel nowPlayingWrapper = new JPanel(new BorderLayout());
    nowPlayingWrapper.setOpaque(false);
    Dimension wrapperSize = new Dimension(485, 100);
    nowPlayingWrapper.setPreferredSize(wrapperSize);
    nowPlayingWrapper.setMinimumSize(wrapperSize);
    nowPlayingWrapper.setMaximumSize(wrapperSize);
    nowPlayingWrapper.add(nowPlayingPanel, BorderLayout.CENTER);

    panel.add(creditsPanel, BorderLayout.WEST);
    panel.add(bannerPanel, BorderLayout.CENTER);
    panel.add(nowPlayingWrapper, BorderLayout.EAST);

    return panel;
  }

  private String buildCreditsDescription() {

    int oneDollarCredits = creditsPer;
    int fiveDollarCredits = (5 * creditsPer) + fiveBonusCredits;
    int tenDollarCredits = (10 * creditsPer) + tenBonusCredits;

    return String.format("1$=%dcr | 5$=%dcr | 10$=%dcr", oneDollarCredits, fiveDollarCredits,
        tenDollarCredits);
  }

  // NOW PLAYING PANEL
  private JPanel buildNowPlayingPanel() {

    JPanel panel = new JPanel(new BorderLayout(10, 0));
    panel.setOpaque(true);
    panel.setBackground(Color.BLACK);
    panel.setBorder(BorderFactory.createMatteBorder(2, 1, 1, 1, Color.WHITE));

    // Fixed size — always the same whether a song is playing or not.
    // Sized to match the "song playing" state so the center logo never shifts.
    Dimension fixedSize = new Dimension(450, 100);
    panel.setPreferredSize(fixedSize);
    panel.setMinimumSize(fixedSize);
    panel.setMaximumSize(fixedSize);

    //
    // LEFT : PLAY STATUS (animated GIF / paused icon)
    //
    playStatus.setPreferredSize(new Dimension(96, 96));
    playStatus.setHorizontalAlignment(SwingConstants.CENTER);
    playStatus.setBorder(null);

    //
    // CENTER : TEXT PANEL
    // "NOW PLAYING:" label removed — gives the three song info lines room to breathe.
    //
    JPanel textPanel = new JPanel();
    textPanel.setOpaque(false);
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

    // Add a little top padding so the text sits centred vertically
    textPanel.setBorder(new EmptyBorder(8, 0, 8, 0));

    songLabel.setForeground(TEXT_PRIMARY);
    songLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));

    artistLabel.setForeground(TEXT_PRIMARY);
    artistLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));

    albumLabel.setForeground(TEXT_SECONDARY);
    albumLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

    textPanel.add(Box.createVerticalGlue());
    textPanel.add(songLabel);
    textPanel.add(Box.createVerticalStrut(4));
    textPanel.add(artistLabel);
    textPanel.add(Box.createVerticalStrut(4));
    textPanel.add(albumLabel);
    textPanel.add(Box.createVerticalGlue());

    //
    // RIGHT : COVER ART
    //
    albumArtLabel.setPreferredSize(new Dimension(96, 96));
    albumArtLabel.setHorizontalAlignment(SwingConstants.CENTER);
    albumArtLabel.setBorder(null);

    panel.add(playStatus, BorderLayout.WEST);
    panel.add(textPanel, BorderLayout.CENTER);
    panel.add(albumArtLabel, BorderLayout.EAST);

    panel.setVisible(false); // hidden until a song starts

    return panel;
  }



  // ============================================================
  // PLACEHOLDER
  // ============================================================
  private JPanel buildPlaceholderPanel() {

    JPanel panel = new JPanel(new CardLayout());
    panel.setOpaque(false);

    JLabel label = new JLabel("NOT IMPLEMENTED");
    label.setForeground(TEXT_SECONDARY);
    label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
    label.setHorizontalAlignment(SwingConstants.CENTER);

    panel.add(label);

    return panel;
  }



  // FULLSCREEN
  public void showFullscreen() {

    GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

    gd.setFullScreenWindow(this);
  }
  
  // HOT HERE
  public void refreshMusicByPopularityResults() {
    
    hotHerePanel.refreshMusicByPopularityResults();
  }

  // GENRE LIST
  public void setGenres(List<GenreDto> genres) {

    genrePanel.setGenres(genres);
  }

  // SONG QUEUE LIST
  public void setQueue(List<SongQueueEntryDto> queue) {

    SwingUtilities.invokeLater(() -> {

      queueListModel.clear();

      if (queue != null) {
        queue.forEach(queueListModel::addElement);
      }
    });
  }

  // NOW PLAYING
  public void setNowPlaying(SongDto songDto) {
    SwingUtilities.invokeLater(() -> {
      if (songDto == null) {
        clearNowPlaying();
        return;
      }

      songLabel.setText(songDto.getSongName());
      artistLabel.setText(songDto.getArtistName());
      albumLabel.setText(songDto.getAlbumName());
      albumArtLabel.setIcon(imageLoader.loadFilesystemImage(songDto.getCoverArtPath(), 96, 96));

      musicPaused = false;
      playStatus.setIcon(
          imageLoader.loadClasspathImage("music_playing.gif", 96, 96, Image.SCALE_DEFAULT));

      // Make the panel visible now that there's something to show.
      nowPlayingPanel.setVisible(true);
    });
  }

  private void clearNowPlaying() {
    songLabel.setText("");
    artistLabel.setText("");
    albumLabel.setText("");
    albumArtLabel.setIcon(null);
    playStatus.setIcon(null);
    musicPaused = false;

    // Hide the entire panel — but keep its space so the logo stays centred.
    nowPlayingPanel.setVisible(false);
  }

  // TOGGLE MUSIC PLAY STATE ICON
  public void toggleMusicPlayStateIcon() {

    SwingUtilities.invokeLater(() -> {

      //
      // If nothing is playing, remain blank
      //
      if (songLabel.getText() == null || songLabel.getText().isBlank()) {

        playStatus.setIcon(null);
        return;
      }

      musicPaused = !musicPaused;

      if (musicPaused) {
        playStatus.setIcon(
            imageLoader.loadClasspathImage("music_paused.png", 96, 96, Image.SCALE_DEFAULT));
      } else {
        playStatus.setIcon(
            imageLoader.loadClasspathImage("music_playing.gif", 96, 96, Image.SCALE_DEFAULT));
      }
    });
  }
}
