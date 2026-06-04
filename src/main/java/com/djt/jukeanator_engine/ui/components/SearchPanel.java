package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

public class SearchPanel extends JPanel implements TabNavigator {

  private static final long serialVersionUID = 1L;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color TEXT_PRIMARY = Color.WHITE;

  // Key colours
  private static final Color KEY_TOP = new Color(88, 88, 96);
  private static final Color KEY_MID = new Color(62, 62, 70);
  private static final Color KEY_FACE = new Color(48, 48, 55);
  private static final Color KEY_FRONT = new Color(22, 22, 26);
  private static final Color KEY_SHADOW = new Color(12, 12, 14);

  // ── Layout constants ──────────────────────────────────────────────────────
  private static final int KEYBOARD_HEIGHT = 260;
  private static final int SEARCH_BAR_HEIGHT = 90;
  private static final int SEARCH_PREVIEW_COUNT = 6;

  // Unified Screen Margin Padding to expose base background gradient
  private static final int SCREEN_PADDING_HORIZONTAL = 60;
  // Must match the exact column edge margin defined in ResultsColumnPanel.java
  private static final int COLUMN_INTERNAL_EDGE_GAP = 10;

  // ── Card names ────────────────────────────────────────────────────────────
  private static final String CARD_ENTRY = "ENTRY";
  private static final String CARD_RESULTS = "RESULTS";
  private static final String CARD_ARTIST = "ARTIST";
  private static final String CARD_DETAIL = "DETAIL";

  private enum KeyboardMode {
    ABC, NUMERIC
  }

  private KeyboardMode keyboardMode = KeyboardMode.ABC;

  private final CardLayout cardLayout = new CardLayout();
  private final JPanel rootPanel = new JPanel(cardLayout);

  private final StringBuilder searchBuffer = new StringBuilder();
  private SearchResultDto lastResult;
  private int artistsOffset = 0;
  private int albumsOffset = 0;
  private int songsOffset = 0;

  private JLabel entrySearchLabel;
  private JLabel resultsSearchLabel;

  private final JPanel resultsCard = new JPanel(new BorderLayout());

  private JPanel entryKeyboardWrapper;
  private JPanel resultsKeyboardWrapper;

  private AlbumDetailCard currentDetailCard;

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final ImageLoader imageLoader;
  private final int normalPlayCost;
  private final int priorityCost;
  private final int popularityT1;
  private final int popularityT2;
  private final int popularityT3;
  private final boolean enableTypeAheadSearch;
  private final int gridCols;
  private final int gridRows;
  private final int artW;
  private final int artH;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────

  public SearchPanel(SongLibraryService songLibraryService, SongQueueService songQueueService,
      ImageLoader imageLoader, int normalPlayCost, int priorityCost, int popularityT1,
      int popularityT2, int popularityT3, boolean enableTypeAheadSearch, int gridCols, int gridRows,
      int artW, int artH) {

    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.imageLoader = imageLoader;
    this.normalPlayCost = normalPlayCost;
    this.priorityCost = priorityCost;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;
    this.enableTypeAheadSearch = enableTypeAheadSearch;
    this.gridCols = gridCols;
    this.gridRows = gridRows;
    this.artW = artW;
    this.artH = artH;

    setLayout(new BorderLayout());
    setOpaque(false);

    rootPanel.setOpaque(false);
    add(rootPanel, BorderLayout.CENTER);

    resultsCard.setOpaque(false);

    rootPanel.add(buildEntryCard(), CARD_ENTRY);
    rootPanel.add(resultsCard, CARD_RESULTS);
    rootPanel.add(placeholder(), CARD_ARTIST);
    rootPanel.add(placeholder(), CARD_DETAIL);

    cardLayout.show(rootPanel, CARD_ENTRY);
  }

  @Override
  public void pushAlbumDetail(AlbumDto album) {

    Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
    AlbumDto full = fetchFull(album);

    if (currentDetailCard != null)
      currentDetailCard.dismiss();

    currentDetailCard = new AlbumDetailCard(owner, full, imageLoader, songQueueService,
        normalPlayCost, priorityCost, popularityT1, popularityT2, popularityT3, this);

    replaceCard(CARD_DETAIL, currentDetailCard);
    cardLayout.show(rootPanel, CARD_DETAIL);
  }

