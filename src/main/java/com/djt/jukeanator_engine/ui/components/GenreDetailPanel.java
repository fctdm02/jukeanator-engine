package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
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
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreTotalsDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

public class GenreDetailPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Sort mode ─────────────────────────────────────────────────────────────
  public enum SortMode {
    POPULARITY, TITLE
  }

  // ── Colours — sourced from ColorTheme.get() ──────────────────────────────

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
  private final PagedCategoryBuffer<ArtistDto> artistBuffer;
  private final PagedCategoryBuffer<AlbumDto> albumBuffer;
  private final PagedCategoryBuffer<SongDto> songBuffer;

  // ── Dependencies needed for row-click handling ────────────────────────────
  private final ImageLoader imageLoader;
  private final AlbumGridPanel.AlbumClickListener onAlbumClicked;
  private final ArtistClickListener onArtistClicked;
  private final SongLibraryService songLibraryService;

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
      String backLabel, Runnable onBack, AlbumGridPanel.AlbumClickListener onAlbumClicked,
      ArtistClickListener onArtistClicked, SongLibraryService songLibraryService) {

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);

    this.genre = genre;
    this.imageLoader = imageLoader;
    this.onAlbumClicked = onAlbumClicked;
    this.onArtistClicked = onArtistClicked;
    this.songLibraryService = songLibraryService;

    SearchResultDto safe = results != null ? results : new SearchResultDto(List.of(), List.of(), List.of());
    int serverPageSize = songLibraryService.getSearchResultPageSize();
    this.artistBuffer = new PagedCategoryBuffer<>(serverPageSize);
    this.albumBuffer = new PagedCategoryBuffer<>(serverPageSize);
    this.songBuffer = new PagedCategoryBuffer<>(serverPageSize);
    this.artistBuffer.seedFirstPage(safeList(safe.artists()));
    this.albumBuffer.seedFirstPage(safeList(safe.albums()));
    this.songBuffer.seedFirstPage(safeList(safe.songs()));

    // ── Header ────────────────────────────────────────────────────────────
    ImageIcon genreImage = null;
    try {
      String resourceName = genre.genreName() + ".png";
      int imgW = LayoutTheme.get().detailHeaderImageW;
      int imgH = LayoutTheme.get().detailHeaderImageH;
      genreImage = imageLoader.loadImageFromDataDir(resourceName, imgW, imgH);
      if (genreImage == null) {
        genreImage = imageLoader.loadImage(resourceName, imgW, imgH);
      }
      if (genreImage != null) {
        java.awt.Image transparentStrippedImage =
            ImageLoader.createTransparentImage(genreImage.getImage(), true, 245);
        genreImage = new ImageIcon(transparentStrippedImage);
      }
    } catch (Exception ignored) {
    }

    // Totals reflect the true count of everything in this genre, independent of sort order and
    // of how much has been paged through so far -- fetched once since genre membership doesn't
    // change when the user toggles Popularity/Album sort.
    String subtitle = null;
    try {
      GenreTotalsDto totals =
          songLibraryService.getGenreTotals(songLibraryService.getOwnLocationId(), genre.genreName());
      subtitle = String.format("%,d artists  •  %,d albums  •  %,d songs", totals.numArtists(),
          totals.numAlbums(), totals.numSongs());
    } catch (Exception ignored) {
    }

    headerPanel = new DetailHeaderPanel(backLabel, onBack, genreImage, "♪", genre.genreName(),
        subtitle, buildSortButtonPanel());
    headerPanel.setOpaque(false);
    // Matches the border used by HomePanel's and HotHerePanel's headers so all three
    // detail-header panels render at the same height.
    int hbH = LayoutTheme.get().homeHeaderBorderH;
    headerPanel.setBorder(new javax.swing.border.EmptyBorder(4, hbH, 4, hbH));
    add(headerPanel, BorderLayout.NORTH);

    // ── Columns ───────────────────────────────────────────────────────────
    columnsPanel.setOpaque(false);
    // Each ResultsColumnPanel adds its own resultColumnPadH on both edges (for the
    // gap between adjacent columns). Applying a matching negative margin here cancels
    // that out on the outermost left/right edges only, so the first/last columns sit
    // flush with the screen edges — mirroring the technique SearchPanel uses for its
    // results columns (see SearchPanel#rebuildResultsCard's unifiedPaddingCalculation).
    int edgeOffset = -LayoutTheme.get().resultColumnPadH;
    columnsPanel.setBorder(new javax.swing.border.EmptyBorder(0, edgeOffset, 0, edgeOffset));
    add(columnsPanel, BorderLayout.CENTER);

    rebuildColumns();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // SORT TOGGLE BUTTONS
  // ─────────────────────────────────────────────────────────────────────────

  /** Holds references to the three sort buttons so we can repaint active state. */
  private JButton btnPopularity;
  private JButton btnTitle;

  private JPanel buildSortButtonPanel() {

    JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
    row.setOpaque(false);
    row.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 8));

    JLabel sortLabel = new JLabel("Sort By: ");
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
    // Previously: new Dimension(170, 42) — hard-coded.
    // Now sourced from LayoutTheme so a scaled theme can adjust button proportions.
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

  private void applySortMode(SortMode mode) {

    if (mode == currentSort)
      return;
    currentSort = mode;

    try {
      SearchResultDto fresh = switch (mode) {
        case POPULARITY -> songLibraryService.getGenreMusicByPopularity(
            songLibraryService.getOwnLocationId(), genre.genreName(), 0, 0, 0);
        case TITLE -> songLibraryService.getGenreMusicByTitle(songLibraryService.getOwnLocationId(),
            genre.genreName(), 0, 0, 0);
      };
      if (fresh == null)
        fresh = new SearchResultDto(List.of(), List.of(), List.of());
      artistBuffer.seedFirstPage(safeList(fresh.artists()));
      albumBuffer.seedFirstPage(safeList(fresh.albums()));
      songBuffer.seedFirstPage(safeList(fresh.songs()));
    } catch (Exception ignored) {
    }

    artistsOffset = 0;
    albumsOffset = 0;
    songsOffset = 0;

    for (JButton btn : new JButton[] {btnPopularity, btnTitle}) {
      if (btn != null) {
        btn.setForeground(ColorTheme.get().textMuted);
        btn.repaint();
      }
    }
    JButton activeBtn = switch (mode) {
      case POPULARITY -> btnPopularity;
      case TITLE -> btnTitle;
    };
    if (activeBtn != null) {
      activeBtn.setForeground(ColorTheme.get().textPrimary);
      activeBtn.repaint();
    }

    rebuildColumns();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // COLUMN RENDERING
  // ─────────────────────────────────────────────────────────────────────────
  private void rebuildColumns() {

    // Previously: PREVIEW_COUNT was a local static constant (9).
    // Now: sourced from LayoutTheme.get().genreDetailPreviewCount so it can be
    // tuned per resolution (more rows fit on a taller / higher-DPI screen).
    final int previewCount = LayoutTheme.get().genreDetailPreviewCount;
    final Integer locationId = songLibraryService.getOwnLocationId();
    final String genreName = genre.genreName();

    // A transient service failure here should not corrupt a buffer's exhausted/next-page
    // bookkeeping -- just leave it as whatever was already fetched and let the next page-down
    // attempt retry.
    try {
      artistBuffer.ensureWindowAvailable(artistsOffset, previewCount,
          pageIdx -> (currentSort == SortMode.POPULARITY
              ? songLibraryService.getGenreMusicByPopularity(locationId, genreName, pageIdx, 0, 0)
              : songLibraryService.getGenreMusicByTitle(locationId, genreName, pageIdx, 0, 0))
                  .artists());

      albumBuffer.ensureWindowAvailable(albumsOffset, previewCount,
          pageIdx -> (currentSort == SortMode.POPULARITY
              ? songLibraryService.getGenreMusicByPopularity(locationId, genreName, 0, pageIdx, 0)
              : songLibraryService.getGenreMusicByTitle(locationId, genreName, 0, pageIdx, 0))
                  .albums());

      songBuffer.ensureWindowAvailable(songsOffset, previewCount,
          pageIdx -> (currentSort == SortMode.POPULARITY
              ? songLibraryService.getGenreMusicByPopularity(locationId, genreName, 0, 0, pageIdx)
              : songLibraryService.getGenreMusicByTitle(locationId, genreName, 0, 0, pageIdx))
                  .songs());
    } catch (Exception ignored) {
    }

    columnsPanel.removeAll();

    columnsPanel.add(ResultsColumnPanel.build("ARTISTS", artistBuffer.items(), artistsOffset,
        previewCount, imageLoader, newOffset -> {
          artistsOffset = newOffset;
          rebuildColumns();
        }, item -> handleRowClick("ARTISTS", item)));

    columnsPanel.add(ResultsColumnPanel.build("ALBUMS", albumBuffer.items(), albumsOffset,
        previewCount, imageLoader, newOffset -> {
          albumsOffset = newOffset;
          rebuildColumns();
        }, item -> handleRowClick("ALBUMS", item)));

    columnsPanel.add(ResultsColumnPanel.build("SONGS", songBuffer.items(), songsOffset,
        previewCount, imageLoader, newOffset -> {
          songsOffset = newOffset;
          rebuildColumns();
        }, item -> handleRowClick("SONGS", item)));

    columnsPanel.revalidate();
    columnsPanel.repaint();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ROW CLICK DISPATCH
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
          if (owner instanceof JukeANatorFrame frame) {
            frame.showAddSongToQueueCard(song);
          }
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
