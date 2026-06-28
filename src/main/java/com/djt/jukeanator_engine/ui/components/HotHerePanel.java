package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;

public class HotHerePanel extends JPanel implements TabNavigator {

  private static final long serialVersionUID = 1L;

  // ── Sort mode ─────────────────────────────────────────────────────────────
  public enum SortMode {
    POPULARITY, TITLE
  }

  // ── Preview row count — sourced from LayoutTheme ──────────────────────────
  // Previously: private static final int PREVIEW_COUNT = 10;

  // ── Card names ────────────────────────────────────────────────────────────
  private static final String CARD_CONTENT = "CONTENT";
  private static final String CARD_ARTIST = "ARTIST";
  private static final String CARD_DETAIL = "DETAIL";

  // ── Layout ────────────────────────────────────────────────────────────────
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel rootPanel = new JPanel(cardLayout);
  private final JPanel contentPanel = new JPanel(new BorderLayout());
  private final JPanel columnsPanel = new JPanel(new GridLayout(1, 3, 2, 0));

  // ── Offset state per column ───────────────────────────────────────────────
  private int artistsOffset = 0;
  private int albumsOffset = 0;
  private int songsOffset = 0;

  // ── Active detail card ────────────────────────────────────────────────────
  private AlbumDetailCard currentDetailCard;

  // ── Tracks which card to return to when the detail card's BACK button is
  // pressed — CARD_CONTENT if the album was opened from the result columns,
  // CARD_ARTIST if it was opened from the artist detail panel. ────────────────
  private String detailReturnCard = CARD_CONTENT;

  // ── Popularity data (loaded once at construction, refreshed periodically by
  // the event-driven popularity update path). A title-sorted view is derived
  // from it in memory each time it refreshes — switching "Order By" never
  // re-queries SongLibraryService, it only swaps which of these two views is
  // shown. ────────────────────────────────────────────────────────────────────
  private SearchResultDto resultsByPopularity = new SearchResultDto();
  private SearchResultDto resultsByTitle = new SearchResultDto();
  private SortMode currentSort = SortMode.POPULARITY;

  // ── Header (kept to allow rebuilding on data refresh) ─────────────────────
  private DetailHeaderPanel headerPanel;
  private JButton btnPopularity;
  private JButton btnTitle;

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

  // ── Resolution-aware grid profile for the album sub-grid (artist detail) ──
  private final LayoutTheme.GridProfile albumGridProfile;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public HotHerePanel(char incrementCreditsKey, CreditManager creditManager,
      SongLibraryService songLibraryService, SongQueueService songQueueService,
      ImageLoader imageLoader, int priorityCostMultiplier, int popularityT1, int popularityT2,
      int popularityT3, LayoutTheme.GridProfile albumGridProfile) {

    this.incrementCreditsKey = incrementCreditsKey;
    this.creditManager = creditManager;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.imageLoader = imageLoader;
    this.priorityCostMultiplier = priorityCostMultiplier;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;
    this.albumGridProfile = albumGridProfile;

    setLayout(new BorderLayout());
    setOpaque(false);

    contentPanel.setOpaque(false);
    columnsPanel.setOpaque(false);
    // Each ResultsColumnPanel adds its own resultColumnPadH on both edges (for the gap
    // between adjacent columns). Applying a matching negative margin here cancels that
    // out on the outermost left/right edges only, so the first/last columns sit flush
    // with the screen edges — mirroring the technique used by GenreDetailPanel and
    // SearchPanel's results columns.
    int edgeOffset = -LayoutTheme.get().resultColumnPadH;
    columnsPanel.setBorder(new javax.swing.border.EmptyBorder(0, edgeOffset, 0, edgeOffset));
    contentPanel.add(columnsPanel, BorderLayout.CENTER);
    rootPanel.setOpaque(false);
    add(rootPanel, BorderLayout.CENTER);

    contentPanel.setName(CARD_CONTENT);
    rootPanel.add(contentPanel, CARD_CONTENT);

    JPanel artistPlaceholder = placeholder();
    artistPlaceholder.setName(CARD_ARTIST);
    rootPanel.add(artistPlaceholder, CARD_ARTIST);

    JPanel detailPlaceholder = placeholder();
    detailPlaceholder.setName(CARD_DETAIL);
    rootPanel.add(detailPlaceholder, CARD_DETAIL);

    refreshMusicByPopularityResults();
  }

  public void refreshMusicByPopularityResults() {

    try {
      this.resultsByPopularity = songLibraryService.getMusicByPopularity();
    } catch (Exception e) {
      throw new RuntimeException("Could not get music by popularity, error: " + e.getMessage(), e);
    }
    if (this.resultsByPopularity == null) {
      this.resultsByPopularity = new SearchResultDto();
    }

    // Derive the title-sorted view in memory from the same popularity payload —
    // no additional SongLibraryService calls are made.
    this.resultsByTitle = sortByTitle(this.resultsByPopularity);

    rebuildHeaderPanel();
    rebuildColumnsPanel();

    // Only navigate to the content card if the user is already there.
    // If they are mid-navigation (e.g. viewing an artist or album detail),
    // leave the current card in place so a background popularity refresh
    // does not disrupt their session.
    if (CARD_CONTENT.equals(currentVisibleCard())) {
      cardLayout.show(rootPanel, CARD_CONTENT);
    }
  }