  @Override
  public void popToRoot() {
    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
      currentDetailCard = null;
    }
    cardLayout.show(rootPanel, lastResult != null ? CARD_RESULTS : CARD_ENTRY);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // FIXED ITEM #3: HERO PANEL WITH ENHANCED LIGHTENING GLASS OVERLAY
  // ─────────────────────────────────────────────────────────────────────────

  private JPanel buildEntryCard() {
    JPanel root = new JPanel(new BorderLayout());
    root.setOpaque(false);

    // Outer alignment container shell ensuring margins allow background gradient to pass through
    JPanel heroWrapper = new JPanel(new BorderLayout());
    heroWrapper.setOpaque(false);
    heroWrapper
        .setBorder(new EmptyBorder(0, SCREEN_PADDING_HORIZONTAL, 0, SCREEN_PADDING_HORIZONTAL));

    // Enhanced inner panel incorporating the exact same frosted glass lighten execution as the
    // keyboard panel
    JPanel enhancedHeroBody = new JPanel(new GridBagLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Exact lightened glass alpha layer setup matching keyboard panel style
        g2.setColor(new Color(255, 255, 255, 30));
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.dispose();
        super.paintComponent(g);
      }
    };
    enhancedHeroBody.setOpaque(false);
    enhancedHeroBody.setPreferredSize(new Dimension(100, 300));

    JLabel heroLabel = new JLabel("Search for your favorite music.");
    heroLabel.setForeground(TEXT_PRIMARY);
    heroLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
    enhancedHeroBody.add(heroLabel);

    heroWrapper.add(enhancedHeroBody, BorderLayout.CENTER);

    JPanel searchBar = buildSearchBarPanel(false);
    searchBar.setPreferredSize(new Dimension(100, SEARCH_BAR_HEIGHT));

    entryKeyboardWrapper = buildKeyboardWrapper(true);

    root.add(searchBar, BorderLayout.NORTH);
    root.add(heroWrapper, BorderLayout.CENTER);
    root.add(entryKeyboardWrapper, BorderLayout.SOUTH);
    return root;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // FIXED ITEM #1 & #2: SEARCH BAR LAYOUT AND RE-ALIGNED BORDER
  // ─────────────────────────────────────────────────────────────────────────

  private JPanel buildSearchBarPanel(boolean forResults) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper
        .setBorder(new EmptyBorder(12, SCREEN_PADDING_HORIZONTAL, 12, SCREEN_PADDING_HORIZONTAL));

    JPanel bar = new JPanel(new BorderLayout(10, 0));
    bar.setBackground(Color.BLACK);
    // Asymmetrical clean white border setup: Top=2px, Left/Bottom/Right=1px
    bar.setBorder(BorderFactory.createMatteBorder(2, 1, 1, 1, Color.WHITE));

    JLabel lbl = new JLabel();
    lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
    lbl.setForeground(Color.WHITE);
    lbl.setOpaque(true);
    lbl.setBackground(Color.BLACK);
    lbl.setBorder(new EmptyBorder(8, 16, 8, 16));
    lbl.setHorizontalAlignment(SwingConstants.CENTER);
    lbl.setText(searchBuffer.length() == 0 ? " " : searchBuffer.toString());

    if (forResults)
      resultsSearchLabel = lbl;
    else
      entrySearchLabel = lbl;

    bar.add(lbl, BorderLayout.CENTER);

    if (!enableTypeAheadSearch) {
      JButton btn = new JButton("SEARCH");
      btn.setPreferredSize(new Dimension(180, 60));
      btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
      btn.setForeground(Color.BLACK);
      btn.setBackground(ACCENT_BLUE);
      btn.setFocusPainted(false);
      btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
      btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      btn.addActionListener(e -> executeSearch());

      JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
      right.setOpaque(false);
      right.add(btn);
      bar.add(right, BorderLayout.EAST);
    }

    wrapper.add(bar, BorderLayout.CENTER);
    return wrapper;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // KEYBOARD WRAPPER
  // ─────────────────────────────────────────────────────────────────────────

