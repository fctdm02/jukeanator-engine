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
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;

public class SearchPanel extends JPanel implements TabNavigator {

  private static final long serialVersionUID = 1L;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color TEXT_PRIMARY = Color.WHITE;

  // ── Layout constants — sourced from LayoutTheme ───────────────────────────
  // All pixel values previously declared here as local static constants are now
  // read from LayoutTheme.get() at use-site so that portrait / small-screen
  // themes override them from one place.

  // ── Card names ────────────────────────────────────────────────────────────
  private static final String CARD_ENTRY = "ENTRY";
  private static final String CARD_RESULTS = "RESULTS";
  private static final String CARD_ARTIST = "ARTIST";
  private static final String CARD_DETAIL = "DETAIL";

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

  private KeyboardPanel entryKeyboard;
  private KeyboardPanel resultsKeyboard;

  private AlbumDetailCard currentDetailCard;

  // ── Tracks which card to return to when the detail card's BACK button is
  // pressed — CARD_RESULTS (or CARD_ENTRY if no search has run yet) if the
  // album was opened from the result columns, CARD_ARTIST if it was opened
  // from the artist detail panel. ─────────────────────────────────────────
  private String detailReturnCard = CARD_ENTRY;

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final char incrementCreditsKey;
  private final CreditManager creditManager;
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final ImageLoader imageLoader;
  private final int priorityCostMultiplier;
  private final int popularityT1;
  private final int popularityT2;
  private final int popularityT3;
  private final boolean enableTypeAheadSearch;

  // ── Resolution-aware grid profile for the album sub-grid (artist detail) ──
  private final LayoutTheme.GridProfile albumGridProfile;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public SearchPanel(char incrementCreditsKey, CreditManager creditManager,
      SongLibraryService songLibraryService, SongQueueService songQueueService,
      ImageLoader imageLoader, int priorityCostMultiplier, int popularityT1, int popularityT2,
      int popularityT3, boolean enableTypeAheadSearch, LayoutTheme.GridProfile albumGridProfile) {

    this.incrementCreditsKey = incrementCreditsKey;
    this.creditManager = creditManager;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.imageLoader = imageLoader;
    this.priorityCostMultiplier = priorityCostMultiplier;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;
    this.enableTypeAheadSearch = enableTypeAheadSearch;
    this.albumGridProfile = albumGridProfile;

    setLayout(new BorderLayout());
    setOpaque(false);

    rootPanel.setOpaque(false);
    add(rootPanel, BorderLayout.CENTER);

    resultsCard.setOpaque(false);

    JPanel entryCard = buildEntryCard();
    entryCard.setName(CARD_ENTRY);
    rootPanel.add(entryCard, CARD_ENTRY);

    resultsCard.setName(CARD_RESULTS);
    rootPanel.add(resultsCard, CARD_RESULTS);

    JPanel artistPlaceholder = placeholder();
    artistPlaceholder.setName(CARD_ARTIST);
    rootPanel.add(artistPlaceholder, CARD_ARTIST);

    JPanel detailPlaceholder = placeholder();
    detailPlaceholder.setName(CARD_DETAIL);
    rootPanel.add(detailPlaceholder, CARD_DETAIL);

    cardLayout.show(rootPanel, CARD_ENTRY);
  }

  @Override
  public void pushAlbumDetail(AlbumDto album) {

    Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
    AlbumDto full = fetchFull(album);

    // Remember which card was visible before navigating to the detail card so
    // the BACK button can return the user to the correct screen (the search
    // results or the artist detail panel).
    detailReturnCard = currentVisibleCard();

    if (currentDetailCard != null)
      currentDetailCard.dismiss();

    currentDetailCard =
        new AlbumDetailCard(owner, full, imageLoader, songQueueService, priorityCostMultiplier,
            popularityT1, popularityT2, popularityT3, this, creditManager, incrementCreditsKey);

    replaceCard(CARD_DETAIL, currentDetailCard);
    cardLayout.show(rootPanel, CARD_DETAIL);
  }

