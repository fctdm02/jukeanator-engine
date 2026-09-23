package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityService;

/**
 * The Home tab's "Legacy" view: a fixed grid of dual-pane album panels (cover art + a full,
 * scrollable track listing) so a song can be queued in a single tap directly from the grid,
 * instead of tapping into an album's detail card first. Reuses the same paging/letter-navigation
 * behavior as {@link AlbumGridPanel} via the shared {@link AlbumPager}; only the per-cell "tile"
 * differs.
 */
public class LegacyAlbumGridPanel extends JPanel implements AlbumGridView {

  private static final long serialVersionUID = 1L;

  /** Notified when a track row is tapped, so the caller can open the Add-to-Queue overlay. */
  public interface SongClickListener {
    void onSongClicked(SongDto song);
  }

  // ── State ─────────────────────────────────────────────────────────────────
  private final List<AlbumDto> albums;
  private final Map<String, List<AlbumDto>> letterMap;
  private final ImageLoader imageLoader;
  private final AlbumGridPanel.AlbumClickListener albumClickListener; // cover art tap → full album view
  private final SongClickListener songClickListener; // track row tap → queue directly
  private final LayoutTheme.LegacyGridProfile profile;
  private final AlbumPager pager;

  private final JPanel gridPanel = new JPanel();
  private final JPanel navPanel = new JPanel(new BorderLayout(8, 0));

  private final UserActivityService userActivityService;
  private final Integer locationId;

  // ── Popularity-bar thresholds (play counts), same as AlbumViewCard/AlbumDetailCard ──
  private final int popularityT1;
  private final int popularityT2;
  private final int popularityT3;

  // ── Currently-playing song (if any is visible on the current page, its row shows a play icon
  // instead of popularity bars). Updated live by HomePanel#setNowPlaying. ──────────────────────
  private SongDto nowPlayingSong;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public LegacyAlbumGridPanel(List<AlbumDto> albums, Map<String, List<AlbumDto>> letterMap,
      ImageLoader imageLoader, LayoutTheme.LegacyGridProfile profile,
      AlbumGridPanel.AlbumClickListener albumClickListener, SongClickListener songClickListener,
      Function<AlbumDto, String> letterKeyExtractor, UserActivityService userActivityService,
      Integer locationId, int popularityT1, int popularityT2, int popularityT3,
      SongDto nowPlayingSong) {

    this.albums = albums != null ? albums : List.of();
    this.letterMap = letterMap != null ? letterMap : Map.of();
    this.imageLoader = imageLoader;
    this.profile = profile;
    this.albumClickListener = albumClickListener;
    this.songClickListener = songClickListener;
    this.userActivityService = userActivityService;
    this.locationId = locationId;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;
    this.nowPlayingSong = nowPlayingSong;

    this.pager = new AlbumPager(this.albums, this.letterMap, letterKeyExtractor, true);

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);