  private JPanel buildKeyboardWrapper(boolean isEntryCard) {
    JPanel outerWrapper = new JPanel(new BorderLayout());
    outerWrapper.setOpaque(false);
    outerWrapper.setPreferredSize(new Dimension(100, KEYBOARD_HEIGHT));
    outerWrapper.setMinimumSize(new Dimension(100, KEYBOARD_HEIGHT));
    outerWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, KEYBOARD_HEIGHT));
    outerWrapper
        .setBorder(new EmptyBorder(0, SCREEN_PADDING_HORIZONTAL, 0, SCREEN_PADDING_HORIZONTAL));

    JPanel frostedBodyPanel = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255, 30));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
      }
    };
    frostedBodyPanel.setOpaque(false);

    JPanel centreShell = new JPanel(new GridBagLayout());
    centreShell.setOpaque(false);
    centreShell.add(buildKeyboardPanel());

    frostedBodyPanel.add(centreShell, BorderLayout.CENTER);
    outerWrapper.add(frostedBodyPanel, BorderLayout.CENTER);

    return outerWrapper;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // KEYBOARD LAYOUT TRACKING
  // ─────────────────────────────────────────────────────────────────────────

  private JPanel buildKeyboardPanel() {
    JPanel p = new JPanel(new GridLayout(3, 1, 10, 10));
    p.setOpaque(false);
    p.setBorder(new EmptyBorder(20, 20, 20, 20));

    if (keyboardMode == KeyboardMode.ABC) {
      p.add(buildAbcRow1());
      p.add(buildAbcRow2());
      p.add(buildAbcRow3());
    } else {
      p.add(buildNumRow1());
      p.add(buildNumRow2());
      p.add(buildNumRow3());
    }
    return p;
  }

  private JPanel buildAbcRow1() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);
    for (char c : "QWERTYUIOP".toCharArray())
      row.add(letterKey(String.valueOf(c)));

    JButton clear = styledKey("CLEAR", new Dimension(140, 60));
    clear.addActionListener(e -> resetSearch());
    row.add(clear);

    row.add(buildBackspaceButton());
    return row;
  }

  private JPanel buildAbcRow2() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);
    for (char c : "ASDFGHJKL".toCharArray())
      row.add(letterKey(String.valueOf(c)));

    row.add(letterKey("'"));
    row.add(buildModeToggleButton("123@", KeyboardMode.NUMERIC));
    row.add(buildModeToggleButton("ABC", KeyboardMode.ABC));
    return row;
  }

  private JPanel buildAbcRow3() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);
    for (char c : "ZXCVBNM,.".toCharArray())
      row.add(letterKey(String.valueOf(c)));

    JButton space = styledKey("SPACE", new Dimension(420, 60));
    space.addActionListener(e -> {
      searchBuffer.append(' ');
      syncSearchLabel();
      if (enableTypeAheadSearch)
        executeSearch();
    });
    row.add(space);
    return row;
  }

  private JPanel buildNumRow1() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);
    for (String s : new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"})
      row.add(letterKey(s));

    JButton clear = styledKey("CLEAR", new Dimension(140, 60));
    clear.addActionListener(e -> resetSearch());
    row.add(clear);

    row.add(buildBackspaceButton());
    return row;
  }

  private JPanel buildNumRow2() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);
    for (String s : new String[] {"!", "@", "#", "$", "%", "^", "&", "*", "\""})
      row.add(letterKey(s));

    row.add(buildModeToggleButton("123@", KeyboardMode.NUMERIC));
    row.add(buildModeToggleButton("ABC", KeyboardMode.ABC));
    return row;
  }

  private JPanel buildNumRow3() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);
    for (String s : new String[] {"(", ")", "[", "]", "/", "\\", "?", ":", ";", "~"})
      row.add(letterKey(s));

    JButton space = styledKey("SPACE", new Dimension(420, 60));
    space.addActionListener(e -> {
      searchBuffer.append(' ');
      syncSearchLabel();
      if (enableTypeAheadSearch)
        executeSearch();
    });
    row.add(space);
    return row;
  }

  private JButton buildBackspaceButton() {
    JButton back = styledKey("⌫", new Dimension(100, 60));
    back.addActionListener(e -> {
      if (searchBuffer.length() > 0) {
        searchBuffer.deleteCharAt(searchBuffer.length() - 1);
        syncSearchLabel();
        if (enableTypeAheadSearch)
          executeSearch();
      }
    });
    return back;
  }

  private JButton buildModeToggleButton(String label, KeyboardMode targetMode) {
    JButton btn = styledKey(label, new Dimension(140, 60));
    if ((targetMode == KeyboardMode.ABC && keyboardMode == KeyboardMode.ABC)
        || (targetMode == KeyboardMode.NUMERIC && keyboardMode == KeyboardMode.NUMERIC)) {
      btn.setBackground(new Color(0, 160, 200));
    }
    btn.addActionListener(e -> {
      if (keyboardMode != targetMode) {
        keyboardMode = targetMode;
        refreshKeyboardWrappers();
      }
    });
    return btn;
  }

  private void refreshKeyboardWrappers() {
    swapKeyboardInWrapper(entryKeyboardWrapper);
    swapKeyboardInWrapper(resultsKeyboardWrapper);
  }

  private void swapKeyboardInWrapper(JPanel wrapper) {
    if (wrapper == null)
      return;

    JPanel frostedBody = (JPanel) wrapper.getComponent(0);
    frostedBody.removeAll();

    JPanel centreShell = new JPanel(new GridBagLayout());
    centreShell.setOpaque(false);
    centreShell.add(buildKeyboardPanel());
    frostedBody.add(centreShell, BorderLayout.CENTER);

    wrapper.repaint();
    wrapper.revalidate();
  }

  private JButton letterKey(String text) {
    JButton btn = styledKey(text, new Dimension(70, 60));
    btn.addActionListener(e -> {
      searchBuffer.append(text);
      syncSearchLabel();
      if (enableTypeAheadSearch)
        executeSearch();
    });
    return btn;
  }

  private JButton styledKey(String text, Dimension size) {
    JButton btn = new JButton(text) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 7;
        int bandH = Math.round(h * 0.28f);
        int faceH = h - bandH;

        g2.setColor(KEY_SHADOW);
        g2.fillRoundRect(1, 3, w - 2, h - 2, arc, arc);

        g2.setColor(KEY_FRONT);
        g2.fillRoundRect(1, faceH - arc / 2, w - 2, bandH + arc / 2, arc, arc);

        float[] frac = {0.0f, 0.55f, 1.0f};
        Color[] cols = {KEY_TOP, KEY_MID, KEY_FACE};
        g2.setPaint(new LinearGradientPaint(0, 0, 0, faceH, frac, cols));
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        g2.setColor(new Color(130, 130, 138, 180));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        g2.setColor(new Color(88, 88, 96, 80));
        g2.drawLine(1, 2, 1, faceH - 2);
        g2.drawLine(w - 2, 2, w - 2, faceH - 2);

        g2.setColor(model.isArmed() ? ACCENT_BLUE : TEXT_PRIMARY);
        g2.setFont(getFont());
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = (faceH - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(getText(), tx, ty);

        g2.dispose();
      }

      @Override
      protected void paintBorder(Graphics g) {}
    };

    btn.setPreferredSize(size);
    btn.setFocusPainted(false);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setOpaque(false);
    btn.setForeground(TEXT_PRIMARY);
    btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return btn;
  }

  private void executeSearch() {
    String query = searchBuffer.toString().trim();
    if (query.isEmpty())
      return;

    try {
      lastResult = songLibraryService.getMusicBySearch(query);
      artistsOffset = 0;
      albumsOffset = 0;
      songsOffset = 0;
      rebuildResultsCard();
      cardLayout.show(rootPanel, CARD_RESULTS);
    } catch (Exception ignored) {
    }
  }

  private void resetSearch() {
    searchBuffer.setLength(0);
    syncSearchLabel();
    lastResult = null;
    artistsOffset = 0;
    albumsOffset = 0;
    songsOffset = 0;
    resultsCard.removeAll();
    resultsCard.revalidate();
    resultsCard.repaint();
    cardLayout.show(rootPanel, CARD_ENTRY);
  }

  private void syncSearchLabel() {
    String display = searchBuffer.toString().isEmpty() ? " " : searchBuffer.toString();
    if (entrySearchLabel != null)
      entrySearchLabel.setText(display);
    if (resultsSearchLabel != null)
      resultsSearchLabel.setText(display);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // FIXED ITEM #1: EXACT SCREEN BOUNDARY MARGIN COLUMN ALIGNMENT
  // ─────────────────────────────────────────────────────────────────────────

  private void rebuildResultsCard() {
    resultsCard.removeAll();

    List<ArtistDto> artists = safeList(lastResult.getArtists());
    List<AlbumDto> albums = safeList(lastResult.getAlbums());
    List<SongDto> songs = safeList(lastResult.getSongs());

    JPanel columnsLayoutContainer = new JPanel(new GridLayout(1, 3, 0, 0));
    columnsLayoutContainer.setOpaque(false);

    // MATHEMATICAL FIX: By subtracting COLUMN_INTERNAL_EDGE_GAP (10px) from
    // SCREEN_PADDING_HORIZONTAL (60px),
    // the outer edges of the content layout expand exactly 10px outwards. This completely
    // counteracts the inner column body's
    // internal border padding, aligning the columns perfectly with the search entry field border.
    int unifiedPaddingCalculation = SCREEN_PADDING_HORIZONTAL - COLUMN_INTERNAL_EDGE_GAP;
    columnsLayoutContainer
        .setBorder(new EmptyBorder(10, unifiedPaddingCalculation, 10, unifiedPaddingCalculation));

    columnsLayoutContainer.add(ResultsColumnPanel.build("ARTISTS", artists, artistsOffset,
        SEARCH_PREVIEW_COUNT, imageLoader, () -> {
          artistsOffset = Math.max(0, artistsOffset - 1);
          rebuildResultsCard();
        }, () -> {
          artistsOffset++;
          rebuildResultsCard();
        }, item -> handleRowClick("ARTISTS", item)));

    columnsLayoutContainer.add(ResultsColumnPanel.build("ALBUMS", albums, albumsOffset,
        SEARCH_PREVIEW_COUNT, imageLoader, () -> {
          albumsOffset = Math.max(0, albumsOffset - 1);
          rebuildResultsCard();
        }, () -> {
          albumsOffset++;
          rebuildResultsCard();
        }, item -> handleRowClick("ALBUMS", item)));

    columnsLayoutContainer.add(ResultsColumnPanel.build("SONGS", songs, songsOffset,
        SEARCH_PREVIEW_COUNT, imageLoader, () -> {
          songsOffset = Math.max(0, songsOffset - 1);
          rebuildResultsCard();
        }, () -> {
          songsOffset++;
          rebuildResultsCard();
        }, item -> handleRowClick("SONGS", item)));

    resultsKeyboardWrapper = buildKeyboardWrapper(false);

    resultsCard.add(buildSearchBarPanel(true), BorderLayout.NORTH);
    resultsCard.add(columnsLayoutContainer, BorderLayout.CENTER);
    resultsCard.add(resultsKeyboardWrapper, BorderLayout.SOUTH);
    resultsCard.revalidate();
    resultsCard.repaint();
  }

  private <T> void handleRowClick(String category, T item) {
    switch (category) {
      case "ARTISTS" -> {
        if (item instanceof ArtistDto a)
          pushArtist(a);
      }
      case "ALBUMS" -> {
        if (item instanceof AlbumDto a)
          pushAlbumDetail(a);
      }
      case "SONGS" -> {
        if (item instanceof SongDto song) {
          Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
          AddSongToQueueDialog.show(owner, song, imageLoader, normalPlayCost, priorityCost,
              () -> songQueueService.addSongToQueue(
                  new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 0)),
              () -> songQueueService.addSongToQueue(
                  new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 1)));
        }
      }
    }
  }

  private void pushArtist(ArtistDto artist) {
    ArtistDto full;
    try {
      full = songLibraryService.getArtistById(artist.getArtistId());
    } catch (Exception e) {
      return;
    }

    ArtistDetailPanel panel =
        new ArtistDetailPanel(full, imageLoader, gridCols, gridRows, artW, artH, "← BACK",
            () -> cardLayout.show(rootPanel, CARD_RESULTS), album -> pushAlbumDetail(album));

    replaceCard(CARD_ARTIST, panel);
    cardLayout.show(rootPanel, CARD_ARTIST);
  }

  private AlbumDto fetchFull(AlbumDto album) {
    try {
      return songLibraryService.getAlbumById(album.getAlbumId());
    } catch (Exception e) {
      return album;
    }
  }

  private void replaceCard(String name, JPanel newPanel) {
    for (int i = rootPanel.getComponentCount() - 1; i >= 0; i--) {
      if (name.equals(rootPanel.getComponent(i).getName())) {
        rootPanel.remove(i);
        break;
      }
    }
    newPanel.setName(name);
    rootPanel.add(newPanel, name);
    rootPanel.revalidate();
    rootPanel.repaint();
  }

  private static <T> List<T> safeList(List<T> list) {
    return list != null ? list : List.of();
  }

  private JPanel placeholder() {
    JPanel p = new JPanel();
    p.setOpaque(false);
    return p;
  }
}
