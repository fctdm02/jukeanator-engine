package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.List;
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

  // ── Preview row count ─────────────────────────────────────────────────────
  private static final int PREVIEW_COUNT = 10;

  // ── Card names ────────────────────────────────────────────────────────────
  private static final String CARD_CONTENT = "CONTENT";
  private static final String CARD_ARTIST = "ARTIST";
  private static final String CARD_DETAIL = "DETAIL";

  // ── Layout ────────────────────────────────────────────────────────────────
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel rootPanel = new JPanel(cardLayout);
  private final JPanel contentPanel = new JPanel(new BorderLayout());

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

  // ── Popularity data (loaded once at construction) ─────────────────────────
  private SearchResultDto results;

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
  private final int gridCols;
  private final int gridRows;
  private final int artW;
  private final int artH;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public HotHerePanel(char incrementCreditsKey, CreditManager creditManager,
      SongLibraryService songLibraryService, SongQueueService songQueueService,
      ImageLoader imageLoader, int priorityCostMultiplier, int popularityT1, int popularityT2,
      int popularityT3, int gridCols, int gridRows, int artW, int artH) {

    this.incrementCreditsKey = incrementCreditsKey;
    this.creditManager = creditManager;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.imageLoader = imageLoader;
    this.priorityCostMultiplier = priorityCostMultiplier;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;
    this.gridCols = gridCols;
    this.gridRows = gridRows;
    this.artW = artW;
    this.artH = artH;

    setLayout(new BorderLayout());
    setOpaque(false);

    contentPanel.setOpaque(false);
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
      this.results = songLibraryService.getMusicByPopularity();
    } catch (Exception e) {
      throw new RuntimeException("Could not get music by popularity, error: " + e.getMessage(), e);
    }

    rebuildColumnsPanel();
    cardLayout.show(rootPanel, CARD_CONTENT);
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

    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
    }

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
   * Resets the Hot Here tab to its default view: returns to the content card and scrolls all three
   * result columns back to their first page. Does NOT re-query the service — the existing results
   * data is kept intact so that the event-driven popularity update path remains the sole source of
   * refreshed data.
   */
  public void resetToDefaultView() {
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

    contentPanel.removeAll();

    List<ArtistDto> artists = safeList(results.getArtists());
    List<AlbumDto> albums = safeList(results.getAlbums());
    List<SongDto> songs = safeList(results.getSongs());

    JPanel columns = new JPanel(new GridLayout(1, 3, 2, 0));
    columns.setOpaque(false);

    columns.add(ResultsColumnPanel.build("ARTISTS", artists, artistsOffset, PREVIEW_COUNT,
        imageLoader, newOffset -> {
          artistsOffset = newOffset;
          rebuildColumnsPanel();
        }, (item) -> handleRowClick("ARTISTS", item)));

    columns.add(ResultsColumnPanel.build("ALBUMS", albums, albumsOffset, PREVIEW_COUNT, imageLoader,
        newOffset -> {
          albumsOffset = newOffset;
          rebuildColumnsPanel();
        }, (item) -> handleRowClick("ALBUMS", item)));

    columns.add(ResultsColumnPanel.build("SONGS", songs, songsOffset, PREVIEW_COUNT, imageLoader,
        newOffset -> {
          songsOffset = newOffset;
          rebuildColumnsPanel();
        }, (item) -> handleRowClick("SONGS", item)));

    contentPanel.add(columns, BorderLayout.CENTER);
    contentPanel.revalidate();
    contentPanel.repaint();
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

    ArtistDto full;
    try {
      full = songLibraryService.getArtistById(artist.getArtistId());
    } catch (Exception e) {
      return;
    }

    ArtistDetailPanel panel =
        new ArtistDetailPanel(full, imageLoader, gridCols, gridRows, artW, artH, "← BACK",
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
