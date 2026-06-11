package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.model.CreditManager;

public class GenreDetailPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Sort mode ─────────────────────────────────────────────────────────────
  public enum SortMode {
    POPULARITY, TITLE, RELEASE_DATE
  }

  // ── Sort-button palette ───────────────────────────────────────────────────
  private static final Color SORT_BTN_ACTIVE_TOP = new Color(0, 160, 210);
  private static final Color SORT_BTN_ACTIVE_BOTTOM = new Color(0, 80, 130);
  private static final Color SORT_BTN_IDLE_BG = new Color(28, 28, 42);
  private static final Color SORT_BTN_BORDER_ACTIVE = new Color(0, 210, 255);
  private static final Color SORT_BTN_BORDER_IDLE = new Color(60, 60, 80);
  private static final Color SORT_TEXT_ACTIVE = Color.WHITE;
  private static final Color SORT_TEXT_IDLE = new Color(160, 165, 180);

  // ── Preview row count (matches HotHerePanel) ──────────────────────────────
  // Number of result rows visible at one time in each column.
  // Tune this value if the screen resolution changes the visible row count.
  private static final int PREVIEW_COUNT = 9;

  // ── Offset state per column ───────────────────────────────────────────────
  private int artistsOffset = 0;
  private int albumsOffset = 0;
  private int songsOffset = 0;

  // ── Header (kept to allow subtitle refresh) ───────────────────────────────
  private DetailHeaderPanel headerPanel;

  // ── Live column container (rebuilt on nav) ────────────────────────────────
  private final JPanel columnsPanel = new JPanel(new GridLayout(1, 3, 2, 0));

  // ── Data ──────────────────────────────────────────────────────────────────
  private final GenreDto genre;
  private List<ArtistDto> artists;
  private List<AlbumDto> albums;
  private List<SongDto> songs;

  // ── Dependencies needed for row-click handling ────────────────────────────
  private final ImageLoader imageLoader;
  private final SongQueueService songQueueService;
  private final int priorityCostMultiplier;
  private final AlbumGridPanel.AlbumClickListener onAlbumClicked;
  private final ArtistClickListener onArtistClicked;
  private final SongLibraryService songLibraryService;
  private final CreditManager creditManager;
  private final char incrementCreditsKey;

  // ── Current sort state ────────────────────────────────────────────────────
  private SortMode currentSort = SortMode.POPULARITY;

  // ── Callback types ────────────────────────────────────────────────────────
  public interface ArtistClickListener {
    void onArtistClicked(ArtistDto artist);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public GenreDetailPanel(GenreDto genre, SearchResultDto results, ImageLoader imageLoader,
      SongQueueService songQueueService, int priorityCostMultiplier, String backLabel,
      Runnable onBack, AlbumGridPanel.AlbumClickListener onAlbumClicked,
      ArtistClickListener onArtistClicked, SongLibraryService songLibraryService,
      CreditManager creditManager, char incrementCreditsKey) {

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);

    this.genre = genre;
    this.imageLoader = imageLoader;
    this.songQueueService = songQueueService;
    this.priorityCostMultiplier = priorityCostMultiplier;
    this.onAlbumClicked = onAlbumClicked;
    this.onArtistClicked = onArtistClicked;
    this.songLibraryService = songLibraryService;
    this.creditManager = creditManager;
    this.incrementCreditsKey = incrementCreditsKey;

    SearchResultDto safe = results != null ? results : new SearchResultDto();
    this.artists = safeList(safe.getArtists());
    this.albums = safeList(safe.getAlbums());
    this.songs = safeList(safe.getSongs());

    // ── Header ────────────────────────────────────────────────────────────
    ImageIcon genreImage = null;
    try {
      String resourceName = genre.getGenreName() + ".png";
      genreImage = imageLoader.loadImage(resourceName, 72, 72);
    } catch (Exception ignored) {
    }

    // Subtitle: total counts across all three categories
    String subtitle =
        artists.size() + " artists  •  " + albums.size() + " albums  •  " + songs.size() + " songs";

    headerPanel = new DetailHeaderPanel(backLabel, onBack, genreImage, "♪", genre.getGenreName(),
        subtitle, buildSortButtonPanel());
    headerPanel.setOpaque(false);
    add(headerPanel, BorderLayout.NORTH);

    // ── Columns ───────────────────────────────────────────────────────────
    columnsPanel.setOpaque(false);
    add(columnsPanel, BorderLayout.CENTER);

    rebuildColumns();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // SORT TOGGLE BUTTONS
  // ─────────────────────────────────────────────────────────────────────────

  /** Holds references to the three sort buttons so we can repaint active state. */
  private JButton btnPopularity;
  private JButton btnTitle;
  private JButton btnReleaseDate;

  private JPanel buildSortButtonPanel() {
    // Row of label + buttons, sized to its natural height
    JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
    row.setOpaque(false);
    row.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 8));

    JLabel sortLabel = new JLabel("Sort By: ");
    sortLabel.setForeground(AlbumGridPanel.TEXT_PRIMARY);
    sortLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
    row.add(sortLabel);

    btnPopularity = sortButton("Popularity", SortMode.POPULARITY);
    btnTitle = sortButton("Title", SortMode.TITLE);
    btnReleaseDate = sortButton("Release Date", SortMode.RELEASE_DATE);

    row.add(btnPopularity);
    row.add(btnTitle);
    row.add(btnReleaseDate);

    // BoxLayout Y_AXIS wrapper with glue above and below pushes the row
    // to the vertical centre regardless of how tall the EAST slot grows.
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
          // Blue gradient fill matching the column panel gradient accent
          g2.setPaint(
              new GradientPaint(0, 0, SORT_BTN_ACTIVE_TOP, 0, getHeight(), SORT_BTN_ACTIVE_BOTTOM));
        } else {
          g2.setColor(SORT_BTN_IDLE_BG);
        }
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

        // Border
        g2.setColor(active ? SORT_BTN_BORDER_ACTIVE : SORT_BTN_BORDER_IDLE);
        g2.setStroke(new java.awt.BasicStroke(active ? 1.5f : 1.0f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        g2.dispose();
        super.paintComponent(g);
      }
    };

    btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    btn.setForeground(currentSort == mode ? SORT_TEXT_ACTIVE : SORT_TEXT_IDLE);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setOpaque(false);
    btn.setPreferredSize(new Dimension(170, 42));
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

  private void applySortMode(SortMode mode) {
    if (mode == currentSort)
      return;
    currentSort = mode;

    // Reload data from service
    try {
      SearchResultDto fresh = switch (mode) {
        case POPULARITY -> songLibraryService.getGenreMusicByPopularity(genre.getGenreName());
        case TITLE -> songLibraryService.getGenreMusicByTitle(genre.getGenreName());
        case RELEASE_DATE -> songLibraryService.getGenreMusicByReleaseDate(genre.getGenreName());
      };
      if (fresh == null)
        fresh = new SearchResultDto();
      artists = safeList(fresh.getArtists());
      albums = safeList(fresh.getAlbums());
      songs = safeList(fresh.getSongs());
    } catch (Exception ignored) {
    }

    // Reset offsets so we start at the top of the new ordering
    artistsOffset = 0;
    albumsOffset = 0;
    songsOffset = 0;

    // Refresh active-state colours on the three toggle buttons
    for (JButton btn : new JButton[] {btnPopularity, btnTitle, btnReleaseDate}) {
      if (btn != null) {
        btn.setForeground(SORT_TEXT_IDLE);
        btn.repaint();
      }
    }
    JButton activeBtn = switch (mode) {
      case POPULARITY -> btnPopularity;
      case TITLE -> btnTitle;
      case RELEASE_DATE -> btnReleaseDate;
    };
    if (activeBtn != null) {
      activeBtn.setForeground(SORT_TEXT_ACTIVE);
      activeBtn.repaint();
    }

    rebuildColumns();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // COLUMN RENDERING (mirrors HotHerePanel#rebuildColumnsPanel)
  // ─────────────────────────────────────────────────────────────────────────
  private void rebuildColumns() {

    columnsPanel.removeAll();

    columnsPanel.add(ResultsColumnPanel.build("ARTISTS", artists, artistsOffset, PREVIEW_COUNT,
        imageLoader, newOffset -> {
          artistsOffset = newOffset;
          rebuildColumns();
        }, item -> handleRowClick("ARTISTS", item)));

    columnsPanel.add(ResultsColumnPanel.build("ALBUMS", albums, albumsOffset, PREVIEW_COUNT,
        imageLoader, newOffset -> {
          albumsOffset = newOffset;
          rebuildColumns();
        }, item -> handleRowClick("ALBUMS", item)));

    columnsPanel.add(ResultsColumnPanel.build("SONGS", songs, songsOffset, PREVIEW_COUNT,
        imageLoader, newOffset -> {
          songsOffset = newOffset;
          rebuildColumns();
        }, item -> handleRowClick("SONGS", item)));

    columnsPanel.revalidate();
    columnsPanel.repaint();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ROW CLICK DISPATCH (mirrors HotHerePanel#handleRowClick)
  // ─────────────────────────────────────────────────────────────────────────
  private <T> void handleRowClick(String category, T item) {
    switch (category) {
      case "ARTISTS" -> {
        if (item instanceof ArtistDto a && onArtistClicked != null)
          onArtistClicked.onArtistClicked(a);
      }
      case "ALBUMS" -> {
        if (item instanceof AlbumDto a && onAlbumClicked != null)
          onAlbumClicked.onAlbumClicked(a);
      }
      case "SONGS" -> {
        if (item instanceof SongDto song) {
          Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
          AddSongToQueuePanel.show(owner, song, imageLoader, priorityCostMultiplier,
              songQueueService, creditManager, incrementCreditsKey);
        }
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HELPERS
  // ─────────────────────────────────────────────────────────────────────────
  private static <T> List<T> safeList(List<T> list) {
    return list != null ? list : List.of();
  }
}
