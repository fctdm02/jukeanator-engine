package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;

public class HomePanel extends JPanel implements TabNavigator {

  private static final long serialVersionUID = 1L;

  // ── Default grid config ───────────────────────────────────────────────────
  public static final int DEFAULT_COLS = 4;
  public static final int DEFAULT_ROWS = 3;
  public static final int DEFAULT_ART_W = 190;
  public static final int DEFAULT_ART_H = 190;

  // ── Card names ────────────────────────────────────────────────────────────
  private static final String CARD_GRID = "GRID";
  private static final String CARD_ARTIST = "ARTIST";
  private static final String CARD_DETAIL = "DETAIL";

  // ── Layout ────────────────────────────────────────────────────────────────
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel rootPanel = new JPanel(cardLayout);

  // ── Active detail card (tracked so its timer can be stopped) ─────────────
  private AlbumDetailCard currentDetailCard;

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

  public HomePanel(char incrementCreditsKey, CreditManager creditManager,
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

    rootPanel.setOpaque(false);
    add(rootPanel, BorderLayout.CENTER);

    // Seed the three cards. ARTIST and DETAIL start as empty placeholders;
    // real content is swapped in on demand via replaceCard().
    rootPanel.add(buildGridCard(), CARD_GRID);
    rootPanel.add(placeholder(), CARD_ARTIST);
    rootPanel.add(placeholder(), CARD_DETAIL);

    cardLayout.show(rootPanel, CARD_GRID);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // TabNavigator — called by AlbumDetailCard
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Fetches the full album (with songs), builds an {@link AlbumDetailCard}, places it in the DETAIL
   * card slot, and flips to it. Any previously active detail card has its timer stopped first so
   * there are no dangling Swing timers.
   */
  @Override
  public void pushAlbumDetail(AlbumDto album) {

    Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);

    AlbumDto full = fetchFull(album);

    if (currentDetailCard != null) {
      currentDetailCard.dismiss(); // stop the countdown timer
    }

    currentDetailCard =
        new AlbumDetailCard(owner, full, imageLoader, songQueueService, priorityCostMultiplier,
            popularityT1, popularityT2, popularityT3, this, creditManager, incrementCreditsKey); // TabNavigator
    // back-reference

    replaceCard(CARD_DETAIL, currentDetailCard);
    cardLayout.show(rootPanel, CARD_DETAIL);
  }

  /**
   * Stops the detail card's countdown timer and returns to the root grid. Safe to call even when no
   * detail card is currently active.
   */
  @Override
  public void popToRoot() {

    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
      currentDetailCard = null;
    }
    cardLayout.show(rootPanel, CARD_GRID);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ARTIST CARD
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigate to the artist detail view within the Home tab. Can be called from outside (e.g. a
   * future "Featured Artists" section on the Home tab).
   */
  public void showArtist(ArtistDto artist) {

    ArtistDetailPanel artistPanel =
        new ArtistDetailPanel(artist, imageLoader, gridCols, gridRows, artW, artH, "← HOME",
            () -> cardLayout.show(rootPanel, CARD_GRID), album -> pushAlbumDetail(album)); // reuse
                                                                                           // TabNavigator
                                                                                           // path

    replaceCard(CARD_ARTIST, artistPanel);
    cardLayout.show(rootPanel, CARD_ARTIST);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // GRID CARD
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildGridCard() {

    JPanel card = new JPanel(new BorderLayout());
    card.setOpaque(false);

    List<AlbumDto> allAlbums;
    try {
      allAlbums = songLibraryService.getAlbums();
    } catch (Exception e) {
      allAlbums = List.of();
    }

    // Use a smaller icon (48×48 instead of 72×72) so the header occupies less
    // vertical space and gives the album grid more room.
    ImageIcon allAlbumsIcon = imageLoader.loadImage("AllAlbumsLogo.png", 48, 48);
    DetailHeaderPanel header = new DetailHeaderPanel(null, null, allAlbumsIcon, "♫", "ALL ALBUMS",
        allAlbums.size() + " albums");
    header.setOpaque(false);
    // Match the 12px left/right padding used by AlbumGridPanel's gridPanel border.
    // Top/bottom padding is kept tight to minimise the header's height.
    header.setBorder(new javax.swing.border.EmptyBorder(4, 12, 4, 12));

    if (allAlbums.isEmpty()) {
      JLabel empty = new JLabel("No albums found.", SwingConstants.CENTER);
      empty.setForeground(AlbumGridPanel.TEXT_SECONDARY);
      empty.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
      card.add(header, BorderLayout.NORTH);
      card.add(empty, BorderLayout.CENTER);
      return card;
    }

    AlbumGridPanel grid = new AlbumGridPanel(allAlbums, imageLoader, gridCols, gridRows, artW, artH,
        album -> pushAlbumDetail(album)); // TabNavigator path

    card.add(header, BorderLayout.NORTH);
    card.add(grid, BorderLayout.CENTER);
    return card;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HELPERS
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Fetches the full {@link AlbumDto} (including the songs list) for the given album. Falls back to
   * the supplied stub if the service call fails.
   */
  private AlbumDto fetchFull(AlbumDto album) {
    try {
      return songLibraryService.getAlbumById(album.getAlbumId());
    } catch (Exception e) {
      return album;
    }
  }

  /**
   * Replaces the component registered under {@code name} in {@code rootPanel} with
   * {@code newPanel}, then revalidates the container.
   *
   * <p>
   * Using the component's {@link java.awt.Component#getName() name} property rather than a
   * positional index makes this robust against reordering and future card additions.
   */
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

  /** Minimal opaque placeholder used to seed card slots before real content arrives. */
  private JPanel placeholder() {
    JPanel p = new JPanel();
    p.setOpaque(false);
    return p;
  }
}
