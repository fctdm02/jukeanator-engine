package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
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
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;
import com.djt.jukeanator_engine.ui.model.CreditManager;

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
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);

  // TOP PANEL
  private final CreditManager creditManager;
  private JLabel creditsTitle;
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

  // ADMIN TAB
  private AdminPanel adminPanel;

  // ── OVERLAY CARD SYSTEM (replaces former JDialog popups) ───────────────────
  private static final String CARD_TABS = "TABS";
  private static final String CARD_ADD_SONG = "ADD_SONG";
  private static final String CARD_SONG_QUEUE = "SONG_QUEUE";
  private static final String CARD_EDIT_ALBUM = "EDIT_ALBUM";

  private final CardLayout overlayCardLayout = new CardLayout();
  private final JPanel overlayRoot = new JPanel(overlayCardLayout) {
    private static final long serialVersionUID = 1L;

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();

      g2.setColor(new Color(10, 10, 10));
      g2.fillRect(0, 0, w, h);

      float[] fractions = {0.0f, 0.20f, 0.42f, 0.62f, 0.82f, 1.0f};
      Color[] colors =
          {new Color(140, 50, 50, 90), new Color(140, 90, 30, 80), new Color(80, 110, 40, 70),
              new Color(30, 100, 110, 70), new Color(40, 60, 140, 80), new Color(100, 30, 140, 90)};
      g2.setPaint(new LinearGradientPaint(new Point2D.Float(0, 0), new Point2D.Float(w, h),
          fractions, colors));
      g2.fillRect(0, 0, w, h);

      g2.dispose();
      // Do NOT call super — we own the background entirely
    }
  };
  private JTabbedPane contentPanelTabs;

  private AddSongToQueueCard addSongToQueueCard;
  private SongQueueCard songQueueCard;
  private EditAlbumCard editAlbumCard;



  // NOW PLAYING
  private List<SongQueueEntryDto> currentQueue = new java.util.ArrayList<>();
  private final JLabel albumArtLabel = new JLabel();
  private final JLabel songLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel artistLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel albumLabel = new JLabel("", SwingConstants.LEFT);
  private final JLabel playStatus = new JLabel();
  private boolean musicPaused = false;


  // SONG CREDITS
  private final char incrementCreditsKey;
  private int numCredits = 0;
  private final int priorityCostMultiplier;
  private final int creditsPerDollar;
  private final int fiveDollarBonusCredits;
  private final int tenDollarBonusCredits;


  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public JukeANatorFrame(JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties,
      SongLibraryService songLibraryService, SongQueueService songQueueService,
      SongPlayerService songPlayerService) {

    this.jukeANatorUserInterfaceProperties = jukeANatorUserInterfaceProperties;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;

    this.incrementCreditsKey = jukeANatorUserInterfaceProperties.getIncrementCreditsKey();
    this.numCredits = jukeANatorUserInterfaceProperties.getNumCredits();
    this.priorityCostMultiplier = jukeANatorUserInterfaceProperties.getPriorityCostMultiplier();
    this.creditsPerDollar = this.jukeANatorUserInterfaceProperties.getCreditsPerDollar();
    this.fiveDollarBonusCredits =
        this.jukeANatorUserInterfaceProperties.getFiveDollarBonusCredits();
    this.tenDollarBonusCredits = this.jukeANatorUserInterfaceProperties.getTenDollarBonusCredits();

    this.enableTypeAheadSearch = this.jukeANatorUserInterfaceProperties.isEnableTypeAheadSearch();

    this.creditManager = new CreditManager(numCredits, creditsPerDollar, fiveDollarBonusCredits,
        tenDollarBonusCredits);

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
    contentPane.setOpaque(false);
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
    contentPanelTabs = buildContentPanelTabs();

    overlayRoot.add(contentPanelTabs, CARD_TABS);
    overlayRoot.add(placeholder(), CARD_ADD_SONG);
    overlayRoot.add(placeholder(), CARD_SONG_QUEUE);
    overlayRoot.add(placeholder(), CARD_EDIT_ALBUM);
    overlayCardLayout.show(overlayRoot, CARD_TABS);

    getContentPane().add(overlayRoot, BorderLayout.CENTER);

    //
    // CREDIT MANAGER AND KEYBOARD LISTENER
    //
    // Register listener to update UI instantly when credit manager updates
    this.creditManager.addListener(() -> {
      creditsTitle.setText("CREDITS: " + creditManager.getCredits());
    });

    // Hardware Bill Acceptor Key Bindings
    this.setFocusable(true);
    this.addKeyListener(new java.awt.event.KeyAdapter() {
      @Override
      public void keyTyped(java.awt.event.KeyEvent e) {
        if (e.getKeyChar() == incrementCreditsKey) {
          creditManager.addDollar();
        }
      }
    });

    requestFocusInWindow();
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
        // Paint the same screen gradient into the content border area so that
        // both the tabs view and the overlay cards share a consistent background.
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int tabAreaHeight = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        int contentH = tabPane.getHeight() - tabAreaHeight;
        int w = tabPane.getWidth();

        g2.setColor(new Color(10, 10, 10));
        g2.fillRect(0, 0, w, contentH);

        float[] fractions = {0.0f, 0.20f, 0.42f, 0.62f, 0.82f, 1.0f};
        Color[] colors = {new Color(140, 50, 50, 90), new Color(140, 90, 30, 80),
            new Color(80, 110, 40, 70), new Color(30, 100, 110, 70), new Color(40, 60, 140, 80),
            new Color(100, 30, 140, 90)};
        // Use the full pane dimensions for the gradient so it matches the
        // frame-level gradient exactly (same diagonal, same colour stops).
        g2.setPaint(new LinearGradientPaint(new Point2D.Float(0, 0),
            new Point2D.Float(w, tabPane.getHeight()), fractions, colors));
        g2.fillRect(0, 0, w, contentH);

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

    adminPanel = buildAdminPanel();
    tabs.addTab("ADMIN", adminPanel);

    tabs.setTabComponentAt(0, new JukeboxTabComponent("HOME", "⌂", new Color(255, 120, 120)));
    tabs.setTabComponentAt(1, new JukeboxTabComponent("SEARCH", "🔍", new Color(0, 220, 255)));
    tabs.setTabComponentAt(2, new JukeboxTabComponent("HOT HERE", "🔥", new Color(255, 80, 120)));
    tabs.setTabComponentAt(3, new JukeboxTabComponent("GENRES", "▣", Color.WHITE));
    tabs.setTabComponentAt(4, new JukeboxTabComponent("ADMIN", "⚙", new Color(255, 220, 0)));

    // Reset each tab to its default state when the user switches to it
    tabs.addChangeListener(e -> {
      switch (tabs.getSelectedIndex()) {
        case 1 -> searchPanel.resetToDefaultView();
        case 2 -> hotHerePanel.resetToDefaultView();
        case 3 -> genrePanel.resetToDefaultView();
        default -> {
          /* HOME and ADMIN require no reset */ }
      }
    });

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

    return new HomePanel(incrementCreditsKey, creditManager, songLibraryService, songQueueService,
        imageLoader, priorityCostMultiplier, POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2,
        POPULARITY_THRESHOLD_3, HOME_GRID_COLS, HOME_GRID_ROWS, HOME_TILE_ART_W, HOME_TILE_ART_H);
  }

  // ============================================================
  // SEARCH PANEL
  // ============================================================
  private SearchPanel buildSearchPanel() {

    return new SearchPanel(incrementCreditsKey, creditManager, songLibraryService, songQueueService,
        imageLoader, priorityCostMultiplier, POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2,
        POPULARITY_THRESHOLD_3, enableTypeAheadSearch, HOME_GRID_COLS, HOME_GRID_ROWS,
        HOME_TILE_ART_W, HOME_TILE_ART_H);
  }

  // ============================================================
  // HOT HERE PANEL
  // ============================================================
  private HotHerePanel buildHotHerePanel() {

    return new HotHerePanel(incrementCreditsKey, creditManager, songLibraryService,
        songQueueService, imageLoader, priorityCostMultiplier, POPULARITY_THRESHOLD_1,
        POPULARITY_THRESHOLD_2, POPULARITY_THRESHOLD_3, HOME_GRID_COLS, HOME_GRID_ROWS,
        HOME_TILE_ART_W, HOME_TILE_ART_H);
  }

  // ============================================================
  // GENRE PANEL
  // ============================================================
  private GenrePanel buildGenresPanel() {

    return new GenrePanel(incrementCreditsKey, creditManager, songLibraryService, songQueueService,
        imageLoader, priorityCostMultiplier, POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2,
        POPULARITY_THRESHOLD_3, HOME_GRID_COLS, HOME_GRID_ROWS, HOME_TILE_ART_W, HOME_TILE_ART_H);
  }

  // ============================================================
  // ADMIN PANEL
  // ============================================================
  private AdminPanel buildAdminPanel() {

    return new AdminPanel(this, songLibraryService, songQueueService, songPlayerService,
        creditManager, incrementCreditsKey, imageLoader);
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

    creditsTitle = new JLabel("CREDITS: " + numCredits);
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

    int oneDollarCredits = creditsPerDollar;
    int fiveDollarCredits = (5 * creditsPerDollar) + fiveDollarBonusCredits;
    int tenDollarCredits = (10 * creditsPerDollar) + tenDollarBonusCredits;

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

    // Clicking anywhere on the Now Playing panel opens the Song Queue dialog
    panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    panel.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        showSongQueueCard();
      }
    });

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
      currentQueue = queue != null ? queue : new java.util.ArrayList<>();
      adminPanel.setQueue(queue);
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

    nowPlayingPanel.setVisible(false);
  }

  // ============================================================
  // OVERLAY CARD SYSTEM (replaces former JDialog popups)
  // ============================================================

  private JPanel placeholder() {
    JPanel p = new JPanel();
    p.setOpaque(false);
    return p;
  }

  /** Returns to the TABS card — the previously-active tab/state is preserved as-is. */
  private void hideOverlay() {
    overlayCardLayout.show(overlayRoot, CARD_TABS);
    requestFocusInWindow();
  }

  private void replaceOverlayCard(String name, JPanel newPanel) {
    for (int i = overlayRoot.getComponentCount() - 1; i >= 0; i--) {
      if (name.equals(overlayRoot.getComponent(i).getName())) {
        overlayRoot.remove(i);
        break;
      }
    }
    newPanel.setName(name);
    overlayRoot.add(newPanel, name);
    overlayRoot.revalidate();
  }

  /**
   * Shows the "Add Song to Queue" overlay for the given song. Available from Home, Search, Hot
   * Here, and Genres.
   */
  public void showAddSongToQueueCard(SongDto song) {

    if (addSongToQueueCard != null) {
      addSongToQueueCard.teardown();
    }

    addSongToQueueCard = new AddSongToQueueCard(song, imageLoader, priorityCostMultiplier,
        songQueueService, creditManager, incrementCreditsKey, this::hideOverlay);

    addSongToQueueCard.setOpaque(false);

    replaceOverlayCard(CARD_ADD_SONG, addSongToQueueCard);
    overlayCardLayout.show(overlayRoot, CARD_ADD_SONG);
    addSongToQueueCard.onShown();
  }

  /**
   * Shows the Song Queue overlay. Used by the Now Playing panel. Disabled while the Admin tab is
   * active, since AdminPanel already has song-queue functionality.
   */
  public void showSongQueueCard() {

    if (contentPanelTabs.getSelectedComponent() == adminPanel) {
      return; // disabled on Admin tab
    }

    if (songQueueCard == null) {
      songQueueCard = new SongQueueCard(songPlayerService, currentQueue, songQueueService,
          creditManager, imageLoader, POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2,
          POPULARITY_THRESHOLD_3, incrementCreditsKey, this::hideOverlay);
      replaceOverlayCard(CARD_SONG_QUEUE, songQueueCard);
    }

    songQueueCard.setOpaque(false);

    songQueueCard.setQueue(currentQueue);
    overlayCardLayout.show(overlayRoot, CARD_SONG_QUEUE);
    songQueueCard.onShown();
  }

  /**
   * Shows the Edit Album overlay. Used only by AdminPanel.
   * 
   * @param selectedAlbum
   * @param invalidAlbumsList
   */
  public void showEditAlbumCard(AlbumDto selectedAlbum, List<AlbumDto> invalidAlbumsList) {

    if (editAlbumCard == null) {
      editAlbumCard = new EditAlbumCard(songLibraryService, selectedAlbum, invalidAlbumsList,
          this::hideOverlay);
      replaceOverlayCard(CARD_EDIT_ALBUM, editAlbumCard);
    } else {
      editAlbumCard.editAlbum(selectedAlbum, invalidAlbumsList);
    }

    overlayCardLayout.show(overlayRoot, CARD_EDIT_ALBUM);
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