  /**
   * Builds a new {@link SearchResultDto} whose artists/albums/songs are copies of
   * {@code source}'s lists, sorted alphabetically by name (artist name, album name, song name
   * respectively). Blank/null names sort to the end.
   */
  private SearchResultDto sortByTitle(SearchResultDto source) {

    List<ArtistDto> artists = new ArrayList<>(safeList(source.getArtists()));
    artists.sort(Comparator.comparing(a -> titleSortKey(a.getArtistName())));

    List<AlbumDto> albums = new ArrayList<>(safeList(source.getAlbums()));
    albums.sort(Comparator.comparing(a -> titleSortKey(a.getAlbumName())));

    List<SongDto> songs = new ArrayList<>(safeList(source.getSongs()));
    songs.sort(Comparator.comparing(s -> titleSortKey(s.getSongName())));

    return new SearchResultDto(songs, artists, albums);
  }

  /**
   * Returns a sort key for {@code name}: blank/null names sort to the end, letters sort after
   * symbols/digits (prefixed with '~') so symbol/digit names sort before A–Z naturally.
   */
  private static String titleSortKey(String name) {
    if (name == null || name.isBlank())
      return "￿";
    char first = Character.toUpperCase(name.charAt(0));
    return Character.isLetter(first) ? ("~" + name.toUpperCase()) : name.toUpperCase();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HEADER (image art, title, counts, Order By) — mirrors the Genre Results
  // screen's DetailHeaderPanel + sort-button layout.
  // ─────────────────────────────────────────────────────────────────────────

  /** (Re)builds the header to reflect the latest counts and current sort state. */
  private void rebuildHeaderPanel() {

    SearchResultDto current = currentSort == SortMode.POPULARITY ? resultsByPopularity : resultsByTitle;
    int artistCount = safeList(current.getArtists()).size();
    int albumCount = safeList(current.getAlbums()).size();
    int songCount = safeList(current.getSongs()).size();

    // Icon size/sizing matches the Home screen's "All Albums" header. No dedicated
    // Hot Here artwork exists yet, so the load falls through to the "🔥" fallback
    // glyph that already represents this tab on the JukeboxTabComponent.
    int headerIconSize = LayoutTheme.get().detailHeaderImageW;
    ImageIcon hotHereIcon =
        imageLoader.loadImage("HotHereLogo.png", headerIconSize, headerIconSize);

    String subtitle =
        artistCount + " artists  •  " + albumCount + " albums  •  " + songCount + " songs";

    headerPanel = new DetailHeaderPanel(null, null, hotHereIcon, "🔥", "Hot Here", subtitle,
        buildSortButtonPanel());
    headerPanel.setOpaque(false);
    int hbH = LayoutTheme.get().homeHeaderBorderH;
    headerPanel.setBorder(new javax.swing.border.EmptyBorder(4, hbH, 4, hbH));

    // Replace whatever header is currently in the NORTH slot (none on first build).
    for (java.awt.Component c : contentPanel.getComponents()) {
      if (c instanceof DetailHeaderPanel) {
        contentPanel.remove(c);
        break;
      }
    }
    contentPanel.add(headerPanel, BorderLayout.NORTH);
    contentPanel.revalidate();
  }

  private JPanel buildSortButtonPanel() {

    JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
    row.setOpaque(false);
    row.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 8));

