package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * The "HOME" tab panel.
 *
 * <p>
 * Card layout with two cards:
 * <ol>
 * <li><b>GRID</b> — a full {@link AlbumGridPanel} showing every album in the library, loaded once
 * on construction.</li>
 * <li><b>ARTIST</b> — an {@link ArtistDetailPanel} shown when the user navigates to an artist from
 * elsewhere (future use); navigating back returns to GRID.</li>
 * </ol>
 *
 * <p>
 * Album tiles open {@link AlbumDetailDialog} directly (no separate card needed — the dialog is
 * modal).
 *
 * <p>
 * Grid dimensions and tile sizes are driven by
 * {@link com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties} fields
 * {@code homeGridCols}, {@code homeGridRows}, {@code homeTileArtWidth}, {@code homeTileArtHeight}
 * (add these to the properties class; defaults below).
 */
public class HomePanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Default grid config (override via properties) ─────────────────────────
  public static final int DEFAULT_COLS = 4;
  public static final int DEFAULT_ROWS = 3;
  public static final int DEFAULT_ART_W = 190;
  public static final int DEFAULT_ART_H = 190;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color BG_DARK = new Color(10, 10, 10);

  // ── Cards ─────────────────────────────────────────────────────────────────
  private static final String CARD_GRID = "GRID";
  private static final String CARD_ARTIST = "ARTIST";

  private final CardLayout cardLayout = new CardLayout();
  private final JPanel rootPanel = new JPanel(cardLayout);

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final ImageLoader imageLoader;
  private final int normalPlayCost;
  private final int priorityCost;
  private final int popularityT1;
  private final int popularityT2;
  private final int popularityT3;
  private final boolean enableBigScrollBars;
  private final int gridCols;
  private final int gridRows;
  private final int artW;
  private final int artH;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * @param songLibraryService Service for fetching albums / artist details.
   * @param songQueueService Service for queuing songs.
   * @param imageLoader Shared loader.
   * @param normalPlayCost Credits for normal play.
   * @param priorityCost Credits for priority play.
   * @param popularityT1 Lower popularity threshold (1 bar).
   * @param popularityT2 Middle popularity threshold (2 bars).
   * @param popularityT3 Upper popularity threshold (3 bars).
   * @param enableBigScrollBars Wide touch-friendly scroll bars.
   * @param gridCols Columns in the home album grid.
   * @param gridRows Rows in the home album grid.
   * @param artW Tile art pixel width.
   * @param artH Tile art pixel height.
   */
  public HomePanel(SongLibraryService songLibraryService, SongQueueService songQueueService,
      ImageLoader imageLoader, int normalPlayCost, int priorityCost, int popularityT1,
      int popularityT2, int popularityT3, boolean enableBigScrollBars, int gridCols, int gridRows,
      int artW, int artH) {

    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.imageLoader = imageLoader;
    this.normalPlayCost = normalPlayCost;
    this.priorityCost = priorityCost;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;
    this.enableBigScrollBars = enableBigScrollBars;
    this.gridCols = gridCols;
    this.gridRows = gridRows;
    this.artW = artW;
    this.artH = artH;

    setLayout(new BorderLayout());
    setBackground(BG_DARK);

    rootPanel.setBackground(BG_DARK);
    add(rootPanel, BorderLayout.CENTER);

    rootPanel.add(buildGridCard(), CARD_GRID);

    // ARTIST card is a placeholder; real content is swapped in on demand
    rootPanel.add(new JPanel(), CARD_ARTIST);

    cardLayout.show(rootPanel, CARD_GRID);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // GRID CARD
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildGridCard() {

    JPanel card = new JPanel(new BorderLayout());
    card.setBackground(BG_DARK);

    List<AlbumDto> allAlbums;
    try {
      allAlbums = songLibraryService.getAlbums();
    } catch (Exception e) {
      allAlbums = List.of();
    }

    DetailHeaderPanel header =
        new DetailHeaderPanel(null, null, null, "♫", "ALL ALBUMS", allAlbums.size() + " albums");

    if (allAlbums.isEmpty()) {

      JLabel empty = new JLabel("No albums found.", SwingConstants.CENTER);

      empty.setForeground(AlbumGridPanel.TEXT_SECONDARY);
      empty.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));

      card.add(header, BorderLayout.NORTH);
      card.add(empty, BorderLayout.CENTER);

      return card;
    }

    AlbumGridPanel grid = new AlbumGridPanel(allAlbums, imageLoader, gridCols, gridRows, artW, artH,
        album -> openAlbumDetail(album));

    card.add(header, BorderLayout.NORTH);
    card.add(grid, BorderLayout.CENTER);

    return card;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ARTIST CARD (navigated to from external callers, e.g. future artist list)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigate to the artist detail view within the Home tab. Can be called from outside (e.g. if a
   * future "Featured Artists" section lives on the Home tab and the user taps an artist name).
   */
  public void showArtist(ArtistDto artist) {

    ArtistDetailPanel artistPanel =
        new ArtistDetailPanel(artist, imageLoader, gridCols, gridRows, artW, artH, "← HOME",
            () -> cardLayout.show(rootPanel, CARD_GRID), album -> openAlbumDetail(album));

    rootPanel.remove(rootPanel.getComponent(getComponentIndex(CARD_ARTIST)));
    rootPanel.add(artistPanel, CARD_ARTIST);

    cardLayout.show(rootPanel, CARD_ARTIST);
    rootPanel.revalidate();
    rootPanel.repaint();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // OPEN ALBUM DETAIL
  // ─────────────────────────────────────────────────────────────────────────
  private void openAlbumDetail(AlbumDto album) {

    Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);

    // Fetch full album (with songs list)
    AlbumDto fullAlbum;
    try {
      fullAlbum = songLibraryService.getAlbumById(album.getAlbumId());
    } catch (Exception e) {
      fullAlbum = album;
    }
    
    // priority cost placeholder
    int numSongs = fullAlbum.getSongs().size();
    int albumNormalPlayCost = normalPlayCost * numSongs;
    int albumPriorityPlayCost = priorityCost * numSongs;

    AlbumDetailDialog.show(
        owner, 
        fullAlbum, 
        imageLoader, 
        songQueueService, 
        albumNormalPlayCost,
        albumPriorityPlayCost, 
        popularityT1, 
        popularityT2, 
        popularityT3, 
        enableBigScrollBars);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HELPER — find component index by card name in a CardLayout panel
  // ─────────────────────────────────────────────────────────────────────────
  private int getComponentIndex(String cardName) {
    // CardLayout stores components in order; GRID=0, ARTIST=1
    return cardName.equals(CARD_GRID) ? 0 : 1;
  }
}
