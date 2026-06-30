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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
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

  // ── Sort mode ─────────────────────────────────────────────────────────────
  public enum SortMode {
    TITLE, ARTIST
  }

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

  // ── Active detail card ──────────────────────────────────────────────────
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

  // ── Albums, fetched ONCE at startup and kept in memory in two sorted views.
  // Switching the "Order By" selection never re-queries SongLibraryService —
  // it only swaps which of these already-sorted lists/letter-maps is shown. ──
  private List<AlbumDto> albumsByTitle = List.of();
  private List<AlbumDto> albumsByArtist = List.of();
  private Map<String, List<AlbumDto>> letterMapByTitle = Map.of();
  private Map<String, List<AlbumDto>> letterMapByArtist = Map.of();
  private SortMode currentSort = SortMode.ARTIST;

  // ── Live grid container (the AlbumGridPanel inside is swapped on sort change) ──
  private final JPanel gridContainer = new JPanel(new BorderLayout());

  // ── The AlbumGridPanel currently shown in gridContainer — tracked so its selected
  // letter/page can be carried over when the sort order is toggled. ──────────
  private AlbumGridPanel currentGridPanel;

  // ── Sort toggle buttons — kept to allow repainting active state ──────────
  private JButton btnTitle;
  private JButton btnArtist;

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
   * card slot, and flips to it.
   */
  @Override
  public void pushAlbumDetail(AlbumDto album) {

    Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);

    AlbumDto full = fetchFull(album);

    // Remember which card was visible before navigating to the detail card so
    // the BACK button can return the user to the correct screen (the album
    // grid or the artist detail panel).
    detailReturnCard = currentVisibleCard();

    currentDetailCard =
        new AlbumDetailCard(owner, full, imageLoader, songQueueService, priorityCostMultiplier,
            popularityT1, popularityT2, popularityT3, this, creditManager, incrementCreditsKey); // TabNavigator
                                                                                                 // back-reference

    replaceCard(CARD_DETAIL, currentDetailCard);
    cardLayout.show(rootPanel, CARD_DETAIL);
  }

  /**
   * Returns to the previously visible card (either the root grid or the artist detail panel).
   */
  @Override
  public void popToRoot() {

    currentDetailCard = null;
    cardLayout.show(rootPanel, detailReturnCard);
  }

  /**
   * Resets the Home tab to the root album grid. Unlike the other tabs' resetToDefaultView(), this
   * does not reset the page or selected letter — it shows the grid exactly as the user left it,
   * at whatever page/letter and sort order were last active.
   */
  public void resetToDefaultView() {
    currentDetailCard = null;
    detailReturnCard = CARD_GRID;
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

    // Fetch the album list ONCE. All sort-order switching below operates
    // purely on the two in-memory lists built from this single call.
    List<AlbumDto> rawAlbums;
    try {
      rawAlbums = songLibraryService.getAlbums();
    } catch (Exception e) {
      rawAlbums = List.of();
    }

    // Both lists are sorted explicitly — do not rely on the service's return order.
    // The letter map for each view must be built from its own sorted list so that
    // the indices stored in the map align exactly with the positions in the list
    // that AlbumGridPanel will paginate through.
    albumsByTitle = sortAlbums(rawAlbums, AlbumDto::getAlbumName);
    letterMapByTitle = buildLetterMap(albumsByTitle, AlbumDto::getAlbumName);

    albumsByArtist = sortAlbums(rawAlbums, AlbumDto::getArtistName);
    letterMapByArtist = buildLetterMap(albumsByArtist, AlbumDto::getArtistName);

    // Icon size is read from LayoutTheme so that the header shrinks on small-landscape
    // displays (1024x768) where detailHeaderImageW/H are reduced to 40px, keeping the
    // header compact and giving the album grid more vertical room.
    int headerIconSize = LayoutTheme.get().detailHeaderImageW;
    ImageIcon allAlbumsIcon =
        imageLoader.loadImage("AllAlbumsLogo.png", headerIconSize, headerIconSize);

    // Artist/song totals are static for the lifetime of this panel, so they are
    // fetched ONCE here rather than recomputed on every sort toggle/header rebuild.
    int artistCount;
    int songCount;
    try {
      com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto allMusic =
          songLibraryService.getMusicByPopularity();
      artistCount = allMusic != null && allMusic.getArtists() != null
          ? allMusic.getArtists().size()
          : 0;
      songCount =
          allMusic != null && allMusic.getSongs() != null ? allMusic.getSongs().size() : 0;
    } catch (Exception e) {
      artistCount = 0;
      songCount = 0;
    }

    String subtitle = artistCount + " artists  •  " + albumsByTitle.size() + " albums  •  "
        + songCount + " songs";

    DetailHeaderPanel header = new DetailHeaderPanel(null, null, allAlbumsIcon, "♫", "All Albums",
        subtitle, buildSortButtonPanel());
    header.setOpaque(false);
    // Left/right padding matches the album grid's own horizontal border so the header
    // icon/text aligns on the y-axis with the tile columns below it.
    // homeHeaderBorderH is 12px by default (matches gridPanel's EmptyBorder(8,12,4,12))
    // and 20px for small-landscape (1024x768) where additional correction is needed.
    int hbH = LayoutTheme.get().homeHeaderBorderH;
    header.setBorder(new javax.swing.border.EmptyBorder(4, hbH, 4, hbH));

    card.add(header, BorderLayout.NORTH);

    if (albumsByTitle.isEmpty()) {
      JLabel empty = new JLabel("No albums found.", SwingConstants.CENTER);
      empty.setForeground(ColorTheme.get().textSecondary);
      empty.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
      card.add(empty, BorderLayout.CENTER);
      return card;
    }

    gridContainer.setOpaque(false);
    currentGridPanel = buildAlbumGridPanel(currentSort);
    gridContainer.add(currentGridPanel, BorderLayout.CENTER);
    card.add(gridContainer, BorderLayout.CENTER);
    return card;
  }

  /** Builds an {@link AlbumGridPanel} bound to the already-sorted list/letter-map for {@code mode}. */
  private AlbumGridPanel buildAlbumGridPanel(SortMode mode) {
    List<AlbumDto> albums = mode == SortMode.TITLE ? albumsByTitle : albumsByArtist;
    Map<String, List<AlbumDto>> letterMap =
        mode == SortMode.TITLE ? letterMapByTitle : letterMapByArtist;
    // Pass the same key extractor that was used to build the letter map so that
    // letterForIndex() highlights the correct letter when ❮/❯ paginate across buckets.
    java.util.function.Function<AlbumDto, String> keyExtractor =
        mode == SortMode.TITLE ? AlbumDto::getAlbumName : AlbumDto::getArtistName;
    return new AlbumGridPanel(albums, letterMap, imageLoader, albumGridProfile,
        album -> pushAlbumDetail(album), true, keyExtractor);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // ORDER BY TOGGLE BUTTONS
  // ─────────────────────────────────────────────────────────────────────────────
  private JPanel buildSortButtonPanel() {

    JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
    row.setOpaque(false);
    row.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 8));

    JLabel sortLabel = new JLabel("Order By: ");
    sortLabel.setForeground(ColorTheme.get().textPrimary);
    sortLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeSortLabel));
    row.add(sortLabel);

    btnArtist = sortButton("Artist", SortMode.ARTIST);
    btnTitle = sortButton("Title", SortMode.TITLE);

    row.add(btnArtist);
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
   * Switches the displayed order between title and artist. Never re-queries
   * {@link SongLibraryService} -- both lists/letter-maps were built once at startup in
   * {@link #buildGridCard()}; this only swaps which already-sorted view is shown.
   */
  private void applySortMode(SortMode mode) {

    if (mode == currentSort)
      return;

    // Capture the currently selected letter/page so the user lands on the same
    // letter and relative page after the grid is rebuilt under the new sort order.
    String selectedLetter = currentGridPanel != null ? currentGridPanel.getSelectedLetter() : "#";
    int pageOffset = currentGridPanel != null ? currentGridPanel.getPageOffsetWithinLetter() : 0;

    currentSort = mode;

    for (JButton btn : new JButton[] {btnTitle, btnArtist}) {
      if (btn != null) {
        btn.setForeground(ColorTheme.get().textMuted);
        btn.repaint();
      }
    }
    JButton activeBtn = mode == SortMode.TITLE ? btnTitle : btnArtist;
    if (activeBtn != null) {
      activeBtn.setForeground(ColorTheme.get().textPrimary);
      activeBtn.repaint();
    }

    currentGridPanel = buildAlbumGridPanel(mode);
    currentGridPanel.selectLetterAtPage(selectedLetter, pageOffset);

    gridContainer.removeAll();
    gridContainer.add(currentGridPanel, BorderLayout.CENTER);
    gridContainer.revalidate();
    gridContainer.repaint();
  }

  /**
   * Returns a new list of {@code rawAlbums} sorted alphabetically by the value returned by
   * {@code keyExtractor}, symbols/numbers first. Blank/null keys sort to the end.
   */
  private List<AlbumDto> sortAlbums(List<AlbumDto> rawAlbums,
      Function<AlbumDto, String> keyExtractor) {
    List<AlbumDto> sorted = new ArrayList<>(rawAlbums);
    sorted.sort(Comparator.comparing(a -> {
      String key = keyExtractor.apply(a);
      if (key == null || key.isBlank())
        return "￿"; // push blanks to end
      char first = Character.toUpperCase(key.charAt(0));
      // Letters sort after symbols/digits by prefixing letters with '~'
      // so that symbol/digit names sort before A-Z naturally.
      return Character.isLetter(first) ? ("~" + key.toUpperCase()) : key.toUpperCase();
    }));
    return sorted;
  }

  /**
   * Builds an ordered map whose keys are "#" (numbers/symbols) followed by "A"-"Z", and whose
   * values are the albums whose {@code keyExtractor} value starts with that key. Keys with no
   * matching albums are omitted.
   *
   * <p>
   * The input list must already be sorted in the desired display order.
   */
  private Map<String, List<AlbumDto>> buildLetterMap(List<AlbumDto> sortedAlbums,
      Function<AlbumDto, String> keyExtractor) {

    // Pre-seed all keys in order so iteration order is always #, A, B, ..., Z.
    Map<String, List<AlbumDto>> map = new LinkedHashMap<>();
    map.put("#", new ArrayList<>());
    for (char c = 'A'; c <= 'Z'; c++) {
      map.put(String.valueOf(c), new ArrayList<>());
    }

    for (AlbumDto album : sortedAlbums) {
      String key = keyExtractor.apply(album);
      if (key == null || key.isBlank()) {
        map.get("#").add(album);
        continue;
      }
      char first = Character.toUpperCase(key.charAt(0));
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
