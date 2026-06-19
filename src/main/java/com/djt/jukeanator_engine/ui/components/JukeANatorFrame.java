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

  // TOP PANEL
  private final CreditManager creditManager;
  private JLabel creditsTitle;
  private JPanel nowPlayingPanel;

  // HOME TAB
  /**
   * Grid layout profile for the Home screen, computed once from the actual screen dimensions so
   * that the album grid fills the available space correctly on any resolution or orientation.
   *
   * <p>
   * Initialised in {@link #initialize()} (which runs on the AWT thread via the constructor)
   * <em>before</em> {@link #buildHomePanel()} is called, so the value is always available when the
   * panel is constructed.
   */
  private LayoutTheme.GridProfile albumGridProfile;
  private HomePanel homePanel;

  // SEARCH TAB
  private final boolean enableTypeAheadSearch;
  private SearchPanel searchPanel;

  // HOT HERE TAB
  private HotHerePanel hotHerePanel;

  // GENRE TAB
  /**
   * Genre-tile grid profile for the Genres screen, computed once from the actual screen dimensions.
   * Passed to GenrePanel instead of the raw HOME_GRID_* constants.
   */
  private LayoutTheme.GenreGridProfile genreGridProfile;

  private GenrePanel genrePanel;

  // QUEUE TAB
  private QueuePanel queuePanel;

  // ADMIN TAB
  private int preAdminTabIndex = 1; // Tracks the tab to return to when exiting Admin
  private AdminPanel adminPanel;
  private java.util.concurrent.CompletableFuture<java.util.List<AlbumDto>> adminAlbumPrefetch;

  // ── OVERLAY CARD SYSTEM (replaces former JDialog popups) ───────────────────
  private static final String CARD_TABS = "TABS";
  private static final String CARD_ADD_SONG = "ADD_SONG";
  private static final String CARD_SONG_QUEUE = "SONG_QUEUE";
  private static final String CARD_EDIT_ALBUM = "EDIT_ALBUM";
  private static final String CARD_LOGIN = "LOGIN";
  private static final String CARD_NOW_PLAYING_ALBUM = "NOW_PLAYING_ALBUM";

  private final CardLayout overlayCardLayout = new CardLayout();
  private final JPanel overlayRoot = new JPanel(overlayCardLayout) {
    private static final long serialVersionUID = 1L;

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();

      g2.setColor(ColorTheme.get().appBgBase);
      g2.fillRect(0, 0, w, h);

      float[] fractions = ColorTheme.get().appGradFractions();
      Color[] colors = ColorTheme.get().appGradColors();
      g2.setPaint(new LinearGradientPaint(new Point2D.Float(0, 0), new Point2D.Float(w, h),
          fractions, colors));
      g2.fillRect(0, 0, w, h);

      g2.dispose();
      // Do NOT call super — we own the background entirely
    }
  };
  private JTabbedPane contentPanelTabs;

  // Guards the tab ChangeListener against spurious resets that fire when the
  // overlay card system shows or hides — neither action is a genuine tab switch.
  private boolean overlayTransitionInProgress = false;
  private int lastSelectedTabIndex = -1;

  private AddSongToQueueCard addSongToQueueCard;
  private SongQueueCard songQueueCard;
  private EditAlbumCard editAlbumCard;
  private LoginToAdminPanelCard loginToAdminPanelCard;
  private AlbumDetailCard nowPlayingAlbumCard;



  // NOW PLAYING
  private List<SongQueueEntryDto> currentQueue = new java.util.ArrayList<>();
  private SongDto currentNowPlayingSong; // tracked so the panel click knows which album to show
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


  // SCREENSAVER
  private final Dimension screenSize = GraphicsEnvironment.getLocalGraphicsEnvironment()
      .getDefaultScreenDevice().getDefaultConfiguration().getBounds().getSize();

  private final int screenWidth = screenSize.width;
  private final int screenHeight = screenSize.height;
  private final boolean enableScreenSaver;
  private ScreenSaverWindow screenSaverWindow;


  // HIBERNATION
  private final boolean enableHibernation;
  private final int hibernateBegin;
  private final int hibernateEnd;
  private HibernationWindow hibernationWindow;
  private boolean hibernationActive = false;


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

    this.enableScreenSaver = this.jukeANatorUserInterfaceProperties.isEnableScreenSaver();

    this.enableHibernation = this.jukeANatorUserInterfaceProperties.isEnableHibernation();
    this.hibernateBegin = this.jukeANatorUserInterfaceProperties.getHibernateBegin();
    this.hibernateEnd = this.jukeANatorUserInterfaceProperties.getHibernateEnd();

    initialize();
  }

  // INITIALIZE
  private void initialize() {

    setTitle("JukeANator");
    setUndecorated(true);
    setBackground(ColorTheme.get().bgFieldDark);

    JPanel contentPane = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Base dark fill first
        g2.setColor(ColorTheme.get().appBgBase);
        g2.fillRect(0, 0, w, h);

        // Diagonal rainbow overlay — top-left to bottom-right,
        // matching the AMI screenshot's warm-left / cool-right spread
        float[] fractions = ColorTheme.get().appGradFractions();
        Color[] colors = ColorTheme.get().appGradColors();
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

    // ── Compute the Home grid profile for the actual screen dimensions ────────
    // screenWidth / screenHeight are already populated from GraphicsEnvironment
    // at field-initialisation time (lines 158-162 in the original).
    albumGridProfile = LayoutTheme.get().homeGridProfile(screenWidth, screenHeight);
    genreGridProfile = LayoutTheme.get().genreGridProfile(screenWidth, screenHeight);

    //
    // TOP 10%
    //
    JPanel topPanel = buildTopPanel();
    topPanel.setPreferredSize(new Dimension(100, LayoutTheme.get().topPanelHeight));
    getContentPane().add(topPanel, BorderLayout.NORTH);

    //
    // BOTTOM 90%
    //
    contentPanelTabs = buildContentPanelTabs();

    overlayRoot.add(contentPanelTabs, CARD_TABS);
    overlayRoot.add(placeholder(), CARD_ADD_SONG);
    overlayRoot.add(placeholder(), CARD_SONG_QUEUE);
    overlayRoot.add(placeholder(), CARD_EDIT_ALBUM);
    overlayRoot.add(placeholder(), CARD_LOGIN);
    overlayRoot.add(placeholder(), CARD_NOW_PLAYING_ALBUM);
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
    // Use WHEN_IN_FOCUSED_WINDOW so the keystroke is caught regardless of which
    // child component (e.g. SongQueueCard) currently holds keyboard focus.
    // A raw KeyListener on JFrame would silently stop working the moment any
    // child panel calls requestFocusInWindow().
    javax.swing.KeyStroke billAcceptorStroke =
        javax.swing.KeyStroke.getKeyStroke(incrementCreditsKey);
    final String BILL_ACCEPTOR_ACTION = "billAcceptor";
    getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(billAcceptorStroke,
        BILL_ACCEPTOR_ACTION);
    getRootPane().getActionMap().put(BILL_ACCEPTOR_ACTION, new javax.swing.AbstractAction() {
      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(java.awt.event.ActionEvent e) {
        creditManager.addDollar();
      }
    });

    // Physical Escape Key Binding to Toggle/Bypass Admin Panel
    javax.swing.KeyStroke escapeStroke =
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
    final String ESCAPE_ACTION = "escapeAction";
    getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeStroke,
        ESCAPE_ACTION);
    getRootPane().getActionMap().put(ESCAPE_ACTION, new javax.swing.AbstractAction() {
      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(java.awt.event.ActionEvent e) {
        // CASE 1: If AdminPanel is currently showing, return to the previous screen
        if (contentPanelTabs.getSelectedIndex() == 6) {
          contentPanelTabs.setSelectedIndex(preAdminTabIndex);
          lastSelectedTabIndex = preAdminTabIndex;
          return;
        }

        // CASE 2: If we are not in AdminPanel, perform the bypass login workflow
        if (loginToAdminPanelCard != null) {
          loginToAdminPanelCard.dismiss();
        }

        hideOverlay();

        // Safe lazy initialization of AdminPanel
        if (adminPanel == null) {
          if (adminAlbumPrefetch == null) {
            adminAlbumPrefetch =
                java.util.concurrent.CompletableFuture.supplyAsync(songLibraryService::getAlbums);
          }
          adminPanel = buildAdminPanel();
          contentPanelTabs.setComponentAt(6, adminPanel);
          adminPanel.setQueue(currentQueue);
        }

        // Save the current tab index before jumping to AdminPanel (index 6)
        preAdminTabIndex = contentPanelTabs.getSelectedIndex();

        contentPanelTabs.setSelectedIndex(6);
        lastSelectedTabIndex = 6;
      }
    });

    if (this.enableScreenSaver) {

      this.screenSaverWindow = new ScreenSaverWindow(this, this.imageLoader, this.screenWidth,
          this.screenHeight, this.songLibraryService.getAlbums().size(), this.songPlayerService,
          this.songLibraryService);

      new IdleMonitor(

          () -> SwingUtilities.invokeLater(() -> {

            if (!this.screenSaverWindow.isVisible()) {

              this.screenSaverWindow.updateContent();

              this.screenSaverWindow.setVisible(true);
            }
          }),

          () -> SwingUtilities.invokeLater(() -> {

            if (screenSaverWindow.isVisible()) {
              screenSaverWindow.setVisible(false);
            }
          }));
    }

    // ── HIBERNATION ────────────────────────────────────────────────────
    if (this.enableHibernation) {

      this.hibernationWindow =
          new HibernationWindow(this, this.screenWidth, this.screenHeight, this.hibernateEnd,
              // onDismiss: Esc key pressed by service technician —
              // force-exit hibernation immediately regardless of the clock.
              () -> SwingUtilities.invokeLater(() -> {
                if (hibernationActive) {
                  hibernationActive = false;
                  hibernationWindow.setVisible(false);
                  songPlayerService.unlockQueue();
                }
              }));

      // Poll once per minute to enter / exit hibernation.
      // Initial delay of 5 s gives SongPlayerServiceImpl.initialize() time to
      // submit its first processQueue() task before the hibernation check can
      // set queueLocked=true — preventing the startup race that caused the
      // queue to never auto-populate when the app started inside a hibernate window.
      javax.swing.Timer hibernationTimer = new javax.swing.Timer(60_000,
          e -> SwingUtilities.invokeLater(this::updateHibernationState));
      hibernationTimer.setInitialDelay(5_000);
      hibernationTimer.start();
    }
    requestFocusInWindow();
  }

  /**
   * Shows or hides the hibernation overlay depending on whether the current wall-clock hour falls
   * within [hibernateBegin, hibernateEnd).
   *
   * <p>
   * On entry: locks the song queue and stops playback so music does not run unattended overnight.
   * <p>
   * On exit: unlocks the queue so normal operation resumes.
   * <p>
   * The screen-saver is also hidden on entry so the two overlays never compete for the same screen
   * real-estate.
   */
  private void updateHibernationState() {

    int hour = java.time.LocalTime.now().getHour();

    boolean inWindow;
    if (hibernateBegin < hibernateEnd) {
      // Normal span — e.g. 03:00 → 10:00
      inWindow = hour >= hibernateBegin && hour < hibernateEnd;
    } else {
      // Overnight span — e.g. 22:00 → 06:00
      inWindow = hour >= hibernateBegin || hour < hibernateEnd;
    }

    if (inWindow && !hibernationActive) {
      // ── ENTERING hibernation ──────────────────────────────────────
      hibernationActive = true;

      // Stop the screen-saver so only the hibernation overlay is shown.
      if (screenSaverWindow != null && screenSaverWindow.isVisible()) {
        screenSaverWindow.setVisible(false);
      }

      // lockQueue() stops the current song AND prevents the next one from
      // being dequeued — a single call replaces the previous two-step pattern.
      songPlayerService.lockQueue();

      hibernationWindow.setVisible(true);

    } else if (!inWindow && hibernationActive) {
      // ── EXITING hibernation ───────────────────────────────────────
      hibernationActive = false;

      hibernationWindow.setVisible(false);

      // Re-enable the queue so patrons can queue songs again.
      songPlayerService.unlockQueue();
    }
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
    UIManager.put("TabbedPane.unselectedBackground", ColorTheme.get().frameTabsTransparent);
    UIManager.put("TabbedPane.background", ColorTheme.get().frameTabsTransparent);

    JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        // Do not fill background — let JFrame gradient show through
      }
    };
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

    tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {

      final int TAB_WIDTH = LayoutTheme.get().tabWidth;
      final int SEPARATOR_HEIGHT = LayoutTheme.get().tabSeparatorHeight;

      //
      // FIXED TAB WIDTH
      //
      @Override
      protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
        return TAB_WIDTH;
      }

      @Override
      protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
        return LayoutTheme.get().tabHeight;
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
          g2.setColor(ColorTheme.get().bgFrostedGlassRing);
        } else {
          g2.setColor(ColorTheme.get().frameTabBgUnselected);
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

        g2.setColor(ColorTheme.get().appBgBase);
        g2.fillRect(0, 0, w, contentH);

        float[] fractions = ColorTheme.get().appGradFractions();
        Color[] colors = ColorTheme.get().appGradColors();
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
        g.setColor(ColorTheme.get().textSecondary);
        g.fillRect(0, separatorY, tabPane.getWidth(), SEPARATOR_HEIGHT);
      }
    });

    tabs.setForeground(ColorTheme.get().textPrimary);
    tabs.setBorder(null);
    tabs.setOpaque(false);
    tabs.setBackground(ColorTheme.get().frameTabsTransparent);

    // ── Tab index layout ──────────────────────────────────────────────────────
    // Index 0 : DUMMY (invisible, 0-size — balances hidden ADMIN on the right)
    // Index 1 : HOME
    // Index 2 : SEARCH
    // Index 3 : HOT HERE
    // Index 4 : GENRES
    // Index 5 : QUEUE
    // Index 6 : ADMIN (invisible/disabled — reached only via LoginToAdminPanelCard)
    //
    // With TAB_WIDTH=200 and 7 total tabs the centering inset formula gives:
    // leftInset = (screenWidth - 7*200) / 2
    // The two invisible tabs (0 and 6) each consume 0 visual pixels but their
    // slot width is suppressed to zero by a 0x0 preferred-size component, so
    // the five visible tabs are centred naturally.

    // Index 0 — invisible dummy (left balancer)
    tabs.addTab("", new JPanel());

    homePanel = buildHomePanel();
    tabs.addTab("HOME", homePanel); // index 1

    searchPanel = buildSearchPanel();
    tabs.addTab("SEARCH", searchPanel); // index 2

    hotHerePanel = buildHotHerePanel();
    tabs.addTab("HOT HERE", hotHerePanel); // index 3

    genrePanel = buildGenresPanel();
    tabs.addTab("GENRES", genrePanel); // index 4

    queuePanel = buildQueuePanel();
    tabs.addTab("QUEUE", queuePanel); // index 5

    // AdminPanel is lazy — the placeholder is swapped out on first successful login.
    // Do NOT call buildAdminPanel() here; it triggers a slow album-library load.
    tabs.addTab("ADMIN", new JPanel()); // index 6 (placeholder — replaced on first login)

    // Invisible zero-size header for the left dummy tab (index 0)
    JPanel dummyTabHeader = new JPanel();
    dummyTabHeader.setOpaque(false);
    dummyTabHeader.setPreferredSize(new Dimension(0, 0));
    tabs.setEnabledAt(0, false);
    tabs.setTabComponentAt(0, dummyTabHeader);

    tabs.setTabComponentAt(1,
        new JukeboxTabComponent("HOME", "⌂", ColorTheme.get().frameTabAccentHome));
    tabs.setTabComponentAt(2,
        new JukeboxTabComponent("SEARCH", "🔍", ColorTheme.get().frameTabAccentSearch));
    tabs.setTabComponentAt(3,
        new JukeboxTabComponent("HOT HERE", "🔥", ColorTheme.get().frameTabAccentHotHere));
    tabs.setTabComponentAt(4, new JukeboxTabComponent("GENRES", "▣", ColorTheme.get().textPrimary));
    tabs.setTabComponentAt(5,
        new JukeboxTabComponent("QUEUE", "♫", ColorTheme.get().frameTabAccentQueue));

    // Admin tab is invisible by default — access is gated by LoginToAdminPanelCard.
    // A zero-size transparent component keeps the tab structure intact while hiding it visually.
    tabs.setEnabledAt(6, false);
    JPanel invisibleTabHeader = new JPanel();
    invisibleTabHeader.setOpaque(false);
    invisibleTabHeader.setPreferredSize(new Dimension(0, 0));
    tabs.setTabComponentAt(6, invisibleTabHeader);

    // Select HOME (index 1) as the default visible tab
    tabs.setSelectedIndex(1);
    lastSelectedTabIndex = 1;

    // Reset each tab to its default state when the user switches to it.
    // Suppress resets triggered by the overlay card system showing/hiding
    // (both transitions make the JTabbedPane hidden/visible, which spuriously
    // fires this listener even though the selected index hasn't changed).
    tabs.addChangeListener(e -> {
      if (overlayTransitionInProgress)
        return;
      int selected = tabs.getSelectedIndex();
      if (selected == lastSelectedTabIndex)
        return;
      lastSelectedTabIndex = selected;
      switch (selected) {
        case 2 -> searchPanel.resetToDefaultView();
        case 3 -> hotHerePanel.resetToDefaultView();
        case 4 -> genrePanel.resetToDefaultView();
        case 5 -> queuePanel.onShown();
        default -> {
          /* HOME, DUMMY, and ADMIN require no reset */ }
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
      iconLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().tabIconFontSize));

      //
      // TEXT
      //
      JLabel textLabel = new JLabel(title);
      textLabel.setAlignmentX(CENTER_ALIGNMENT);
      textLabel.setForeground(accentColor);
      textLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().tabTextFontSize));

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
        POPULARITY_THRESHOLD_3, albumGridProfile);
  }

  // ============================================================
  // SEARCH PANEL
  // ============================================================
  private SearchPanel buildSearchPanel() {

    return new SearchPanel(incrementCreditsKey, creditManager, songLibraryService, songQueueService,
        imageLoader, priorityCostMultiplier, POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2,
        POPULARITY_THRESHOLD_3, enableTypeAheadSearch, albumGridProfile);
  }

  // ============================================================
  // HOT HERE PANEL
  // ============================================================
  private HotHerePanel buildHotHerePanel() {

    return new HotHerePanel(incrementCreditsKey, creditManager, songLibraryService,
        songQueueService, imageLoader, priorityCostMultiplier, POPULARITY_THRESHOLD_1,
        POPULARITY_THRESHOLD_2, POPULARITY_THRESHOLD_3, albumGridProfile);
  }

  // ============================================================
  // GENRE PANEL
  // ============================================================
  private GenrePanel buildGenresPanel() {

    return new GenrePanel(incrementCreditsKey, creditManager, songLibraryService, songQueueService,
        imageLoader, priorityCostMultiplier, POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2,
        POPULARITY_THRESHOLD_3, albumGridProfile, // album sub-grid (artist detail within Genres
                                                  // tab)
        genreGridProfile); // genre-tile grid (top-level genre picker)
  }

  // ============================================================
  // ADMIN PANEL
  // ============================================================
  private AdminPanel buildAdminPanel() {

    return new AdminPanel(this, songLibraryService, songQueueService, songPlayerService,
        creditManager, imageLoader, adminAlbumPrefetch);
  }

  // ============================================================
  // QUEUE PANEL
  // ============================================================
  /**
   * A transparent panel that hosts the SongQueueCard content inline as a proper tab, painting the
   * same diagonal gradient as the rest of the screen so the background appears seamless. The
   * SongQueueCard is created once and reused; calling {@link QueuePanel#onShown()} refreshes the
   * list and restarts the countdown.
   */
  private QueuePanel buildQueuePanel() {

    return new QueuePanel(songPlayerService, currentQueue, songQueueService, creditManager,
        imageLoader, POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2, POPULARITY_THRESHOLD_3,
        /* onDismiss — Cancel button navigates back to HOME (index 1) */
        () -> SwingUtilities.invokeLater(() -> {
          contentPanelTabs.setSelectedIndex(1);
          lastSelectedTabIndex = 1;
        }));
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
    creditsPanel.setBackground(ColorTheme.get().bgFieldDark);
    creditsPanel
        .setBorder(BorderFactory.createMatteBorder(2, 1, 1, 1, ColorTheme.get().textPrimary));
    Dimension sidePanelSize =
        new Dimension(LayoutTheme.get().creditsPanelW, LayoutTheme.get().creditsPanelH);
    creditsPanel.setPreferredSize(sidePanelSize);
    creditsPanel.setMinimumSize(sidePanelSize);
    creditsPanel.setMaximumSize(sidePanelSize);

    //
    // LOCATION LOGO (same size as Now Playing cover art)
    //
    JLabel locationLogo = new JLabel();
    locationLogo.setHorizontalAlignment(SwingConstants.CENTER);
    locationLogo.setVerticalAlignment(SwingConstants.CENTER);
    locationLogo.setOpaque(true);
    locationLogo.setBackground(ColorTheme.get().frameLocationLogoBg);
    locationLogo.setPreferredSize(
        new Dimension(LayoutTheme.get().topPanelIconSize, LayoutTheme.get().topPanelIconSize));
    locationLogo.setBorder(BorderFactory.createLineBorder(ColorTheme.get().coverArtBorder, 1));

    // Try JPG first
    ImageIcon logoIcon = imageLoader.loadImage("locationLogo.jpg",
        LayoutTheme.get().topPanelIconSize, LayoutTheme.get().topPanelIconSize);

    // Fallback PNG
    if (logoIcon == null) {
      logoIcon = imageLoader.loadImage("locationLogo.png", LayoutTheme.get().topPanelIconSize,
          LayoutTheme.get().topPanelIconSize);
    }

    if (logoIcon != null) {
      locationLogo.setIcon(logoIcon);
    } else {
      // Fallback text
      locationLogo.setText("★");
      locationLogo.setForeground(ColorTheme.get().textPrimary);
      locationLogo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
    }

    //
    // CREDITS TEXT (stacked labels, same as before)
    //
    JPanel creditsTextPanel = new JPanel();
    creditsTextPanel.setBackground(ColorTheme.get().bgFieldDark);
    creditsTextPanel.setOpaque(true);
    creditsTextPanel.setLayout(new BoxLayout(creditsTextPanel, BoxLayout.Y_AXIS));

    creditsTitle = new JLabel("CREDITS: " + numCredits);
    creditsTitle.setForeground(ColorTheme.get().frameCreditsTitleColor);
    creditsTitle
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeCreditTitle));

    JLabel creditDescription = new JLabel(buildCreditsDescription());
    creditDescription.setForeground(ColorTheme.get().textPrimary);
    creditDescription
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeCreditDesc));

    creditsTextPanel.add(creditsTitle);
    creditsTextPanel.add(Box.createVerticalStrut(5));
    creditsTextPanel.add(creditDescription);

    creditsPanel.add(locationLogo, BorderLayout.WEST);
    creditsPanel.add(creditsTextPanel, BorderLayout.CENTER);

    // Clicking the credits panel opens the admin login card.
    creditsPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    creditsPanel.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        showLoginToAdminPanelCard();
      }
    });

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
    ImageIcon icon = imageLoader.loadImage("JukeANatorLogo.png",
        LayoutTheme.get().topPanelIconSize * 3, LayoutTheme.get().topPanelIconSize);
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
    Dimension wrapperSize =
        new Dimension(LayoutTheme.get().nowPlayingWrapperW, LayoutTheme.get().nowPlayingWrapperH);
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
    panel.setBackground(ColorTheme.get().bgFieldDark);
    panel.setBorder(BorderFactory.createMatteBorder(2, 1, 1, 1, ColorTheme.get().textPrimary));

    // Fixed size — always the same whether a song is playing or not.
    // Sized to match the "song playing" state so the center logo never shifts.
    Dimension fixedSize =
        new Dimension(LayoutTheme.get().nowPlayingPanelW, LayoutTheme.get().nowPlayingPanelH);
    panel.setPreferredSize(fixedSize);
    panel.setMinimumSize(fixedSize);
    panel.setMaximumSize(fixedSize);

    //
    // LEFT : PLAY STATUS (animated GIF / paused icon)
    //
    playStatus.setPreferredSize(
        new Dimension(LayoutTheme.get().topPanelIconSize, LayoutTheme.get().topPanelIconSize));
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

    songLabel.setForeground(ColorTheme.get().textPrimary);
    songLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeTrackSong));

    artistLabel.setForeground(ColorTheme.get().textPrimary);
    artistLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeTrackSong));

    albumLabel.setForeground(ColorTheme.get().textSecondary);
    albumLabel
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeTrackArtist));

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
    albumArtLabel.setPreferredSize(
        new Dimension(LayoutTheme.get().topPanelIconSize, LayoutTheme.get().topPanelIconSize));
    albumArtLabel.setHorizontalAlignment(SwingConstants.CENTER);
    albumArtLabel.setBorder(null);

    panel.add(playStatus, BorderLayout.WEST);
    panel.add(textPanel, BorderLayout.CENTER);
    panel.add(albumArtLabel, BorderLayout.EAST);

    panel.setVisible(false); // hidden until a song starts

    // Clicking anywhere on the Now Playing panel opens the album detail card
    // for the currently-playing song, showing the full track listing.
    panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    panel.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        showNowPlayingAlbumCard();
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
      // adminPanel is lazy — only push the queue if it has already been instantiated.
      if (adminPanel != null) {
        adminPanel.setQueue(queue);
      }
      queuePanel.setQueue(currentQueue);
      // Keep the overlay SongQueueCard's reference in sync even when it is not
      // currently visible. Without this, the card would show a stale list
      // (including the now-playing song) until the user closed and reopened it.
      if (songQueueCard != null) {
        songQueueCard.setQueue(currentQueue);
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
      albumArtLabel.setIcon(imageLoader.loadFilesystemImage(songDto.getCoverArtPath(),
          LayoutTheme.get().topPanelIconSize, LayoutTheme.get().topPanelIconSize));
      currentNowPlayingSong = songDto;

      musicPaused = false;
      playStatus.setIcon(
          imageLoader.loadClasspathImage("music_playing.gif", LayoutTheme.get().topPanelIconSize,
              LayoutTheme.get().topPanelIconSize, Image.SCALE_DEFAULT));

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
    currentNowPlayingSong = null;

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
    overlayTransitionInProgress = true;
    overlayCardLayout.show(overlayRoot, CARD_TABS);
    overlayTransitionInProgress = false;
    requestFocusInWindow();
  }

  /**
   * Returns the name of the overlay card currently visible in {@code overlayRoot}, falling back to
   * {@code CARD_TABS} if none is marked visible (e.g. before the first layout pass).
   */
  private String currentVisibleOverlayCard() {
    for (java.awt.Component c : overlayRoot.getComponents()) {
      if (c.isVisible()) {
        String name = c.getName();
        return name != null ? name : CARD_TABS;
      }
    }
    return CARD_TABS;
  }

  /**
   * Shows the named overlay card. Used to return to a specific overlay (e.g. the now-playing album
   * detail) after a nested overlay such as {@code CARD_ADD_SONG} is dismissed.
   */
  private void showOverlayCard(String cardName) {
    overlayTransitionInProgress = true;
    overlayCardLayout.show(overlayRoot, cardName);
    overlayTransitionInProgress = false;
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
  /**
   * Shows the "Add Song to Queue" overlay for the given song. Available from Home, Search, Hot
   * Here, and Genres.
   *
   * <p>
   * Before displaying the card the service is consulted via
   * {@link SongQueueService#isSongEligibleForQueue}. If the song is currently ineligible the card
   * is still shown, but it opens on its built-in "Song Constraint" panel rather than the normal
   * play-selection panel.
   */
  public void showAddSongToQueueCard(SongDto song) {

    if (addSongToQueueCard != null) {
      addSongToQueueCard.teardown();
    }

    // ── Capture caller so dismiss returns here, not always to TABS ────────
    // If the song was opened from a nested overlay (e.g. the now-playing album
    // detail card) we want to go back to that overlay when the user closes the
    // add-song card, not all the way back to the tab content area.
    final String callerCard = currentVisibleOverlayCard();
    Runnable onDismiss =
        CARD_TABS.equals(callerCard) ? this::hideOverlay : () -> showOverlayCard(callerCard);

    // ── Eligibility check (normal play = priority 1) ──────────────────────
    // getHighestPriority() returns the next available priority; normal plays
    // always use priority 1, so we pass 1 here as a representative value.
    String reason = songQueueService.isSongEligibleForQueue(song.getAlbumId(), song.getSongId(), 1);

    addSongToQueueCard = new AddSongToQueueCard(song, imageLoader, priorityCostMultiplier,
        songQueueService, creditManager, onDismiss);

    addSongToQueueCard.setOpaque(false);

    // ── Show constraint panel immediately when the song is ineligible ─────
    if (reason != null) {
      addSongToQueueCard.showConstraintPanel(reason);
    }

    replaceOverlayCard(CARD_ADD_SONG, addSongToQueueCard);
    overlayTransitionInProgress = true;
    overlayCardLayout.show(overlayRoot, CARD_ADD_SONG);
    overlayTransitionInProgress = false;
    addSongToQueueCard.onShown();
  }

  /**
   * Shows the Song Queue overlay. Used by the Now Playing panel. Disabled while the Admin tab is
   * active, since AdminPanel already has song-queue functionality.
   */
  public void showSongQueueCard() {

    if ((adminPanel != null && contentPanelTabs.getSelectedComponent() == adminPanel)
        || contentPanelTabs.getSelectedComponent() == queuePanel) {
      return; // disabled on Admin and Queue tabs (Queue tab has its own embedded view)
    }

    if (songQueueCard == null) {
      songQueueCard = new SongQueueCard(songPlayerService, currentQueue, songQueueService,
          creditManager, imageLoader, POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2,
          POPULARITY_THRESHOLD_3, this::hideOverlay);
      replaceOverlayCard(CARD_SONG_QUEUE, songQueueCard);
    }

    songQueueCard.setOpaque(false);

    songQueueCard.setQueue(currentQueue);
    overlayTransitionInProgress = true;
    overlayCardLayout.show(overlayRoot, CARD_SONG_QUEUE);
    overlayTransitionInProgress = false;
    songQueueCard.onShown();
  }

  /**
   * Shows the album detail card (track listing) for the currently-playing song's album. Triggered
   * when the user clicks the Now Playing panel in the top-right corner.
   *
   * <p>
   * The tab index that was active at the moment of the click is captured so that dismissing or
   * timing out the card restores the exact tab the user was on, rather than jumping to a fixed tab.
   */
  private void showNowPlayingAlbumCard() {

    if (currentNowPlayingSong == null)
      return;

    // Fetch the full album (with track listing) for the now-playing song
    AlbumDto full;
    try {
      full = songLibraryService.getAlbumById(currentNowPlayingSong.getAlbumId());
    } catch (Exception e) {
      return; // cannot show detail without album data
    }

    // Capture the tab index to restore when the card is dismissed or times out
    final int returnTabIndex = contentPanelTabs.getSelectedIndex();

    // Stop any previously active now-playing album card timer before replacing it
    if (nowPlayingAlbumCard != null) {
      nowPlayingAlbumCard.dismiss();
    }

    // TabNavigator whose popToRoot restores the previously-active tab
    TabNavigator navigator = new TabNavigator() {
      @Override
      public void popToRoot() {
        if (nowPlayingAlbumCard != null) {
          nowPlayingAlbumCard.dismiss();
          nowPlayingAlbumCard = null;
        }
        hideOverlay();
        overlayTransitionInProgress = true;
        contentPanelTabs.setSelectedIndex(returnTabIndex);
        lastSelectedTabIndex = returnTabIndex;
        overlayTransitionInProgress = false;
      }

      @Override
      public void pushAlbumDetail(AlbumDto album) {
        // Not reachable from this card — songs within the album open the
        // AddSongToQueue card directly via JukeANatorFrame#showAddSongToQueueCard.
      }
    };

    nowPlayingAlbumCard = new AlbumDetailCard(JukeANatorFrame.this, full, imageLoader,
        songQueueService, priorityCostMultiplier, POPULARITY_THRESHOLD_1, POPULARITY_THRESHOLD_2,
        POPULARITY_THRESHOLD_3, navigator, creditManager, incrementCreditsKey);
    nowPlayingAlbumCard.setOpaque(false);

    replaceOverlayCard(CARD_NOW_PLAYING_ALBUM, nowPlayingAlbumCard);
    overlayTransitionInProgress = true;
    overlayCardLayout.show(overlayRoot, CARD_NOW_PLAYING_ALBUM);
    overlayTransitionInProgress = false;
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

    overlayTransitionInProgress = true;
    overlayCardLayout.show(overlayRoot, CARD_EDIT_ALBUM);
    overlayTransitionInProgress = false;
  }

  /**
   * Shows the Admin Login overlay when the user clicks the Credits panel.
   *
   * <p>
   * On successful authentication, the Admin panel is selected directly — the Admin tab remains
   * invisible at all times so it cannot be reached without logging in. On Cancel or timeout the
   * overlay is dismissed and the previously active tab / state is preserved.
   */
  public void showLoginToAdminPanelCard() {

    // Kick off the album fetch in the background immediately — by the time the
    // admin successfully logs in and AdminPanel is constructed, the data will
    // likely already be available, eliminating the first-show delay.
    if (adminPanel == null && adminAlbumPrefetch == null) {
      adminAlbumPrefetch =
          java.util.concurrent.CompletableFuture.supplyAsync(songLibraryService::getAlbums);
    }

    if (loginToAdminPanelCard != null) {
      loginToAdminPanelCard.dismiss();
    }

    loginToAdminPanelCard = new LoginToAdminPanelCard(songLibraryService,
        /* onSuccess */ () -> SwingUtilities.invokeLater(() -> {
          hideOverlay();

          // ── Lazy initialisation ───────────────────────────────────────────
          // The album list was pre-fetched in the background as soon as the
          // login overlay appeared, so AdminPanel construction is now cheap.
          if (adminPanel == null) {
            adminPanel = buildAdminPanel();
            adminAlbumPrefetch = null; // consumed — allow a fresh fetch next time if needed
            // Swap the lightweight placeholder out for the real panel.
            contentPanelTabs.setComponentAt(6, adminPanel);
            // Push any queue updates that arrived before the panel existed.
            adminPanel.setQueue(currentQueue);
          }

          // Capture the screen they were looking at before the login overlay took over
          preAdminTabIndex = contentPanelTabs.getSelectedIndex();

          // Switch to the Admin panel without making the tab visible or enabled —
          // the tab header stays permanently hidden so it cannot be clicked directly.
          contentPanelTabs.setSelectedIndex(6);
          lastSelectedTabIndex = 6;
        }), /* onDismiss */ this::hideOverlay);

    loginToAdminPanelCard.setOpaque(false);

    replaceOverlayCard(CARD_LOGIN, loginToAdminPanelCard);
    overlayTransitionInProgress = true;
    overlayCardLayout.show(overlayRoot, CARD_LOGIN);
    overlayTransitionInProgress = false;
    loginToAdminPanelCard.onShown();
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
            imageLoader.loadClasspathImage("music_paused.png", LayoutTheme.get().topPanelIconSize,
                LayoutTheme.get().topPanelIconSize, Image.SCALE_DEFAULT));
      } else {
        playStatus.setIcon(
            imageLoader.loadClasspathImage("music_playing.gif", LayoutTheme.get().topPanelIconSize,
                LayoutTheme.get().topPanelIconSize, Image.SCALE_DEFAULT));
      }
    });
  }
}
