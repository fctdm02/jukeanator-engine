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

  // ── Popularity data (loaded once at construction) ─────────────────────────
  private SearchResultDto results;

  // ── Dependencies ──────────────────────────────────────────────────────────
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

  public HotHerePanel(CreditManager creditManager, SongLibraryService songLibraryService,
      SongQueueService songQueueService, ImageLoader imageLoader, int priorityCostMultiplier,
      int popularityT1, int popularityT2, int popularityT3, int gridCols, int gridRows, int artW,
      int artH) {

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

    rootPanel.add(contentPanel, CARD_CONTENT);
    rootPanel.add(placeholder(), CARD_ARTIST);
    rootPanel.add(placeholder(), CARD_DETAIL);

    refreshMusicByPopularityResults();
  }

  public void refreshMusicByPopularityResults() {

    try {
      this.results = songLibraryService.getMusicByPopularity();
    } catch (Exception e) {
      this.results = new SearchResultDto();
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

    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
    }

    currentDetailCard = new AlbumDetailCard(owner, full, imageLoader, songQueueService,
        priorityCostMultiplier, popularityT1, popularityT2, popularityT3, this, creditManager);

    replaceCard(CARD_DETAIL, currentDetailCard);
    cardLayout.show(rootPanel, CARD_DETAIL);
  }

  @Override
  public void popToRoot() {

    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
      currentDetailCard = null;
    }
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
          AddSongToQueueDialog.show(owner, song, imageLoader, priorityCostMultiplier,
              songQueueService, creditManager);
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

  private static <T> List<T> safeList(List<T> list) {
    return list != null ? list : List.of();
  }

  private JPanel placeholder() {
    JPanel p = new JPanel();
    p.setOpaque(false);
    return p;
  }
}