    JLabel sortLabel = new JLabel("Order By: ");
    sortLabel.setForeground(ColorTheme.get().textPrimary);
    sortLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeSortLabel));
    row.add(sortLabel);

    btnPopularity = sortButton("Popularity", SortMode.POPULARITY);
    btnTitle = sortButton("Title", SortMode.TITLE);

    row.add(btnPopularity);
    row.add(btnTitle);

    JPanel wrapper = new JPanel();
    wrapper.setLayout(new javax.swing.BoxLayout(wrapper, javax.swing.BoxLayout.Y_AXIS));
    wrapper.setOpaque(false);
    wrapper.add(javax.swing.Box.createVerticalGlue());
    wrapper.add(row);
    wrapper.add(javax.swing.Box.createVerticalGlue());

    return wrapper;
  }

  private JButton sortButton(String label, SortMode mode) {

    JButton btn = new JButton(label) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean active = (currentSort == mode);

        if (active) {
          g2.setPaint(new GradientPaint(0, 0, ColorTheme.get().navBtnGradTop, 0, getHeight(),
              ColorTheme.get().navBtnGradBottom));
        } else {
          g2.setColor(ColorTheme.get().sortBtnIdleBg);
        }
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

        g2.setColor(active ? ColorTheme.get().accentBlue : ColorTheme.get().detailHeaderBorder);
        g2.setStroke(new java.awt.BasicStroke(active ? 1.5f : 1.0f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        g2.dispose();
        super.paintComponent(g);
      }
    };

    btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeSortBtn));
    btn.setForeground(
        currentSort == mode ? ColorTheme.get().textPrimary : ColorTheme.get().textMuted);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setOpaque(false);
    btn.setPreferredSize(new Dimension(LayoutTheme.get().sortBtnW, LayoutTheme.get().sortBtnH));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    btn.addActionListener(e -> applySortMode(mode));

    btn.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        btn.repaint();
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        btn.repaint();
      }
    });

    return btn;
  }

  /**
   * Switches between popularity and title ordering. Never re-queries
   * {@link SongLibraryService} — both views were already built in memory by
   * {@link #refreshMusicByPopularityResults()}; this only swaps which one is displayed.
   */
  private void applySortMode(SortMode mode) {

    if (mode == currentSort)
      return;
    currentSort = mode;

    artistsOffset = 0;
    albumsOffset = 0;
    songsOffset = 0;

    rebuildHeaderPanel();
    rebuildColumnsPanel();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // TabNavigator
  // ─────────────────────────────────────────────────────────────────────────

  @Override
  public void pushAlbumDetail(AlbumDto album) {

    Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
    AlbumDto full = fetchFull(album);

    // Remember which card was visible before navigating to the detail card so
    // the BACK button can return the user to the correct screen (the result
    // columns or the artist detail panel).
    detailReturnCard = currentVisibleCard();

    currentDetailCard =
        new AlbumDetailCard(owner, full, imageLoader, songQueueService, priorityCostMultiplier,
            popularityT1, popularityT2, popularityT3, this, creditManager, incrementCreditsKey);

    replaceCard(CARD_DETAIL, currentDetailCard);
    cardLayout.show(rootPanel, CARD_DETAIL);
  }

  @Override
  public void popToRoot() {

    currentDetailCard = null;
    cardLayout.show(rootPanel, detailReturnCard);
  }

  /**
   * Resets the Hot Here tab to its default view: returns to the content card and scrolls all three
   * result columns back to their first page. Does NOT re-query the service — the existing results
   * data is kept intact so that the event-driven popularity update path remains the sole source of
   * refreshed data.
   */
  public void resetToDefaultView() {
    currentDetailCard = null;
    artistsOffset = 0;
    albumsOffset = 0;
    songsOffset = 0;
    detailReturnCard = CARD_CONTENT;
    rebuildColumnsPanel();
    cardLayout.show(rootPanel, CARD_CONTENT);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CONTENT PANEL
  // ─────────────────────────────────────────────────────────────────────────
  private void rebuildColumnsPanel() {

    columnsPanel.removeAll();

    SearchResultDto current = currentSort == SortMode.POPULARITY ? resultsByPopularity : resultsByTitle;
    List<ArtistDto> artists = safeList(current.getArtists());
    List<AlbumDto> albums = safeList(current.getAlbums());
    List<SongDto> songs = safeList(current.getSongs());

    int previewCount = LayoutTheme.get().hotHerePreviewCount;

    columnsPanel.add(ResultsColumnPanel.build("ARTISTS", artists, artistsOffset, previewCount,
        imageLoader, newOffset -> {
          artistsOffset = newOffset;
          rebuildColumnsPanel();
        }, (item) -> handleRowClick("ARTISTS", item)));

    columnsPanel.add(ResultsColumnPanel.build("ALBUMS", albums, albumsOffset, previewCount,
        imageLoader, newOffset -> {
          albumsOffset = newOffset;
          rebuildColumnsPanel();
        }, (item) -> handleRowClick("ALBUMS", item)));

    columnsPanel.add(ResultsColumnPanel.build("SONGS", songs, songsOffset, previewCount,
        imageLoader, newOffset -> {
          songsOffset = newOffset;
          rebuildColumnsPanel();
        }, (item) -> handleRowClick("SONGS", item)));

    columnsPanel.revalidate();
    columnsPanel.repaint();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ROW CLICK DISPATCH
  // ─────────────────────────────────────────────────────────────────────────
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

  // ─────────────────────────────────────────────────────────────────────────
  // ARTIST CARD
  // ─────────────────────────────────────────────────────────────────────────
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
        () -> cardLayout.show(rootPanel, CARD_CONTENT), album -> pushAlbumDetail(album));

    replaceCard(CARD_ARTIST, panel);
    cardLayout.show(rootPanel, CARD_ARTIST);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HELPERS
  // ─────────────────────────────────────────────────────────────────────────
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
   * {@code CARD_CONTENT} if none is marked visible (e.g. before the first layout pass).
   */
  private String currentVisibleCard() {
    for (java.awt.Component c : rootPanel.getComponents()) {
      if (c.isVisible()) {
        return c.getName();
      }
    }
    return CARD_CONTENT;
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
