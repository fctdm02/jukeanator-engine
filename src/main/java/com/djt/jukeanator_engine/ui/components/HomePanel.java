package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  // public static final int DEFAULT_COLS = 4;
  // public static final int DEFAULT_ROWS = 3;
  // public static final int DEFAULT_ART_W = 190;
  // public static final int DEFAULT_ART_H = 190;

  // ── Card names ────────────────────────────────────────────────────────────
  private static final String CARD_GRID = "GRID";
  private static final String CARD_ARTIST = "ARTIST";
  private static final String CARD_DETAIL = "DETAIL";

  // ── Layout ────────────────────────────────────────────────────────────────
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel rootPanel = new JPanel(cardLayout);

  // ── Active detail card (tracked so its timer can be stopped) ─────────────
  private AlbumDetailCard currentDetailCard;

  // ── Tracks which card to return to when the detail card's BACK button is
  // pressed — CARD_GRID if the album was opened from the root grid, CARD_ARTIST
  // if it was opened from the artist detail panel. ───────────────────────────
  private String detailReturnCard = CARD_GRID;

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
  public HomePanel(char incrementCreditsKey, CreditManager creditManager,
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

    rootPanel.setOpaque(false);
    add(rootPanel, BorderLayout.CENTER);

    // Seed the three cards. ARTIST and DETAIL start as empty placeholders;
    // real content is swapped in on demand via replaceCard().
    JPanel gridCard = buildGridCard();
    gridCard.setName(CARD_GRID);
    rootPanel.add(gridCard, CARD_GRID);

    JPanel artistPlaceholder = placeholder();
    artistPlaceholder.setName(CARD_ARTIST);
    rootPanel.add(artistPlaceholder, CARD_ARTIST);

    JPanel detailPlaceholder = placeholder();
    detailPlaceholder.setName(CARD_DETAIL);
    rootPanel.add(detailPlaceholder, CARD_DETAIL);

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

    // Remember which card was visible before navigating to the detail card so
    // the BACK button can return the user to the correct screen (the album
    // grid or the artist detail panel).
    detailReturnCard = currentVisibleCard();

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
   * Stops the detail card's countdown timer and returns to the previously visible card (either the
   * root grid or the artist detail panel). Safe to call even when no detail card is currently
   * active.
   */
  @Override
  public void popToRoot() {

    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
      currentDetailCard = null;
    }
    cardLayout.show(rootPanel, detailReturnCard);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ARTIST CARD
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigate to the artist detail view within the Home tab. Can be called from outside (e.g. a
   * future "Featured Artists" section on the Home tab).
   */
  public void showArtist(ArtistDto artist) {

    ArtistDetailPanel artistPanel = new ArtistDetailPanel(artist, imageLoader, albumGridProfile,
        "← HOME", () -> cardLayout.show(rootPanel, CARD_GRID), album -> pushAlbumDetail(album)); // reuse

    replaceCard(CARD_ARTIST, artistPanel);
    cardLayout.show(rootPanel, CARD_ARTIST);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // GRID CARD
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildGridCard() {

    JPanel card = new JPanel(new BorderLayout());
    card.setOpaque(false);

    List<AlbumDto> rawAlbums;
    try {
      rawAlbums = songLibraryService.getAlbums();
    } catch (Exception e) {
      rawAlbums = List.of();
    }

    // ── Item #1: Sort albums alphabetically by name, symbols/numbers first ──
    List<AlbumDto> allAlbums = new ArrayList<>(rawAlbums);
    allAlbums.sort(Comparator.comparing(a -> {
      String name = a.getAlbumName();
      if (name == null || name.isBlank())
        return "\uFFFF"; // push blanks to end
      char first = Character.toUpperCase(name.charAt(0));
      // Letters sort after symbols/digits by prefixing letters with '~'
      // so that symbol/digit names sort before A–Z naturally.
      return Character.isLetter(first) ? ("~" + name.toUpperCase()) : name.toUpperCase();
    }));

    // ── Item #2: Build ordered letter→albums map (#, A–Z) ───────────────────
    Map<String, List<AlbumDto>> letterMap = buildLetterMap(allAlbums);

    // Icon size is read from LayoutTheme so that the header shrinks on small-landscape
    // displays (1024x768) where detailHeaderImageW/H are reduced to 40px, keeping the
    // header compact and giving the album grid more vertical room.
    int headerIconSize = LayoutTheme.get().detailHeaderImageW;
    ImageIcon allAlbumsIcon =
        imageLoader.loadImage("AllAlbumsLogo.png", headerIconSize, headerIconSize);
    DetailHeaderPanel header = new DetailHeaderPanel(null, null, allAlbumsIcon, "♫", "ALL ALBUMS",
        allAlbums.size() + " albums");
    header.setOpaque(false);
    // Left/right padding matches the album grid's own horizontal border so the header
    // icon/text aligns on the y-axis with the tile columns below it.
    // homeHeaderBorderH is 12px by default (matches gridPanel's EmptyBorder(8,12,4,12))
    // and 20px for small-landscape (1024x768) where additional correction is needed.
    int hbH = LayoutTheme.get().homeHeaderBorderH;
    header.setBorder(new javax.swing.border.EmptyBorder(4, hbH, 4, hbH));

    if (allAlbums.isEmpty()) {
      JLabel empty = new JLabel("No albums found.", SwingConstants.CENTER);
      empty.setForeground(ColorTheme.get().textSecondary);
      empty.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
      card.add(header, BorderLayout.NORTH);
      card.add(empty, BorderLayout.CENTER);
      return card;
    }

    AlbumGridPanel grid = new AlbumGridPanel(allAlbums, letterMap, imageLoader, albumGridProfile,
        album -> pushAlbumDetail(album), true); // TabNavigator path

    card.add(header, BorderLayout.NORTH);
    card.add(grid, BorderLayout.CENTER);
    return card;
  }

  /**
   * Builds an ordered map whose keys are "#" (numbers/symbols) followed by "A"–"Z", and whose
   * values are the albums whose names start with that key. Keys with no matching albums are
   * omitted.
   *
   * <p>
   * The input list must already be sorted in the desired display order.
   */
  private Map<String, List<AlbumDto>> buildLetterMap(List<AlbumDto> sortedAlbums) {

    // Pre-seed all keys in order so iteration order is always #, A, B, …, Z.
    Map<String, List<AlbumDto>> map = new LinkedHashMap<>();
    map.put("#", new ArrayList<>());
    for (char c = 'A'; c <= 'Z'; c++) {
      map.put(String.valueOf(c), new ArrayList<>());
    }

    for (AlbumDto album : sortedAlbums) {
      String name = album.getAlbumName();
      if (name == null || name.isBlank()) {
        map.get("#").add(album);
        continue;
      }
      char first = Character.toUpperCase(name.charAt(0));
      if (Character.isLetter(first)) {
        map.get(String.valueOf(first)).add(album);
      } else {
        map.get("#").add(album);
      }
    }

    // Remove any bucket that ended up empty so the nav bar only shows real letters.
    map.entrySet().removeIf(e -> e.getValue().isEmpty());
    return map;
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

  /**
   * Returns the name of the card currently visible in {@code rootPanel}, falling back to
   * {@code CARD_GRID} if none is marked visible (e.g. before the first layout pass).
   */
  private String currentVisibleCard() {
    for (java.awt.Component c : rootPanel.getComponents()) {
      if (c.isVisible()) {
        return c.getName();
      }
    }
    return CARD_GRID;
  }

  /** Minimal opaque placeholder used to seed card slots before real content arrives. */
  private JPanel placeholder() {
    JPanel p = new JPanel();
    p.setOpaque(false);
    return p;
  }
}