  @Override
  public void popToRoot() {
    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
      currentDetailCard = null;
    }
    cardLayout.show(rootPanel, detailReturnCard);
  }

  /**
   * Resets the Search tab to its initial entry state (blank query, no results). Called whenever the
   * user switches to this tab.
   */
  public void resetToDefaultView() {
    resetSearch();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HERO PANEL WITH ENHANCED LIGHTENING GLASS OVERLAY
  // ─────────────────────────────────────────────────────────────────────────

  private JPanel buildEntryCard() {
    JPanel root = new JPanel(new BorderLayout());
    root.setOpaque(false);

    // Outer alignment container shell ensuring margins allow background gradient to pass through
    JPanel heroWrapper = new JPanel(new BorderLayout());
    heroWrapper.setOpaque(false);
    heroWrapper.setBorder(new EmptyBorder(0, LayoutTheme.get().searchPanelPaddingHorizontal, 0,
        LayoutTheme.get().searchPanelPaddingHorizontal));

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
    enhancedHeroBody.setPreferredSize(new Dimension(100, LayoutTheme.get().searchHeroHeight));

    JLabel heroLabel = new JLabel("Search for your favorite music.");
    heroLabel.setForeground(TEXT_PRIMARY);
    heroLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeSearchHero));
    enhancedHeroBody.add(heroLabel);

    heroWrapper.add(enhancedHeroBody, BorderLayout.CENTER);

    JPanel searchBar = buildSearchBarPanel(false);
    searchBar.setPreferredSize(new Dimension(100, LayoutTheme.get().searchBarHeight));

    entryKeyboard = buildSearchKeyboard();

    root.add(searchBar, BorderLayout.NORTH);
    root.add(heroWrapper, BorderLayout.CENTER);

    // Item 1.3: small spacer between hero area and keyboard panel (small-landscape only;
    // zero height on other resolutions so no visual change elsewhere).
    int spacerH = LayoutTheme.get().searchHeroSpacerH;
    if (spacerH > 0) {
      JPanel spacer = new JPanel();
      spacer.setOpaque(false);
      spacer.setPreferredSize(new Dimension(1, spacerH));
      JPanel southStack = new JPanel(new BorderLayout());
      southStack.setOpaque(false);
      southStack.add(spacer, BorderLayout.NORTH);
      southStack.add(entryKeyboard, BorderLayout.CENTER);
      root.add(southStack, BorderLayout.SOUTH);
    } else {
      root.add(entryKeyboard, BorderLayout.SOUTH);
    }
    return root;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // SEARCH BAR LAYOUT AND RE-ALIGNED BORDER
  // ─────────────────────────────────────────────────────────────────────────

  private JPanel buildSearchBarPanel(boolean forResults) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.setBorder(new EmptyBorder(LayoutTheme.get().searchBarWrapperPadV,
        LayoutTheme.get().searchPanelPaddingHorizontal, LayoutTheme.get().searchBarWrapperPadV,
        LayoutTheme.get().searchPanelPaddingHorizontal));

    JPanel bar = new JPanel(new BorderLayout(10, 0));
    bar.setBackground(Color.BLACK);
    // Asymmetrical clean white border setup: Top=2px, Left/Bottom/Right=1px
    bar.setBorder(BorderFactory.createMatteBorder(2, 1, 1, 1, Color.WHITE));

    JLabel lbl = new JLabel();
    lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeSearchBar));
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
      btn.setPreferredSize(
          new Dimension(LayoutTheme.get().searchBtnW, LayoutTheme.get().searchBtnH));
      btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeSearchBtn));
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
  // KEYBOARD — delegates to shared KeyboardPanel
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Builds a {@link KeyboardPanel} wired to the search buffer so that every key press appends to
   * (or modifies) the current query and, when type-ahead is enabled, immediately triggers a search.
   */
  private KeyboardPanel buildSearchKeyboard() {
    return new KeyboardPanel(new KeyboardPanel.KeyboardListener() {
      @Override
      public void onCharacter(String ch) {
        searchBuffer.append(ch);
        syncSearchLabel();
        if (enableTypeAheadSearch)
          executeSearch();
      }

      @Override
      public void onBackspace() {
        if (searchBuffer.length() > 0) {
          searchBuffer.deleteCharAt(searchBuffer.length() - 1);
          syncSearchLabel();
          if (enableTypeAheadSearch)
            executeSearch();
        }
      }

      @Override
      public void onClear() {
        resetSearch();
      }

      @Override
      public void onSpace() {
        searchBuffer.append(' ');
        syncSearchLabel();
        if (enableTypeAheadSearch)
          executeSearch();
      }
    });
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
  // EXACT SCREEN BOUNDARY MARGIN COLUMN ALIGNMENT
  // ─────────────────────────────────────────────────────────────────────────

  private void rebuildResultsCard() {
    resultsCard.removeAll();

    List<ArtistDto> artists = safeList(lastResult.getArtists());
    List<AlbumDto> albums = safeList(lastResult.getAlbums());
    List<SongDto> songs = safeList(lastResult.getSongs());

    JPanel columnsLayoutContainer = new JPanel(new GridLayout(1, 3, 0, 0));
    columnsLayoutContainer.setOpaque(false);

    // MATHEMATICAL FIX: By subtracting columnInternalEdgeGap (10px) from the panel's
    // horizontal padding, the outer column edges expand to line up with the screen edges
    // at the same indent as the keyboard and search bar panels.
    // Uses searchPanelPaddingHorizontal so all three panels share the same left/right indent.
    int unifiedPaddingCalculation =
        LayoutTheme.get().searchPanelPaddingHorizontal - LayoutTheme.get().columnInternalEdgeGap;
    columnsLayoutContainer
        .setBorder(new EmptyBorder(10, unifiedPaddingCalculation, 10, unifiedPaddingCalculation));

    int previewCount = LayoutTheme.get().searchPreviewCount;

    columnsLayoutContainer.add(ResultsColumnPanel.build("ARTISTS", artists, artistsOffset,
        previewCount, imageLoader, newOffset -> {
          artistsOffset = newOffset;
          rebuildResultsCard();
        }, item -> handleRowClick("ARTISTS", item)));

    columnsLayoutContainer.add(ResultsColumnPanel.build("ALBUMS", albums, albumsOffset,
        previewCount, imageLoader, newOffset -> {
          albumsOffset = newOffset;
          rebuildResultsCard();
        }, item -> handleRowClick("ALBUMS", item)));

    columnsLayoutContainer.add(ResultsColumnPanel.build("SONGS", songs, songsOffset, previewCount,
        imageLoader, newOffset -> {
          songsOffset = newOffset;
          rebuildResultsCard();
        }, item -> handleRowClick("SONGS", item)));

    resultsKeyboard = buildSearchKeyboard();

    resultsCard.add(buildSearchBarPanel(true), BorderLayout.NORTH);
    resultsCard.add(columnsLayoutContainer, BorderLayout.CENTER);
    resultsCard.add(resultsKeyboard, BorderLayout.SOUTH);
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
          if (owner instanceof JukeANatorFrame frame) {
            frame.showAddSongToQueueCard(song);
          }
        }
      }
    }
  }

  private void pushArtist(ArtistDto artist) {

    ArtistDto full = null;
    String artistName = artist.getArtistName();
    try {
      full = songLibraryService.getArtistByName(artistName);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalStateException("Could not get artist: [" + artistName + "]", e);
    }

    ArtistDetailPanel panel = new ArtistDetailPanel(full, imageLoader, albumGridProfile, "← BACK",
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

  /**
   * Returns the name of the card currently visible in {@code rootPanel}, falling back to
   * {@code CARD_ENTRY} if none is marked visible (e.g. before the first layout pass).
   */
  private String currentVisibleCard() {
    for (java.awt.Component c : rootPanel.getComponents()) {
      if (c.isVisible()) {
        return c.getName();
      }
    }
    return CARD_ENTRY;
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
