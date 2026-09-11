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
import java.awt.RenderingHints;
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
  private final JPanel columnsPanel = new JPanel();

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

  // ── Paged result buffers — one triple per sort mode, since switching "Order
  // By" queries SongLibraryService independently by popularity or by title.
  // Only the active mode's buffers are kept populated; the inactive mode's
  // are reset and lazily refilled the next time it becomes active. ──────────
  private final PagedCategoryBuffer<ArtistDto> popularityArtistBuffer;
  private final PagedCategoryBuffer<AlbumDto> popularityAlbumBuffer;
  private final PagedCategoryBuffer<SongDto> popularitySongBuffer;
  private final PagedCategoryBuffer<ArtistDto> titleArtistBuffer;
  private final PagedCategoryBuffer<AlbumDto> titleAlbumBuffer;
  private final PagedCategoryBuffer<SongDto> titleSongBuffer;
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

    int serverPageSize = songLibraryService.getSearchResultPageSize();
    this.popularityArtistBuffer = new PagedCategoryBuffer<>(serverPageSize);
    this.popularityAlbumBuffer = new PagedCategoryBuffer<>(serverPageSize);
    this.popularitySongBuffer = new PagedCategoryBuffer<>(serverPageSize);
    this.titleArtistBuffer = new PagedCategoryBuffer<>(serverPageSize);
    this.titleAlbumBuffer = new PagedCategoryBuffer<>(serverPageSize);
    this.titleSongBuffer = new PagedCategoryBuffer<>(serverPageSize);

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

    SearchResultDto popularity;
    try {
      popularity =
          songLibraryService.getMusicByPopularity(songLibraryService.getOwnLocationId(), 0, 0, 0);
    } catch (Exception e) {
      throw new RuntimeException("Could not get music by popularity, error: " + e.getMessage(), e);
    }
    if (popularity == null) {
      popularity = new SearchResultDto(List.of(), List.of(), List.of());
    }

    popularityArtistBuffer.seedFirstPage(safeList(popularity.artists()));
    popularityAlbumBuffer.seedFirstPage(safeList(popularity.albums()));
    popularitySongBuffer.seedFirstPage(safeList(popularity.songs()));

    // The title-sorted view is now fetched independently from SongLibraryService (see
    // getMusicByTitle) rather than derived in memory, so it just needs to be reset here --
    // rebuildColumnsPanel() will lazily re-fetch it from page 0 if title mode is active.
    titleArtistBuffer.reset();
    titleAlbumBuffer.reset();
    titleSongBuffer.reset();

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

  // ─────────────────────────────────────────────────────────────────────────
  // HEADER (image art, title, Order By) — mirrors the Genre Results
  // screen's DetailHeaderPanel + sort-button layout.
  // ─────────────────────────────────────────────────────────────────────────

  /** (Re)builds the header to reflect the current sort state. */
  private void rebuildHeaderPanel() {

    // Icon size/sizing matches the Home screen's "All Albums" header. No dedicated
    // Hot Here artwork exists yet, so the load falls through to the "🔥" fallback
    // glyph that already represents this tab on the JukeboxTabComponent.
    int headerIconSize = LayoutTheme.get().detailHeaderImageW;
    ImageIcon hotHereIcon =
        imageLoader.loadImage("HotHere_Header_Image.png", headerIconSize, headerIconSize);

    headerPanel = new DetailHeaderPanel(null, null, hotHereIcon, "🔥", "Hot Here", null,
        buildSortButtonPanel(), ColorTheme.get().frameTabAccentHotHere);
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
    btnTitle = sortButton("Album", SortMode.TITLE);

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
   * Switches between popularity and title ordering. The newly active mode's buffers are reset so
   * {@link #rebuildColumnsPanel()} fetches fresh data from page 0 via {@code SongLibraryService}.
   */
  private void applySortMode(SortMode mode) {

    if (mode == currentSort)
      return;
    currentSort = mode;

    artistsOffset = 0;
    albumsOffset = 0;
    songsOffset = 0;

    if (mode == SortMode.POPULARITY) {
      popularityArtistBuffer.reset();
      popularityAlbumBuffer.reset();
      popularitySongBuffer.reset();
    } else {
      titleArtistBuffer.reset();
      titleAlbumBuffer.reset();
      titleSongBuffer.reset();
    }

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
   * refreshed data. Called by {@link JukeANatorFrame}'s idle monitor after 10 minutes of
   * inactivity, along with every other tab's resetToDefaultView() — not by ordinary tab switching,
   * so navigating within Hot Here and switching away and back leaves the view exactly as it was
   * left.
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

    int previewCount = LayoutTheme.get().hotHerePreviewCount;
    Integer locationId = songLibraryService.getOwnLocationId();
    boolean popularity = currentSort == SortMode.POPULARITY;

    PagedCategoryBuffer<ArtistDto> artistBuffer = popularity ? popularityArtistBuffer : titleArtistBuffer;
    PagedCategoryBuffer<AlbumDto> albumBuffer = popularity ? popularityAlbumBuffer : titleAlbumBuffer;
    PagedCategoryBuffer<SongDto> songBuffer = popularity ? popularitySongBuffer : titleSongBuffer;

    // A transient service failure here should not corrupt a buffer's exhausted/next-page
    // bookkeeping -- just leave it as whatever was already fetched and let the next page-down
    // attempt retry.
    try {
      artistBuffer.ensureWindowAvailable(artistsOffset, previewCount,
          pageIdx -> (popularity ? songLibraryService.getMusicByPopularity(locationId, pageIdx, 0, 0)
              : songLibraryService.getMusicByTitle(locationId, pageIdx, 0, 0)).artists());

      albumBuffer.ensureWindowAvailable(albumsOffset, previewCount,
          pageIdx -> (popularity ? songLibraryService.getMusicByPopularity(locationId, 0, pageIdx, 0)
              : songLibraryService.getMusicByTitle(locationId, 0, pageIdx, 0)).albums());

      songBuffer.ensureWindowAvailable(songsOffset, previewCount,
          pageIdx -> (popularity ? songLibraryService.getMusicByPopularity(locationId, 0, 0, pageIdx)
              : songLibraryService.getMusicByTitle(locationId, 0, 0, pageIdx)).songs());
    } catch (Exception ignored) {
    }

    List<ArtistDto> artists = artistBuffer.items();
    List<AlbumDto> albums = albumBuffer.items();
    List<SongDto> songs = songBuffer.items();

    JPanel artistsColumn = ResultsColumnPanel.build("ARTISTS", artists, artistsOffset, previewCount,
        imageLoader, newOffset -> {
          artistsOffset = newOffset;
          rebuildColumnsPanel();
        }, (item) -> handleRowClick("ARTISTS", item), ResultsColumnPanel.ColumnPosition.FIRST,
        popularityT1, popularityT2, popularityT3, -1);

    JPanel albumsColumn = ResultsColumnPanel.build("ALBUMS", albums, albumsOffset, previewCount,
        imageLoader, newOffset -> {
          albumsOffset = newOffset;
          rebuildColumnsPanel();
        }, (item) -> handleRowClick("ALBUMS", item), ResultsColumnPanel.ColumnPosition.MIDDLE,
        popularityT1, popularityT2, popularityT3, -1);

    JPanel songsColumn = ResultsColumnPanel.build("SONGS", songs, songsOffset, previewCount,
        imageLoader, newOffset -> {
          songsOffset = newOffset;
          rebuildColumnsPanel();
        }, (item) -> handleRowClick("SONGS", item), ResultsColumnPanel.ColumnPosition.LAST,
        popularityT1, popularityT2, popularityT3, -1);

    ResultsColumnPanel.layoutThreeColumns(columnsPanel, artistsColumn, albumsColumn, songsColumn);

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
    String artistName = artist.artistName();
    try {
      full = songLibraryService.getArtistByName(songLibraryService.getOwnLocationId(), artistName);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalStateException("Could not get artist: [" + artistName + "]", e);
    }

    ArtistDetailPanel panel = new ArtistDetailPanel(full, imageLoader, albumGridProfile, "← Back",
        () -> cardLayout.show(rootPanel, CARD_CONTENT), album -> pushAlbumDetail(album));

    replaceCard(CARD_ARTIST, panel);
    cardLayout.show(rootPanel, CARD_ARTIST);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HELPERS
  // ─────────────────────────────────────────────────────────────────────────
  private AlbumDto fetchFull(AlbumDto album) {
    try {
      return songLibraryService.getAlbumById(songLibraryService.getOwnLocationId(), album.albumId());
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