    gridPanel.setOpaque(false);
    navPanel.setBorder(new EmptyBorder(4, 0, 4, 0));
    navPanel.setOpaque(false);
    int navH = LayoutTheme.get().navBtnH + 4 + 4;
    navPanel.setPreferredSize(new Dimension(0, navH));
    navPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, navH));

    add(gridPanel, BorderLayout.CENTER);
    add(navPanel, BorderLayout.SOUTH);

    refresh();
  }

  // ── Public API — mirrors AlbumGridPanel so HomePanel can carry the selected letter/page over
  // when the sort order or Legacy toggle changes ──────────────────────────────────────────────

  @Override
  public String getSelectedLetter() {
    return pager.selectedLetter();
  }

  @Override
  public int getPageOffsetWithinLetter() {
    return pager.pageOffsetWithinLetter(pageSize());
  }

  @Override
  public void selectLetterAtPage(String letter, int pageOffset) {
    pager.selectLetterAtPage(letter, pageOffset, pageSize());
    refresh();
  }

  /** Updates which song (if any) shows the play icon instead of popularity bars, and re-renders. */
  public void setNowPlaying(SongDto song) {
    nowPlayingSong = song;
    refresh();
  }

  private int pageSize() {
    return profile.cols() * profile.rows();
  }

  private void trackPageNavigation(String direction) {
    userActivityService.recordSwingActivity(locationId, UserActivityType.PAGE_NAVIGATION,
        Map.of("category", "LEGACY_ALBUMS", "direction", direction));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PAGE RENDERING
  // ─────────────────────────────────────────────────────────────────────────
  private void refresh() {

    int pageSize = pageSize();
    int total = albums.size();

    pager.clamp(total);

    int start = pager.startIndex();
    int end = Math.min(start + pageSize, total);

    boolean hasPrev = start > 0;
    boolean hasNext = end < total;

    // ── Grid ──────────────────────────────────────────────────────────────
    gridPanel.removeAll();
    gridPanel.setLayout(new GridLayout(profile.rows(), profile.cols(),
        LayoutTheme.get().albumGridGapH, LayoutTheme.get().albumGridGapV));
    gridPanel.setBorder(new EmptyBorder(8, 0, 4, 0));

    for (int i = start; i < end; i++) {
      gridPanel.add(buildCard(albums.get(i)));
    }

    int filled = end - start;
    for (int i = filled; i < pageSize; i++) {
      JPanel blank = new JPanel();
      blank.setOpaque(false);
      gridPanel.add(blank);
    }

    // ── Navigation ────────────────────────────────────────────────────────
    navPanel.removeAll();

    JButton prevBtn = ButtonFactory.createNavigationButton("❮");
    prevBtn.setVisible(hasPrev);
    prevBtn.addActionListener(e -> {
      trackPageNavigation("prev");
      pager.prevPage(pageSize);
      refresh();
    });

    JButton nextBtn = ButtonFactory.createNavigationButton("❯");
    nextBtn.setVisible(hasNext);
    nextBtn.addActionListener(e -> {
      trackPageNavigation("next");
      pager.nextPage(pageSize, total);
      refresh();
    });

    JPanel letterStrip = AlbumPager.buildLetterStrip(letterMap, pager.selectedLetter(), letter -> {
      pager.selectLetter(letter);
      refresh();
    });

    JPanel prevWrapper = new JPanel(new BorderLayout());
    prevWrapper.setOpaque(false);
    prevWrapper
        .setPreferredSize(new Dimension(LayoutTheme.get().navBtnW, LayoutTheme.get().navBtnH));
    prevWrapper.add(prevBtn, BorderLayout.CENTER);

    JPanel nextWrapper = new JPanel(new BorderLayout());
    nextWrapper.setOpaque(false);
    nextWrapper
        .setPreferredSize(new Dimension(LayoutTheme.get().navBtnW, LayoutTheme.get().navBtnH));
    nextWrapper.add(nextBtn, BorderLayout.CENTER);

    navPanel.add(prevWrapper, BorderLayout.WEST);
    navPanel.add(letterStrip, BorderLayout.CENTER);
    navPanel.add(nextWrapper, BorderLayout.EAST);
    navPanel.setVisible(!letterMap.isEmpty() || hasPrev || hasNext);

    gridPanel.revalidate();
    gridPanel.repaint();
    navPanel.revalidate();
    navPanel.repaint();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // DUAL-PANE ALBUM CARD
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildCard(AlbumDto album) {

    boolean explicit = Boolean.TRUE.equals(album.hasExplicit());
    Color borderColor = explicit ? ColorTheme.get().accentExplicit : ColorTheme.get().legacyPanelBorder;

    JPanel card = new JPanel(new BorderLayout(0, 0));
    card.setOpaque(true);
    card.setBackground(Color.BLACK);
    card.setBorder(BorderFactory.createLineBorder(borderColor, 2));

    // ── Title bar — solid fill matching the card border color (white, or red for explicit
    // albums) with black text, so it reads as one continuous colored frame around the card. ──
    JLabel title = new JLabel(
        (album.artistName() != null ? album.artistName() + " - " : "")
            + AlbumGridPanel.albumDisplayName(album.albumName(), album.genreName()));
    title.setOpaque(true);
    title.setBackground(borderColor);
    title.setForeground(Color.BLACK);
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, profile.headerFontSize()));
    title.setBorder(new EmptyBorder(2, 6, 2, 6));
    card.add(title, BorderLayout.NORTH);

    // ── Cover art column (WEST) — art panel, stretched edge-to-edge to fill whatever space
    // it's given, plus an optional publisher/release-date strip below it. A matte border on
    // the column's right edge separates it from the track listing. ────────────────────────
    JPanel artColumn = new JPanel(new BorderLayout(0, 0));
    artColumn.setOpaque(false);
    artColumn.setPreferredSize(new Dimension(profile.coverSize(), 0));
    artColumn.setBorder(
        BorderFactory.createMatteBorder(0, 0, 0, 2, ColorTheme.get().colorSeparator));

    ImageIcon coverIcon = null;
    if (album.coverArtPath() != null) {
      try {
        coverIcon = imageLoader.loadFilesystemImage(album.coverArtPath(), profile.coverSize(),
            profile.coverSize());
      } catch (Exception ignored) {
      }
    }

    JPanel artPanel = buildCoverArtPanel(coverIcon);
    artPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    artPanel.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (albumClickListener != null)
          albumClickListener.onAlbumClicked(album);
      }
    });
    artColumn.add(artPanel, BorderLayout.CENTER);

    String publisherLine = publisherLine(album);
    if (publisherLine != null) {
      JLabel publisherLabel = new JLabel(publisherLine, SwingConstants.LEFT);
      publisherLabel.setOpaque(true);
      publisherLabel.setBackground(borderColor);
      publisherLabel.setForeground(Color.BLACK);
      publisherLabel.setFont(
          new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(8, profile.headerFontSize() - 2)));
      publisherLabel.setBorder(new EmptyBorder(1, 4, 1, 4));
      artColumn.add(publisherLabel, BorderLayout.SOUTH);
    }

    // ── Track listing (CENTER, scrollable) ──────────────────────────────
    // Painted solidly black (not transparent) end-to-end — trackList, the viewport, and the
    // scroll pane itself — instead of relying on transparency to let the card's black
    // background show through, since the viewport was rendering white in the gap below a
    // short track list rather than the card's black.
    JPanel trackList = new JPanel();
    trackList.setLayout(new BoxLayout(trackList, BoxLayout.Y_AXIS));
    trackList.setOpaque(true);
    trackList.setBackground(Color.BLACK);
    trackList.setBorder(new EmptyBorder(2, 2, 2, 4));

    List<SongDto> songs = album.songs() != null ? album.songs() : List.of();
    for (SongDto song : songs) {
      trackList.add(buildTrackRow(song));
    }

    JScrollPane scroll = new JScrollPane(trackList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scroll.setOpaque(true);
    scroll.setBackground(Color.BLACK);
    scroll.getViewport().setOpaque(true);
    scroll.getViewport().setBackground(Color.BLACK);
    scroll.setBorder(null);
    scroll.getVerticalScrollBar().setUnitIncrement(profile.trackRowH());
    scroll.getHorizontalScrollBar().setUnitIncrement(profile.trackRowH());
    // Touch screens need a much wider, easier-to-grab scroll bar than the look and feel's
    // mouse-sized default.
    scroll.getVerticalScrollBar().setUI(new TouchScrollBarUI());
    scroll.getHorizontalScrollBar().setUI(new TouchScrollBarUI());
    // Fill the bottom-right gap left when both scroll bars are showing, so it matches the black
    // tracks rather than the look and feel's default corner color.
    JPanel corner = new JPanel();
    corner.setBackground(Color.BLACK);
    scroll.setCorner(JScrollPane.LOWER_RIGHT_CORNER, corner);

    JPanel content = new JPanel(new BorderLayout(0, 0));
    content.setOpaque(false);
    content.add(artColumn, BorderLayout.WEST);
    content.add(scroll, BorderLayout.CENTER);

    card.add(content, BorderLayout.CENTER);

    return card;
  }

  /**
   * Builds a panel that draws {@code icon} stretched to exactly fill whatever bounds it is given
   * at paint time — unlike a {@link JLabel} icon (which paints at its natural pixel size and
   * leaves padding around it if the allotted cell is a different size), this guarantees no gap
   * between the art and the panel's edges. Falls back to a centered "♫" glyph when {@code icon}
   * is {@code null}.
   */
  private JPanel buildCoverArtPanel(ImageIcon icon) {
    JPanel art = new JPanel() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (icon != null) {
          g.drawImage(icon.getImage(), 0, 0, getWidth(), getHeight(), this);
        } else {
          Graphics2D g2 = (Graphics2D) g.create();
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setColor(ColorTheme.get().sidebarPlaceholderFg);
          g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, getHeight() / 3)));
          java.awt.FontMetrics fm = g2.getFontMetrics();
          String glyph = "♫";
          int tx = (getWidth() - fm.stringWidth(glyph)) / 2;
          int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
          g2.drawString(glyph, tx, ty);
          g2.dispose();
        }
      }
    };
    art.setOpaque(false);
    return art;
  }

  /**
   * Returns "{@code © releaseDate recordLabel}" (e.g. "© 1983 Mercury") for display beneath the
   * cover art, or {@code null} when the album has neither. Filters out the library's placeholder
   * values for unknown metadata ("UNKNOWN" / "1950") so they never render. The © prefix only
   * appears when a release date is present; a label with no date is shown on its own.
   */
  private static String publisherLine(AlbumDto album) {
    String label = album.recordLabel() != null && !album.recordLabel().isBlank()
        && !"UNKNOWN".equalsIgnoreCase(album.recordLabel().trim()) ? album.recordLabel().trim()
            : null;
    String date = album.releaseDate() != null && !album.releaseDate().isBlank()
        && !"1950".equals(album.releaseDate().trim()) ? album.releaseDate().trim() : null;

    if (date == null)
      return label;

    return label != null ? "© " + date + " " + label : "© " + date;
  }

  /** One clickable "NN. Song Title" row — tapping it queues the song directly. */
  private JPanel buildTrackRow(SongDto song) {

    // Preferred width is the row's natural content width (indicator + full track text), not
    // Integer.MAX_VALUE, so the enclosing scroll pane can show a horizontal scroll bar only when a
    // long track name doesn't fit. The maximum width stays unbounded so BoxLayout still stretches
    // every row to the full viewport width (keeping the hover highlight edge-to-edge).
    JPanel row = new JPanel(new BorderLayout(4, 0)) {
      private static final long serialVersionUID = 1L;

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, profile.trackRowH());
      }
    };
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(1, 4, 1, 4));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, profile.trackRowH()));
    row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    // ── Indicator (WEST), directly to the left of the track text: a red play icon when this
    // song is the one currently playing, otherwise the usual popularity bars. ─────────────────
    int barMaxH = Math.max(6, profile.trackRowH() - 8);
    int barsW = 3 * (2 + 1) + 4;

    JComponent indicator;
    if (song.equals(nowPlayingSong)) {
      JLabel playIcon = new JLabel("▶", SwingConstants.CENTER);
      playIcon.setForeground(ColorTheme.get().accentRed);
      playIcon.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, barMaxH)));
      indicator = playIcon;
    } else {
      int[] barHeights = SongTrackCellRenderer.computeBarHeights(barMaxH);
      int bars = SongTrackCellRenderer.barsForPlays(song.numPlays() == null ? 0 : song.numPlays(),
          popularityT1, popularityT2, popularityT3);
      SongTrackCellRenderer.PopularityBarsPanel barsPanel =
          new SongTrackCellRenderer.PopularityBarsPanel(bars, 2, 1, barHeights);
      barsPanel.setOpaque(false);
      barsPanel.setPreferredSize(new Dimension(barsW, barMaxH + 4));
      indicator = barsPanel;
    }

    JPanel indicatorWrapper = new JPanel(new BorderLayout());
    indicatorWrapper.setOpaque(false);
    indicatorWrapper.setPreferredSize(new Dimension(barsW, profile.trackRowH()));
    indicatorWrapper.add(indicator, BorderLayout.CENTER);
    row.add(indicatorWrapper, BorderLayout.WEST);

    JLabel label = new JLabel(String.format("%02d-%s", song.trackNumber(), song.songName()));
    label.setForeground(ColorTheme.get().textPrimary);
    label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, profile.trackFontSize()));
    row.add(label, BorderLayout.CENTER);

    row.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        row.setOpaque(true);
        row.setBackground(new Color(255, 255, 255, 30));
        // Repaint the whole track-rows container in one pass (not just this row) so the
        // hover background is fully drawn before any child repaints, and stale pixels from
        // adjacent rows don't bleed through — mirrors AlbumViewCard.buildTrackRow's fix for
        // the same class of artifact.
        java.awt.Container rowsPanel = row.getParent();
        if (rowsPanel != null) {
          rowsPanel.repaint();
        } else {
          row.repaint();
        }
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        row.setOpaque(false);
        row.setBackground(null);
        java.awt.Container rowsPanel = row.getParent();
        if (rowsPanel != null) {
          rowsPanel.repaint();
        } else {
          row.repaint();
        }
      }

      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (songClickListener != null)
          songClickListener.onSongClicked(song);
      }
    });

    return row;
  }
}
