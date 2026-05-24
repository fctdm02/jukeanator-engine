package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
//import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.net.URL;
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
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
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
  private boolean enableBigScrollBars;
  
  // COLORS
  private static final Color BG_DARK = new Color(10, 10, 10);
  private static final Color BG_PANEL = new Color(22, 22, 28);
  private static final Color BG_SEARCH = new Color(32, 32, 40);
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);

  // SEARCH TAB
  private static final int KEYBOARD_HEIGHT = 260;
  private static final int SEARCH_BAR_HEIGHT = 90;  
  private final boolean enableTypeAheadSearch;
  private final CardLayout searchCardLayout = new CardLayout();
  private final JPanel searchRootPanel = new JPanel(searchCardLayout);
  private JLabel entrySearchLabel;
  private JLabel resultsSearchLabel;
  private final StringBuilder searchBuffer = new StringBuilder();

  // Results state
  private SearchResultDto lastSearchResult = null;
  private int artistsOffset = 0;
  private int albumsOffset = 0;
  private int songsOffset = 0;
  private static final int SEARCH_PREVIEW_COUNT = 6;

  // Results panels (rebuilt on each search)
  private final JPanel searchResultsPanel = new JPanel(new BorderLayout());

  
  // HOT HERE TAB
  private static final int HOT_HERE_PREVIEW_COUNT = 10;
  private int hotHereArtistsOffset = 0;
  private int hotHereAlbumsOffset = 0;
  private int hotHereSongsOffset = 0;
  private final JPanel hotHereContentPanel = new JPanel(new BorderLayout());
  private SearchResultDto hotHereResults;

  // GENRE TAB
  private static final int GENRES_PER_PAGE = 12;
  private final JPanel genresRootPanel = new JPanel(new CardLayout());
  private final JPanel genresGridPanel = new JPanel(new GridLayout(2, 6, 20, 20));
  private final JPanel genresPaginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
  private final CardLayout genresCardLayout = new CardLayout();
  private final JPanel genresContentPanel = new JPanel(genresCardLayout);
  private final JPanel genreDetailsPanel = new JPanel(new BorderLayout());
  private int currentGenresPage = 0;
  private final Map<String, ImageIcon> genreIconCache = new HashMap<>();
  private final DefaultListModel<GenreDto> genresListModel = new DefaultListModel<>();

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
  public JukeANatorFrame(
      JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties,
      SongLibraryService songLibraryService,
      SongQueueService songQueueService,
      SongPlayerService songPlayerService) {

    this.jukeANatorUserInterfaceProperties = jukeANatorUserInterfaceProperties;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;
    
    this.enableBigScrollBars = this.jukeANatorUserInterfaceProperties.getEnableBigScrollBars();
    
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
  // TABS PANEL
  // ============================================================
  private JTabbedPane buildContentPanelTabs() {

    JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
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
        if (isSelected) {
          g.setColor(new Color(70, 70, 70));
          g.fillRect(x, y, w, h);
        } else {
          g.setColor(Color.BLACK);
          g.fillRect(x, y, w, h);
        }
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
        // DO NOTHING
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

    tabs.setBackground(Color.BLACK);
    tabs.setForeground(Color.WHITE);
    tabs.setBorder(null);
    tabs.setOpaque(true);

    tabs.addTab("HOME", buildPlaceholderPanel());
    tabs.addTab("SEARCH", buildSearchPanel());
    tabs.addTab("HOT HERE", buildHotHerePanel());
    tabs.addTab("GENRES", buildGenresPanel());
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
  // SEARCH PANEL
  // ============================================================
  private JPanel buildSearchPanel() {

    searchRootPanel.setBackground(BG_DARK);

    // CARD 1: ENTRY (hero + search bar + keyboard)
    JPanel entryCard = buildSearchEntryCard();

    // CARD 2: RESULTS (search bar + keyboard + 3-column results)
    searchResultsPanel.setBackground(BG_DARK);

    searchRootPanel.add(entryCard,          "ENTRY");
    searchRootPanel.add(searchResultsPanel, "RESULTS");

    searchCardLayout.show(searchRootPanel, "ENTRY");

    return searchRootPanel;
  }

  // SEARCH ENTRY CARD
  private JPanel buildSearchEntryCard() {

    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(BG_DARK);

    JPanel heroPanel = new JPanel(new GridBagLayout());
    heroPanel.setBackground(new Color(25, 25, 35));
    heroPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_BLUE));
    heroPanel.setPreferredSize(new Dimension(100, 300));

    JLabel heroLabel = new JLabel("Search for your favorite music.");
    heroLabel.setForeground(TEXT_PRIMARY);
    heroLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
    heroPanel.add(heroLabel);

    JPanel searchBarPanel = buildSearchBarPanel(false);
    searchBarPanel.setPreferredSize(new Dimension(100, SEARCH_BAR_HEIGHT));

    JPanel keyboardPanel = buildFixedKeyboardPanel();

    root.add(searchBarPanel, BorderLayout.NORTH);
    root.add(heroPanel,      BorderLayout.CENTER);
    root.add(keyboardPanel,  BorderLayout.SOUTH);

    return root;
  }

  // SEARCH BAR PANEL
  private JPanel buildSearchBarPanel(boolean forResults) {

    JPanel bar = new JPanel(new BorderLayout(10, 0));
    bar.setBackground(new Color(20, 20, 30));
    bar.setBorder(new EmptyBorder(12, 20, 12, 20));

    JLabel entryLabel = new JLabel();
    entryLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
    entryLabel.setForeground(Color.WHITE);
    entryLabel.setOpaque(true);
    entryLabel.setBackground(new Color(40, 40, 55));
    entryLabel.setBorder(new EmptyBorder(8, 16, 8, 16));
    entryLabel.setHorizontalAlignment(SwingConstants.CENTER);
    entryLabel.setText(searchBuffer.length() == 0 ? " " : searchBuffer.toString());

    if (forResults) {
      resultsSearchLabel = entryLabel;
    } else {
      entrySearchLabel = entryLabel;
    }

    JButton searchButton = null;
    if (!enableTypeAheadSearch) {
      searchButton = new JButton("SEARCH");
      searchButton.setPreferredSize(new Dimension(180, 60));
      searchButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
      searchButton.setForeground(Color.BLACK);
      searchButton.setBackground(ACCENT_BLUE);
      searchButton.setFocusPainted(false);
      searchButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
      searchButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      searchButton.addActionListener(e -> executeSearch());
    }

    bar.add(entryLabel, BorderLayout.CENTER);

    if (!enableTypeAheadSearch && searchButton != null) {
      JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
      rightPanel.setOpaque(false);
      rightPanel.add(searchButton);
      bar.add(rightPanel, BorderLayout.EAST);
    }

    return bar;
  }

  // KEYBOARD PANEL
  private JPanel buildFixedKeyboardPanel() {

    JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.setBackground(BG_SEARCH);

    JPanel keyboard = buildKeyboardPanel();
    wrapper.add(keyboard);

    wrapper.setPreferredSize(new Dimension(100, KEYBOARD_HEIGHT));
    wrapper.setMinimumSize(new Dimension(100, KEYBOARD_HEIGHT));
    wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, KEYBOARD_HEIGHT));

    return wrapper;
  }

  private JPanel buildKeyboardPanel() {

    JPanel panel = new JPanel();
    panel.setOpaque(false);
    panel.setBorder(new EmptyBorder(20, 50, 20, 50));
    panel.setLayout(new GridLayout(3, 1, 10, 10));

    panel.add(buildKeyboardRow1());
    panel.add(buildKeyboardRow2());
    panel.add(buildKeyboardRow3());

    return panel;
  }

  private JPanel buildKeyboardRow1() {

    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);

    for (char c : "QWERTYUIOP".toCharArray()) {
      row.add(createKeyboardButton(String.valueOf(c)));
    }
    row.add(createKeyboardButton("'"));

    JButton clear = createKeyboardButton("CLEAR");
    clear.setPreferredSize(new Dimension(140, 60));
    clear.addActionListener(e -> resetSearchState());
    row.add(clear);

    JButton backspace = createKeyboardButton("⌫");
    backspace.setPreferredSize(new Dimension(100, 60));
    backspace.addActionListener(e -> {
      if (searchBuffer.length() > 0) {
        searchBuffer.deleteCharAt(searchBuffer.length() - 1);
        updateSearchEntryLabel();
        if (enableTypeAheadSearch) {
          executeSearch();
        }
      }
    });
    row.add(backspace);

    return row;
  }

  private JPanel buildKeyboardRow2() {

    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);

    for (char c : "ASDFGHJKL".toCharArray()) {
      row.add(createKeyboardButton(String.valueOf(c)));
    }

    JButton numeric = createKeyboardButton("123@");
    numeric.setPreferredSize(new Dimension(140, 60));
    row.add(numeric);

    JButton alpha = createKeyboardButton("ABC");
    alpha.setPreferredSize(new Dimension(140, 60));
    row.add(alpha);

    return row;
  }

  private JPanel buildKeyboardRow3() {

    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);

    for (char c : "ZXCVBNM".toCharArray()) {
      row.add(createKeyboardButton(String.valueOf(c)));
    }

    JButton space = createKeyboardButton("SPACE");
    space.setPreferredSize(new Dimension(420, 60));
    space.addActionListener(e -> {
      searchBuffer.append(' ');
      updateSearchEntryLabel();
      if (enableTypeAheadSearch) {
        executeSearch();
      }
    });
    row.add(space);

    return row;
  }

  private JButton createKeyboardButton(String text) {

    JButton button = new JButton(text);
    button.setPreferredSize(new Dimension(70, 60));
    styleKeyboardButton(button);

    if (text.length() == 1 && !text.equals(" ")) {
      button.addActionListener(e -> {
        searchBuffer.append(text);
        updateSearchEntryLabel();
        if (enableTypeAheadSearch) {
          executeSearch();
        }
      });
    }

    return button;
  }

  private void styleKeyboardButton(JButton button) {

    button.setFocusPainted(false);
    button.setBackground(new Color(70, 70, 80));
    button.setForeground(TEXT_PRIMARY);
    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    button.setBorder(BorderFactory.createLineBorder(ACCENT_BLUE, 1));
  }

  private void updateSearchEntryLabel() {

    String display = searchBuffer.toString().isEmpty() ? " " : searchBuffer.toString();
    if (entrySearchLabel   != null) entrySearchLabel.setText(display);
    if (resultsSearchLabel != null) resultsSearchLabel.setText(display);
  }

  // RESET SEARCH STATE
  private void resetSearchState() {

    if (entrySearchLabel   != null) entrySearchLabel.setText(" ");
    if (resultsSearchLabel != null) resultsSearchLabel.setText(" ");

    searchBuffer.setLength(0);
    updateSearchEntryLabel();

    lastSearchResult = null;
    artistsOffset    = 0;
    albumsOffset     = 0;
    songsOffset      = 0;

    searchResultsPanel.removeAll();
    searchResultsPanel.revalidate();
    searchResultsPanel.repaint();

    searchCardLayout.show(searchRootPanel, "ENTRY");
  }

  // EXECUTE SEARCH
  private void executeSearch() {

    String query = searchBuffer.toString().trim();
    if (query.isEmpty()) return;

    try {
      lastSearchResult = songLibraryService.getMusicBySearch(query);
      artistsOffset    = 0;
      albumsOffset     = 0;
      songsOffset      = 0;

      rebuildSearchResultsPanel();
      searchCardLayout.show(searchRootPanel, "RESULTS");

    } catch (Exception ex) {
      // TODO: show error state
    }
  }

  // RESULTS PANEL
  private void rebuildSearchResultsPanel() {

    searchResultsPanel.removeAll();

    JPanel searchBarPanel = buildSearchBarPanel(true);

    JPanel columnsPanel = new JPanel(new GridLayout(1, 3, 2, 0));
    columnsPanel.setBackground(Color.BLACK);
    columnsPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

    columnsPanel.add(buildSearchResultColumn("ARTISTS",
        lastSearchResult.getArtists() == null ? List.of() : lastSearchResult.getArtists(),
        artistsOffset));

    columnsPanel.add(buildSearchResultColumn("ALBUMS",
        lastSearchResult.getAlbums() == null ? List.of() : lastSearchResult.getAlbums(),
        albumsOffset));

    columnsPanel.add(buildSearchResultColumn("SONGS",
        lastSearchResult.getSongs() == null ? List.of() : lastSearchResult.getSongs(),
        songsOffset));

    JPanel keyboardWrapper = buildFixedKeyboardPanel();

    searchResultsPanel.add(searchBarPanel,   BorderLayout.NORTH);
    searchResultsPanel.add(columnsPanel,     BorderLayout.CENTER);
    searchResultsPanel.add(keyboardWrapper,  BorderLayout.SOUTH);

    searchResultsPanel.revalidate();
    searchResultsPanel.repaint();
  }

  // SEARCH RESULT COLUMN — up/down nav only, no VIEW ALL
  private <T> JPanel buildSearchResultColumn(String header, List<T> items, int offset) {

    JPanel column = new JPanel(new BorderLayout());
    column.setBackground(new Color(15, 15, 20));
    column.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(60, 60, 80)));

    //
    // HEADER
    //
    int total = items.size();
    JLabel headerLabel = new JLabel(header + " (" + total + ")", SwingConstants.CENTER);
    headerLabel.setForeground(ACCENT_BLUE);
    headerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    headerLabel.setBorder(new EmptyBorder(10, 0, 8, 0));
    headerLabel.setOpaque(true);
    headerLabel.setBackground(new Color(20, 20, 30));

    //
    // ROWS
    //
    JPanel rowsPanel = new JPanel();
    rowsPanel.setBackground(new Color(15, 15, 20));
    rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));

    for (int slot = 0; slot < SEARCH_PREVIEW_COUNT; slot++) {

      int itemIndex = offset + slot;
      JPanel row = (itemIndex < total)
          ? buildSearchResultRow(itemIndex + 1, items.get(itemIndex), header)
          : buildEmptySearchResultRow();

      rowsPanel.add(row);

      if (slot < SEARCH_PREVIEW_COUNT - 1) {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(50, 50, 65));
        sep.setBackground(new Color(50, 50, 65));
        rowsPanel.add(sep);
      }
    }

    //
    // NAVIGATION — up / down only
    //
    JPanel navPanel = new JPanel(new BorderLayout(4, 0));
    navPanel.setBackground(new Color(20, 20, 30));
    navPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

    JButton upButton = new JButton("∧");
    styleNavButton(upButton);
    upButton.setEnabled(offset > 0);
    upButton.addActionListener(e -> {
      adjustOffset(header, -1);
      rebuildSearchResultsPanel();
    });

    JButton downButton = new JButton("∨");
    styleNavButton(downButton);
    downButton.setEnabled(offset + SEARCH_PREVIEW_COUNT < total);
    downButton.addActionListener(e -> {
      adjustOffset(header, 1);
      rebuildSearchResultsPanel();
    });

    navPanel.add(upButton,   BorderLayout.WEST);
    navPanel.add(downButton, BorderLayout.EAST);

    column.add(headerLabel, BorderLayout.NORTH);
    column.add(rowsPanel,   BorderLayout.CENTER);
    column.add(navPanel,    BorderLayout.SOUTH);

    return column;
  }

  private JPanel buildEmptySearchResultRow() {

    JPanel row = new JPanel(new BorderLayout());
    row.setBackground(new Color(15, 15, 20));
    row.setBorder(new EmptyBorder(8, 10, 8, 10));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

    JLabel numLabel = new JLabel("");
    numLabel.setPreferredSize(new Dimension(36, 56));

    JLabel thumb = new JLabel();
    thumb.setPreferredSize(new Dimension(56, 56));

    JPanel textPanel = new JPanel();
    textPanel.setOpaque(false);

    row.add(numLabel,  BorderLayout.WEST);
    row.add(thumb,     BorderLayout.CENTER);
    row.add(textPanel, BorderLayout.CENTER);

    row.setOpaque(true);
    return row;
  }

  private void adjustOffset(String category, int delta) {

    switch (category) {
      case "ARTISTS" -> artistsOffset = Math.max(0, artistsOffset + delta);
      case "ALBUMS"  -> albumsOffset  = Math.max(0, albumsOffset  + delta);
      case "SONGS"   -> songsOffset   = Math.max(0, songsOffset   + delta);
    }
  }

  private void styleNavButton(JButton b) {

    b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
    b.setForeground(Color.WHITE);
    b.setBackground(new Color(50, 50, 70));
    b.setFocusPainted(false);
    b.setBorderPainted(false);
    b.setPreferredSize(new Dimension(60, 40));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  // SEARCH RESULT ROW
  private <T> JPanel buildSearchResultRow(int rowNum, T item, String category) {

    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setBackground(new Color(15, 15, 20));
    row.setBorder(new EmptyBorder(8, 10, 8, 10));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
    row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    // ROW NUMBER
    JLabel numLabel = new JLabel(String.format("%02d", rowNum));
    numLabel.setForeground(TEXT_SECONDARY);
    numLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
    numLabel.setPreferredSize(new Dimension(36, 56));
    numLabel.setHorizontalAlignment(SwingConstants.CENTER);

    // THUMBNAIL
    JLabel thumb = new JLabel();
    thumb.setPreferredSize(new Dimension(56, 56));
    thumb.setHorizontalAlignment(SwingConstants.CENTER);
    thumb.setOpaque(true);
    thumb.setBackground(new Color(40, 40, 55));

    // TEXT
    JPanel textPanel = new JPanel();
    textPanel.setOpaque(false);
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

    JLabel line1 = new JLabel();
    JLabel line2 = new JLabel();
    line1.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
    line1.setForeground(Color.WHITE);
    line2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    line2.setForeground(TEXT_SECONDARY);

    String coverPath = null;

    if (category.equals("ARTISTS") && item instanceof ArtistDto artist) {
      
      line1.setText(artist.getArtistName());
      line2.setText(artist.getSongCount() + " songs, " + artist.getAlbumCount() + " albums");
      coverPath = artist.getCoverArtPath();
      
    } else if (category.equals("ALBUMS") && item instanceof AlbumDto album) {
      
      line1.setText(album.getAlbumName());
      line2.setText(album.getArtistName());      
      coverPath = album.getCoverArtPath();
      
    } else if (category.equals("SONGS") && item instanceof SongDto song) {
      line1.setText(song.getSongName());
      line2.setText(song.getArtistName());
      coverPath = song.getCoverArtPath();
      
    }

    // LOAD THUMBNAIL
    final String finalCoverPath = coverPath;
    if (finalCoverPath != null) {
      try {
        thumb.setIcon(imageLoader.loadFilesystemImage(finalCoverPath, 56, 56));
      } catch (Exception ignored) {
      }
    }

    textPanel.add(line1);
    textPanel.add(Box.createVerticalStrut(3));
    textPanel.add(line2);

    // ASSEMBLE: [numLabel + thumb] | textPanel
    JPanel leftSection = new JPanel(new BorderLayout(8, 0));
    leftSection.setOpaque(false);
    leftSection.add(numLabel, BorderLayout.WEST);
    leftSection.add(thumb, BorderLayout.CENTER);

    row.add(leftSection, BorderLayout.WEST);
    row.add(textPanel, BorderLayout.CENTER);

    // HOVER EFFECT
    row.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        row.setBackground(new Color(30, 30, 45));
        repaintChildren(row);
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        row.setBackground(new Color(15, 15, 20));
        repaintChildren(row);
      }
    });
    
    if (category.equals("SONGS") && item instanceof SongDto song) {

      row.addMouseListener(new java.awt.event.MouseAdapter() {

        @Override
        public void mouseClicked(java.awt.event.MouseEvent e) {

          // Resolve the top-level Frame (JukeANatorFrame extends JFrame extends Frame)
          Frame owner = (Frame) SwingUtilities.getWindowAncestor(row);

          AddSongToQueueDialog.show(
              owner,
              song,
              imageLoader,
              creditsPer,           // normal play cost  — already an instance field
              creditsPer * 2,       // priority cost placeholder until defined              
              // Normal play — priority 0
              () -> songQueueService.addSongToQueue(
                  new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 0)),
              // Priority play — priority 1 (value TBD)
              () -> songQueueService.addSongToQueue(
                  new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 1)));          
        }
      });
    } else if (category.equals("ALBUMS") && item instanceof AlbumDto album) {

      row.addMouseListener(new java.awt.event.MouseAdapter() {

        @Override
        public void mouseClicked(java.awt.event.MouseEvent e) {

          Frame owner = (Frame) SwingUtilities.getWindowAncestor(row);

          // Fetch the full album (with track list) from the service.
          // The AlbumDto in search results may be a summary; the dialog
          // needs the songs list populated.
          AlbumDto fullAlbum = songLibraryService.getAlbumById(album.getAlbumId());

          AlbumDetailDialog.show(
              owner,
              fullAlbum,
              imageLoader,
              songQueueService,
              creditsPer,
              creditsPer * 2,               // priority cost placeholder
              POPULARITY_THRESHOLD_1,
              POPULARITY_THRESHOLD_2,
              POPULARITY_THRESHOLD_3,
              enableBigScrollBars);
        }
      });
    }    

    return row;
  }
  
  private void repaintChildren(java.awt.Container c) {
    for (java.awt.Component child : c.getComponents()) {
      child.repaint();
    }
  }  

  
  
  
  
  

  
  
  
  // ============================================================
  // HOT HERE PANEL
  // ============================================================
  private JPanel buildHotHerePanel() {

    hotHereContentPanel.setBackground(BG_DARK);

    // Load data once
    try {
      hotHereResults = songLibraryService.getMusicByPopularity();
    } catch (Exception e) {
      hotHereResults = new SearchResultDto();
    }

    rebuildHotHereColumnsPanel();

    return hotHereContentPanel;
  }

  // REBUILD HOT HERE COLUMNS
  private void rebuildHotHereColumnsPanel() {

    hotHereContentPanel.removeAll();

    List<?> artists = hotHereResults.getArtists() != null ? hotHereResults.getArtists() : List.of();
    List<?> albums = hotHereResults.getAlbums() != null ? hotHereResults.getAlbums() : List.of();
    List<?> songs = hotHereResults.getSongs() != null ? hotHereResults.getSongs() : List.of();

    JPanel columnsPanel = new JPanel(new GridLayout(1, 3, 2, 0));
    columnsPanel.setBackground(Color.BLACK);
    columnsPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

    columnsPanel.add(buildHotHereColumn("ARTISTS", artists, hotHereArtistsOffset));
    columnsPanel.add(buildHotHereColumn("ALBUMS", albums, hotHereAlbumsOffset));
    columnsPanel.add(buildHotHereColumn("SONGS", songs, hotHereSongsOffset));

    hotHereContentPanel.add(columnsPanel, BorderLayout.CENTER);

    hotHereContentPanel.revalidate();
    hotHereContentPanel.repaint();
  }

  // HOT HERE COLUMN (mirrors buildSearchResultColumn, but no VIEW ALL button,
  // uses HOT_HERE_PREVIEW_COUNT rows instead of SEARCH_PREVIEW_COUNT)
  private <T> JPanel buildHotHereColumn(String header, List<T> items, int offset) {

    JPanel column = new JPanel(new BorderLayout());
    column.setBackground(new Color(15, 15, 20));
    column.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(60, 60, 80)));

    //
    // HEADER
    //
    int total = items.size();
    JLabel headerLabel = new JLabel(header + " (" + total + ")", SwingConstants.CENTER);
    headerLabel.setForeground(ACCENT_BLUE);
    headerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    headerLabel.setBorder(new EmptyBorder(10, 0, 8, 0));
    headerLabel.setOpaque(true);
    headerLabel.setBackground(new Color(20, 20, 30));

    //
    // ROWS (up to HOT_HERE_PREVIEW_COUNT)
    //
    JPanel rowsPanel = new JPanel();
    rowsPanel.setBackground(new Color(15, 15, 20));
    rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));

    for (int slot = 0; slot < HOT_HERE_PREVIEW_COUNT; slot++) {

      int itemIndex = offset + slot;
      JPanel row =
          (itemIndex < total) ? buildSearchResultRow(itemIndex + 1, items.get(itemIndex), header)
              : buildEmptySearchResultRow();

      rowsPanel.add(row);

      if (slot < HOT_HERE_PREVIEW_COUNT - 1) {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(50, 50, 65));
        sep.setBackground(new Color(50, 50, 65));
        rowsPanel.add(sep);
      }
    }

    //
    // NAVIGATION (up / down only — no VIEW ALL)
    //
    JPanel navPanel = new JPanel(new BorderLayout(4, 0));
    navPanel.setBackground(new Color(20, 20, 30));
    navPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

    JButton upButton = new JButton("∧");
    styleNavButton(upButton);
    upButton.setEnabled(offset > 0);
    upButton.addActionListener(e -> {
      adjustHotHereOffset(header, -1);
      rebuildHotHereColumnsPanel();
    });

    JButton downButton = new JButton("∨");
    styleNavButton(downButton);
    downButton.setEnabled(offset + HOT_HERE_PREVIEW_COUNT < total);
    downButton.addActionListener(e -> {
      adjustHotHereOffset(header, 1);
      rebuildHotHereColumnsPanel();
    });

    navPanel.add(upButton, BorderLayout.WEST);
    navPanel.add(downButton, BorderLayout.EAST);

    column.add(headerLabel, BorderLayout.NORTH);
    column.add(rowsPanel, BorderLayout.CENTER);
    column.add(navPanel, BorderLayout.SOUTH);

    return column;
  }

  // Adjust the correct per-column offset
  private void adjustHotHereOffset(String category, int delta) {

    switch (category) {
      case "ARTISTS" -> hotHereArtistsOffset = Math.max(0, hotHereArtistsOffset + delta);
      case "ALBUMS" -> hotHereAlbumsOffset = Math.max(0, hotHereAlbumsOffset + delta);
      case "SONGS" -> hotHereSongsOffset = Math.max(0, hotHereSongsOffset + delta);
    }
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

    //
    // NAVIGATION BUTTON PANEL
    //
    JPanel navigationPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    navigationPanel.setOpaque(false);

    JPanel bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.setOpaque(false);
    bottomPanel.add(genresPaginationPanel, BorderLayout.CENTER);
    bottomPanel.add(navigationPanel, BorderLayout.EAST);   
    
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

    refreshGenresUI();

    return genresRootPanel;
  }

  // REFRESH GENRES PAGE
  private void refreshGenresPage() {

    genresGridPanel.removeAll();

    int start = currentGenresPage * GENRES_PER_PAGE;
    int end = Math.min(
        start + GENRES_PER_PAGE,
        genresListModel.size());

    for (int i = start; i < end; i++) {

      GenreDto genre = genresListModel.get(i);

      genresGridPanel.add(buildGenreTile(genre));
    }

    genresGridPanel.revalidate();
    genresGridPanel.repaint();
  }

  // GENRE TILE
  private JPanel buildGenreTile(GenreDto genreDto) {

    JPanel panel = new JPanel(new BorderLayout());
    Color parentBackground = genresGridPanel.getBackground();
    panel.setBackground(parentBackground);
    panel.setOpaque(true);
    panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    
    JLabel imageLabel = new JLabel();
    imageLabel.setOpaque(false);
    imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

    String genreName = genreDto.getGenreName();

    try {

      ImageIcon cached = genreIconCache.get(genreName);

      if (cached != null) {

        imageLabel.setIcon(cached);

      } else {

        String resourceName = genreName + ".png";
        URL resource = getClass().getResource(resourceName);
        if (resource != null) {

          ImageIcon imageIcon = imageLoader.loadImage(resourceName, 240, 240);
          genreIconCache.put(genreName, imageIcon);
          imageLabel.setIcon(imageIcon);

        } else {

          imageLabel.setText(genreName);
        }
      }

    } catch (Exception e) {

      imageLabel.setText(genreName);
    }

    JLabel textLabel = new JLabel(
        genreName.toUpperCase(),
        SwingConstants.CENTER);

    textLabel.setForeground(Color.WHITE);
    textLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
    textLabel.setBorder(new EmptyBorder(10, 0, 10, 0));
    textLabel.setOpaque(false);
    
    panel.add(imageLabel, BorderLayout.CENTER);
    panel.add(textLabel, BorderLayout.SOUTH);
    panel.addMouseListener(new java.awt.event.MouseAdapter() {

      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {

        showGenreDetails(genreDto);
      }
    });

    return panel;
  }
  
  // GENRE DETAILS
  private void showGenreDetails(GenreDto genreDto) {

    genreDetailsPanel.removeAll();

    JPanel detailsPanel = new JPanel(new GridBagLayout());

    detailsPanel.setBackground(BG_DARK);

    JLabel label = new JLabel(
        "Genre Details: " + genreDto.getGenreName());

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

  // QUEUE SONG DETAILS
  private void showQueueSongDetails(SongQueueEntryDto songQueueEntryDto) {
    
    SongDto song = songQueueEntryDto.getSong();

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
    panel.setBackground(BG_PANEL);
    panel.setBorder(new EmptyBorder(10, 20, 10, 20));

    //
    // LEFT : CREDITS
    //
    JPanel creditsPanel = new JPanel(new BorderLayout(10, 0));
    creditsPanel.setBackground(Color.BLACK);
    creditsPanel.setOpaque(true);
    creditsPanel.setBorder(null);
    creditsPanel.setPreferredSize(new Dimension(350, 100));

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
  
  // NOW PLAYING PANEL
  private JPanel buildNowPlayingPanel() {

    JPanel panel = new JPanel(new BorderLayout(10, 0));

    panel.setBackground(Color.BLACK);
    panel.setOpaque(true);
    panel.setBorder(null);

    //
    // LEFT : PLAY STATUS
    //
    playStatus.setPreferredSize(new Dimension(96, 96));
    playStatus.setHorizontalAlignment(SwingConstants.CENTER);
    playStatus.setBorder(null);

    //
    // CENTER : TEXT PANEL
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
    // RIGHT : COVER ART
    //
    albumArtLabel.setPreferredSize(new Dimension(96, 96));
    albumArtLabel.setHorizontalAlignment(SwingConstants.CENTER);
    albumArtLabel.setBorder(null);

    panel.add(playStatus, BorderLayout.WEST);
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

  
  
  
  
  
  
    
 
  
  // FULLSCREEN
  public void showFullscreen() {

    GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

    gd.setFullScreenWindow(this);
  }

  // GENRE LIST
  public void setGenres(List<GenreDto> genres) {

    SwingUtilities.invokeLater(() -> {

      genresListModel.clear();

      if (genres != null) {
        genres.forEach(genresListModel::addElement);
      }

      // clamp current page
      int maxPage = Math.max(
          0,
          (int) Math.ceil(
              genresListModel.size() / (double) GENRES_PER_PAGE) - 1);

      if (currentGenresPage > maxPage) {
        currentGenresPage = maxPage;
      }

      refreshGenresUI();
    });
  }
  
  private void refreshGenresUI() {
    rebuildGenresPagination();
    refreshGenresPage();
  }

  private void rebuildGenresPagination() {

    genresPaginationPanel.removeAll();
    genresPaginationPanel.setLayout(new BorderLayout());
    genresPaginationPanel.setOpaque(false);

    int pageCount = Math.max(1, (int) Math.ceil(genresListModel.size() / (double) GENRES_PER_PAGE));

    //
    // PREVIOUS BUTTON
    //
    JButton previousButton = new JButton("<");
    previousButton.setPreferredSize(new Dimension(120, 60));

    stylePageNavButton(previousButton);

    previousButton.addActionListener(e -> {

      if (currentGenresPage > 0) {

        currentGenresPage--;
        refreshGenresUI();
      }
    });

    //
    // NEXT BUTTON
    //
    JButton nextButton = new JButton(">");
    nextButton.setPreferredSize(new Dimension(120, 60));

    stylePageNavButton(nextButton);

    nextButton.addActionListener(e -> {

      if (currentGenresPage < pageCount - 1) {

        currentGenresPage++;
        refreshGenresUI();
      }
    });

    boolean hasPrevious = currentGenresPage > 0;
    boolean hasNext = currentGenresPage < pageCount - 1;

    previousButton.setVisible(hasPrevious);
    nextButton.setVisible(hasNext);
    
    //
    // DOT BUTTONS
    //
    JPanel dotsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    dotsPanel.setOpaque(false);

    for (int i = 0; i < pageCount; i++) {

      JButton dot = new JButton("●");

      dot.setForeground(i == currentGenresPage ? ACCENT_BLUE : Color.WHITE);
      dot.setBackground(BG_DARK);
      dot.setBorderPainted(false);
      dot.setFocusPainted(false);
      dot.setContentAreaFilled(false);

      dot.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));

      final int page = i;

      dot.addActionListener(e -> {

        currentGenresPage = page;
        refreshGenresUI();
      });

      dotsPanel.add(dot);
    }

    //
    // LEFT WRAPPER
    //
    JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    leftPanel.setOpaque(false);
    leftPanel.add(previousButton);
    leftPanel.setPreferredSize(new Dimension(140, 80));

    //
    // RIGHT WRAPPER
    //
    JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    rightPanel.setOpaque(false);
    rightPanel.add(nextButton);
    rightPanel.setPreferredSize(new Dimension(140, 80));

    //
    // ASSEMBLE
    //
    genresPaginationPanel.add(leftPanel, BorderLayout.WEST);
    genresPaginationPanel.add(dotsPanel, BorderLayout.CENTER);
    genresPaginationPanel.add(rightPanel, BorderLayout.EAST);

    genresPaginationPanel.revalidate();
    genresPaginationPanel.repaint();
  }

  private void stylePageNavButton(JButton b) {
    b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
    b.setForeground(Color.WHITE);
    b.setBackground(Color.BLACK);
    b.setFocusPainted(false);
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
      playStatus.setIcon(imageLoader.loadClasspathImage("music_playing.gif", 96, 96, Image.SCALE_DEFAULT));
    });
  }

  private void clearNowPlaying() {

    songLabel.setText("");
    artistLabel.setText("");
    albumLabel.setText("");
    albumArtLabel.setIcon(null);
    playStatus.setIcon(null);
    musicPaused = false;    
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
        playStatus.setIcon(imageLoader.loadClasspathImage("music_paused.png", 96, 96, Image.SCALE_DEFAULT));
      } else {
        playStatus.setIcon(imageLoader.loadClasspathImage("music_playing.gif", 96, 96, Image.SCALE_DEFAULT));
      }
    });
  }
}