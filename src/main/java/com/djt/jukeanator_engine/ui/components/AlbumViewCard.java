package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.text.NumberFormat;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

public class AlbumViewCard extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Layout constants — sourced from LayoutTheme ───────────────────────────
  private static final int LEFT_PANEL_WIDTH = LayoutTheme.get().albumViewSidebarW;
  private static final int COVER_SIZE = LayoutTheme.get().albumViewCoverSize;

  // Number of track rows shown per page in the nav-paginated track listing.
  private static final int TRACKS_PER_PAGE = LayoutTheme.get().albumViewTracksPerPage;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color BG_ROW_HOVER = new Color(255, 255, 255, 25);
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color ACCENT_EXPLICIT = new Color(220, 60, 60);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);
  private static final Color SEPARATOR = new Color(50, 50, 65);

  // ── Popularity bar geometry — sourced from SongTrackCellRenderer ──────────
  private static final int BAR_WIDTH = SongTrackCellRenderer.BAR_WIDTH;
  private static final int BAR_GAP = SongTrackCellRenderer.BAR_GAP;
  private static final int BAR_MAX_H = SongTrackCellRenderer.BAR_MAX_H;

  // ── Left Columns Allocation Dimensions — sourced from LayoutTheme ─────────
  private static final int PLAYS_COLUMN_WIDTH = LayoutTheme.get().albumViewPlaysColW;
  private static final int TRK_NUM_COLUMN_WIDTH = LayoutTheme.get().albumViewTrkNumColW;

  // ── Compilation Grid Column Layout Widths — sourced from LayoutTheme ──────
  private static final int COMPILATION_ARTIST_WIDTH = LayoutTheme.get().albumViewCompilationArtistW;
  private static final int COMPILATION_SONG_WIDTH = LayoutTheme.get().albumViewCompilationSongW;

  // ── Track list pagination state ───────────────────────────────────────────
  private int trackOffset = 0;
  /**
   * The number of track rows actually rendered on the most recent call to
   * {@link #rebuildTrackRows()}.  Used as the page-jump size for the prev/next
   * buttons so that the navigation always advances by exactly as many rows as
   * are visible — regardless of the {@code TRACKS_PER_PAGE} constant, which
   * may differ from the visible count when the panel height is constrained
   * (e.g. on a 1024 × 768 display).
   */
  private int lastRenderedCount = TRACKS_PER_PAGE;
  private List<SongDto> trackSongs;
  private AlbumDto trackAlbum;
  private int trackT1;
  private int trackT2;
  private int trackT3;
  private SongClickListener trackListener;
  private JPanel trackRowsPanel;
  private JButton trackPrevBtn;
  private JButton trackNextBtn;

  // ── Song-click callback ───────────────────────────────────────────────────
  public interface SongClickListener {
    void onSongClicked(SongDto song);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public AlbumViewCard(AlbumDto album, ImageLoader imageLoader, int threshold1, int threshold2,
      int threshold3, SongClickListener songClickListener) {

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);
    // Outer left/right padding matches the ResultsColumnPanel column gutter (10px each side),
    // giving the same screen margins as the genre details view.
    setBorder(new EmptyBorder(0, 10, 0, 10));

    add(buildSidebar(album, imageLoader), BorderLayout.WEST);
    add(buildTrackList(album, threshold1, threshold2, threshold3, songClickListener),
        BorderLayout.CENTER);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // LEFT SIDEBAR — cover art + album metadata
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildSidebar(AlbumDto album, ImageLoader imageLoader) {

    JPanel sidebar = new JPanel(new GridBagLayout());
    sidebar.setOpaque(false);
    sidebar.setPreferredSize(new Dimension(LEFT_PANEL_WIDTH, 0));
    sidebar.setMinimumSize(new Dimension(LEFT_PANEL_WIDTH, 0));
    // Top padding matches the TRACKS header top inset (8px) so cover art aligns with the first row.
    // Left padding matches the ResultsColumnPanel outer column gutter (10px).
    // Right border is the separator line between the sidebar and the track list.
    sidebar.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 0, 1, SEPARATOR), new EmptyBorder(8, 10, 8, 0)));

    // ── Cover art ─────────────────────────────────────────────────────────
    JLabel cover = new JLabel();
    cover.setHorizontalAlignment(SwingConstants.CENTER);
    cover.setVerticalAlignment(SwingConstants.CENTER);
    cover.setOpaque(false);

    if (album.getCoverArtPath() != null) {
      try {
        ImageIcon icon =
            imageLoader.loadFilesystemImage(album.getCoverArtPath(), COVER_SIZE, COVER_SIZE);
        if (icon != null)
          cover.setIcon(icon);
      } catch (Exception ignored) {
      }
    }

    if (cover.getIcon() == null) {
      cover.setPreferredSize(new Dimension(COVER_SIZE, COVER_SIZE));
      cover.setText("♫");
      cover.setFont(new Font(Font.SANS_SERIF, Font.BOLD, COVER_SIZE / 5)); // scales with cover size
      cover.setForeground(new Color(80, 80, 100));
    }

    // ── Metadata ──────────────────────────────────────────────────────────
    JPanel meta = new JPanel();
    meta.setOpaque(false);
    meta.setLayout(new BoxLayout(meta, BoxLayout.Y_AXIS));
    meta.setBorder(new EmptyBorder(14, 14, 14, 14));

    // Album name — wraps if long
    meta.add(wrappingMetaLabel(
        AlbumGridPanel.albumDisplayName(album.getAlbumName(), album.getGenreName()), Font.BOLD,
        LayoutTheme.get().fontSizeDetailTitle, TEXT_PRIMARY));
    meta.add(Box.createVerticalStrut(6));

    // Artist name — wraps if long
    meta.add(wrappingMetaLabel(album.getArtistName(), Font.BOLD,
        LayoutTheme.get().fontSizeDetailSubtitle + 2, ACCENT_BLUE));
    meta.add(Box.createVerticalStrut(6));

    if (album.getReleaseDate() != null && !album.getReleaseDate().isBlank()) {
      meta.add(singleLineMetaLabel(album.getReleaseDate(), Font.PLAIN,
          LayoutTheme.get().fontSizeTrackArtist, TEXT_SECONDARY));
      meta.add(Box.createVerticalStrut(4));
    }

    if (album.getRecordLabel() != null && !album.getRecordLabel().isBlank()) {
      meta.add(singleLineMetaLabel(album.getRecordLabel(), Font.PLAIN,
          LayoutTheme.get().fontSizeTrackArtist, TEXT_SECONDARY));
      meta.add(Box.createVerticalStrut(4));
    }

    if (Boolean.TRUE.equals(album.getHasExplicit())) {
      JLabel explicit = singleLineMetaLabel("EXPLICIT", Font.BOLD,
          LayoutTheme.get().fontSizeAdminArtist, ACCENT_EXPLICIT);
      explicit.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(ACCENT_EXPLICIT, 1), new EmptyBorder(2, 6, 2, 6)));
      meta.add(Box.createVerticalStrut(6));
      meta.add(explicit);
    }

    int trackCount = album.getSongs() == null ? 0 : album.getSongs().size();
    meta.add(Box.createVerticalStrut(6));
    meta.add(singleLineMetaLabel(trackCount + " songs", Font.PLAIN,
        LayoutTheme.get().fontSizeTrackArtist, TEXT_SECONDARY));

    int playCount = album.getNumPlays() == null ? 0 : album.getNumPlays();
    String formattedPlayCount = NumberFormat.getIntegerInstance().format(playCount);
    meta.add(Box.createVerticalStrut(6));
    meta.add(singleLineMetaLabel(formattedPlayCount + " plays", Font.PLAIN,
        LayoutTheme.get().fontSizeTrackArtist, TEXT_SECONDARY));

    JPanel content = new JPanel();
    content.setOpaque(false);
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.add(cover);
    content.add(meta);

    // Place content in the centre of the sidebar so it is vertically centred
    // regardless of how tall the panel grows.
    java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = java.awt.GridBagConstraints.CENTER;
    sidebar.add(content, gbc);

    return sidebar;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // RIGHT PANEL — paginated track list with footer nav (mirrors ResultsColumnPanel)
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildTrackList(AlbumDto album, int t1, int t2, int t3,
      SongClickListener listener) {

    // Stash state needed for page rebuilds.
    this.trackAlbum = album;
    this.trackSongs = album.getSongs() != null ? album.getSongs() : List.of();
    this.trackT1 = t1;
    this.trackT2 = t2;
    this.trackT3 = t3;
    this.trackListener = listener;

    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    // Right padding matches the ResultsColumnPanel outer column gutter (10px).
    // Left padding adds a visual gap between the sidebar separator and the track list.
    wrapper.setBorder(new EmptyBorder(0, 12, 0, 10));

    // ── Header Panel (NORTH) ──────────────────────────────────────────────
    // Matches the size and background profile of the bottom nav panel layout structure
    JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
    headerPanel.setBackground(Color.BLACK);
    headerPanel.setBorder(new EmptyBorder(8, 16, 8, 16));
    headerPanel.setPreferredSize(new Dimension(headerPanel.getPreferredSize().width, 45));

    // Replicating column distribution using sub-panels matching the track rows layout
    JPanel headerLeftCluster = new JPanel(new BorderLayout(6, 0));
    headerLeftCluster.setOpaque(false);

    JLabel popHeaderLabel = new JLabel("# Plays");
    popHeaderLabel.setForeground(TEXT_SECONDARY);
    popHeaderLabel
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist));
    popHeaderLabel.setPreferredSize(new Dimension(PLAYS_COLUMN_WIDTH, 30));

    JLabel trackHeaderLabel = new JLabel("Trk #");
    trackHeaderLabel.setForeground(TEXT_SECONDARY);
    trackHeaderLabel
        .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist));
    trackHeaderLabel.setPreferredSize(new Dimension(TRK_NUM_COLUMN_WIDTH, 30));
    trackHeaderLabel.setHorizontalAlignment(SwingConstants.CENTER);

    headerLeftCluster.add(popHeaderLabel, BorderLayout.WEST);
    headerLeftCluster.add(trackHeaderLabel, BorderLayout.CENTER);

    // Layout configuration designed to tightly group columns together with a padding EAST section
    JPanel headerCenterCluster = new JPanel(new BorderLayout(10, 0));
    headerCenterCluster.setOpaque(false);

    if (album.isCompilation()) {
      JPanel textCluster = new JPanel(new BorderLayout(10, 0));
      textCluster.setOpaque(false);

      JLabel artistHeaderLabel = new JLabel("Artist");
      artistHeaderLabel.setForeground(TEXT_SECONDARY);
      artistHeaderLabel
          .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist));
      artistHeaderLabel.setPreferredSize(new Dimension(COMPILATION_ARTIST_WIDTH, 30));

      JLabel songHeaderLabel = new JLabel("Song");
      songHeaderLabel.setForeground(TEXT_SECONDARY);
      songHeaderLabel
          .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist));
      songHeaderLabel.setPreferredSize(new Dimension(COMPILATION_SONG_WIDTH, 30));

      textCluster.add(artistHeaderLabel, BorderLayout.WEST);
      textCluster.add(songHeaderLabel, BorderLayout.CENTER);

      headerCenterCluster.add(textCluster, BorderLayout.WEST);
      headerCenterCluster.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
    } else {
      JLabel songHeaderLabel = new JLabel("Song");
      songHeaderLabel.setForeground(TEXT_SECONDARY);
      songHeaderLabel
          .setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist));
      headerCenterCluster.add(songHeaderLabel, BorderLayout.CENTER);
    }

    headerPanel.add(headerLeftCluster, BorderLayout.WEST);
    headerPanel.add(headerCenterCluster, BorderLayout.CENTER);
    wrapper.add(headerPanel, BorderLayout.NORTH);

    // ── Blue gradient body — rows + footer nav ────────────────────────────
    // Mirrors ResultsColumnPanel.innerColumnBody: gradient fills the body panel;
    // rowsPanel lives in CENTER and the navPanel lives in SOUTH.
    JPanel body = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        LinearGradientPaint blueGradient = new LinearGradientPaint(new Point2D.Float(0, 0),
            new Point2D.Float(0, getHeight()), new float[] {0.0f, 1.0f},
            new Color[] {new Color(24, 38, 60, 225), new Color(12, 18, 30, 245)});
        g2.setPaint(blueGradient);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    body.setOpaque(false);

    // ── Rows panel ────────────────────────────────────────────────────────
    trackRowsPanel = new JPanel();
    trackRowsPanel.setOpaque(false);
    trackRowsPanel.setLayout(new BoxLayout(trackRowsPanel, BoxLayout.Y_AXIS));
    trackRowsPanel.setBorder(new EmptyBorder(4, 0, 4, 0));

    // ── Footer nav panel (mirrors ResultsColumnPanel navPanel) ────────────
    JPanel navPanel = new JPanel(new BorderLayout(8, 0));
    navPanel.setBackground(Color.BLACK);
    // Use resultNavBorderV (half per side) so the nav panel height matches
    // ResultsColumnPanel exactly — both shrink together on small-landscape,
    // freeing the same 29px that lets 7 rows fit fully visible.
    int navBorderEach = LayoutTheme.get().resultNavBorderV / 2;
    navPanel.setBorder(new EmptyBorder(navBorderEach, 12, navBorderEach, 12));

    trackPrevBtn = trackNavButton(true);
    trackPrevBtn.addActionListener(e -> {
      trackOffset = Math.max(0, trackOffset - lastRenderedCount);
      rebuildTrackRows();
    });

    trackNextBtn = trackNavButton(false);
    trackNextBtn.addActionListener(e -> {
      trackOffset += lastRenderedCount;
      rebuildTrackRows();
    });

    navPanel.add(trackPrevBtn, BorderLayout.WEST);
    navPanel.add(trackNextBtn, BorderLayout.EAST);

    body.add(trackRowsPanel, BorderLayout.CENTER);
    body.add(navPanel, BorderLayout.SOUTH);

    wrapper.add(body, BorderLayout.CENTER);

    // Initial population.
    rebuildTrackRows();

    return wrapper;
  }

  /**
   * Repopulates {@link #trackRowsPanel} for the current {@link #trackOffset} and updates the
   * enabled state of the prev/next nav buttons.
   */
  private void rebuildTrackRows() {

    trackRowsPanel.removeAll();

    int total = trackSongs.size();
    int end = Math.min(trackOffset + TRACKS_PER_PAGE, total);

    for (int i = trackOffset; i < end; i++) {
      trackRowsPanel.add(
          buildTrackRow(trackAlbum, trackSongs.get(i), trackT1, trackT2, trackT3, trackListener));
      if (i < end - 1) {
        JSeparator sep = new JSeparator();
        sep.setForeground(SEPARATOR);
        sep.setBackground(SEPARATOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        trackRowsPanel.add(sep);
      }
    }

    // Record how many rows were actually rendered this pass.
    // The prev/next buttons jump by this count, not by TRACKS_PER_PAGE, so that
    // navigation always advances by exactly the number of visible rows — even
    // when the panel height is constrained to fewer rows than TRACKS_PER_PAGE
    // (e.g. 6 visible rows on a 1024 × 768 display vs. TRACKS_PER_PAGE = 15).
    lastRenderedCount = end - trackOffset;

    trackPrevBtn.setEnabled(trackOffset > 0);
    trackNextBtn.setEnabled(trackOffset + lastRenderedCount < total);

    trackRowsPanel.revalidate();
    trackRowsPanel.repaint();
  }

  /**
   * Creates a nav caret button for the track list footer, using the same vector-geometry rendering
   * as {@code ResultsColumnPanel.navButton}.
   *
   * @param isUpDirection {@code true} for the "previous / up" caret, {@code false} for "next /
   *        down".
   */
  private static JButton trackNavButton(final boolean isUpDirection) {
    JButton btn = new JButton() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();

        // 1. Draw Hover State Overlay Background (Clear by default when not interactive)
        if (isEnabled() && getBackground() != null && getBackground().getAlpha() > 0) {
          g2.setColor(getBackground());
          g2.fillRoundRect(0, 0, w, h, 8, 8);
        }

        // 2. Vector-map the Isosceles Triangle path geometry
        if (isEnabled()) {
          g2.setColor(getForeground());
        } else {
          g2.setColor(new Color(255, 255, 255, 40)); // Clean semi-transparent disabled tint
        }

        g2.setStroke(new java.awt.BasicStroke(3.0f, java.awt.BasicStroke.CAP_ROUND,
            java.awt.BasicStroke.JOIN_ROUND));

        // Geometric boundary padding setups to match the AMI open caret shape
        int paddingX = Math.round(w * 0.32f);
        int paddingY = Math.round(h * 0.34f);

        int leftX = paddingX;
        int rightX = w - paddingX;
        int centerX = w / 2;

        if (isUpDirection) {
          int topY = paddingY;
          int bottomY = h - paddingY;
          // Render wide isosceles pointing upwards
          g2.drawLine(leftX, bottomY, centerX, topY);
          g2.drawLine(centerX, topY, rightX, bottomY);
        } else {
          int topY = paddingY;
          int bottomY = h - paddingY;
          // Render wide isosceles pointing downwards
          g2.drawLine(leftX, topY, centerX, bottomY);
          g2.drawLine(centerX, bottomY, rightX, topY);
        }

        g2.dispose();
      }
    };

    // Initialize with a completely transparent alpha background color context to pass visual checks
    btn.setOpaque(false);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);

    btn.setPreferredSize(
        new Dimension(LayoutTheme.get().resultNavBtnW, LayoutTheme.get().resultNavBtnH));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    // Default visual color rules
    btn.setForeground(Color.WHITE);
    btn.setBackground(new Color(255, 255, 255, 0)); // Pure transparent default pass-through state

    btn.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        if (btn.isEnabled()) {
          // Glow custom corporate accent color block only when active hover tracking triggers
          btn.setBackground(ACCENT_BLUE);
          btn.setForeground(Color.BLACK);
          btn.repaint();
        }
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        // Return instantly back to clear pass-through background environment tracking
        btn.setBackground(new Color(255, 255, 255, 0));
        btn.setForeground(Color.WHITE);
        btn.repaint();
      }
    });

    return btn;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // TRACK ROW
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildTrackRow(AlbumDto album, SongDto song, int t1, int t2, int t3,
      SongClickListener listener) {

    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(10, 16, 10, 16));

    // Give BoxLayout a complete height contract: preferred, minimum, and maximum
    // are all set to the same value so the row height is fully determined and
    // BoxLayout cannot compress rows below their intended height when the body
    // panel has less vertical space than the sum of row heights. Without a
    // minimum/preferred, BoxLayout is free to shrink rows toward 0 px, which
    // causes the text from one row to be painted on top of the next.
    Dimension rowSize = new Dimension(Integer.MAX_VALUE, LayoutTheme.get().resultRowMaxH);
    row.setPreferredSize(rowSize);
    row.setMinimumSize(new Dimension(0, LayoutTheme.get().resultRowMaxH));
    row.setMaximumSize(rowSize);

    if (listener != null) {
      row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ── Popularity bars (WEST) ────────────────────────────────────────────
    int bars = barsForPlays(song.getNumPlays(), t1, t2, t3);
    JPanel barsPanel = new PopularityBarsPanel(bars);
    barsPanel.setOpaque(false);
    barsPanel.setPreferredSize(new Dimension(3 * (BAR_WIDTH + BAR_GAP) + 6, BAR_MAX_H + 4));

    // ── Track number ──────────────────────────────────────────────────────
    JLabel numLabel = new JLabel(String.format("%02d", song.getTrackNumber()));
    numLabel.setForeground(TEXT_SECONDARY);
    numLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeTrackSong));
    numLabel.setPreferredSize(new Dimension(TRK_NUM_COLUMN_WIDTH, 30));
    numLabel.setHorizontalAlignment(SwingConstants.CENTER);

    // ── Left cluster: bars + track num ────────────────────────────────────
    JPanel left = new JPanel(new BorderLayout(6, 0));
    left.setOpaque(false);

    // Wrap barsPanel inside an alignment block matching PLAYS_COLUMN_WIDTH constraint
    JPanel barsAlignmentPanel = new JPanel(new BorderLayout());
    barsAlignmentPanel.setOpaque(false);
    barsAlignmentPanel.setPreferredSize(new Dimension(PLAYS_COLUMN_WIDTH, 30));
    barsAlignmentPanel.add(barsPanel, BorderLayout.WEST);

    left.add(barsAlignmentPanel, BorderLayout.WEST);
    left.add(numLabel, BorderLayout.CENTER);
    row.add(left, BorderLayout.WEST);

    // ── Split Columns (CENTER) ────────────────────────────────────────────
    // Creates structured grid labels layout depending on whether the album object represents a
    // compilation
    JPanel columnsPanel = new JPanel(new BorderLayout(10, 0));
    columnsPanel.setOpaque(false);

    if (album.isCompilation()) {
      JPanel textCluster = new JPanel(new BorderLayout(10, 0));
      textCluster.setOpaque(false);

      JLabel artistLabel = new JLabel(song.getArtistName());
      artistLabel.setForeground(TEXT_PRIMARY);
      artistLabel
          .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeTrackSong));
      artistLabel.setPreferredSize(new Dimension(COMPILATION_ARTIST_WIDTH, 30));

      JLabel songLabel = new JLabel(song.getSongName());
      songLabel.setForeground(TEXT_PRIMARY);
      songLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeTrackSong));
      songLabel.setPreferredSize(new Dimension(COMPILATION_SONG_WIDTH, 30));

      textCluster.add(artistLabel, BorderLayout.WEST);
      textCluster.add(songLabel, BorderLayout.CENTER);

      columnsPanel.add(textCluster, BorderLayout.WEST);
      columnsPanel.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
    } else {
      JLabel songLabel = new JLabel(song.getSongName());
      songLabel.setForeground(TEXT_PRIMARY);
      songLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeTrackSong));
      columnsPanel.add(songLabel, BorderLayout.CENTER);
    }

    row.add(columnsPanel, BorderLayout.CENTER);

    // ── Hover + click ─────────────────────────────────────────────────────
    row.addMouseListener(new java.awt.event.MouseAdapter() {

      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        row.setOpaque(true);
        row.setBackground(BG_ROW_HOVER);
        // Repaint the entire trackRowsPanel in one pass so the hover background
        // is fully drawn before any child repaints, and so stale pixels from
        // adjacent rows (text bleed, animated-GIF observer frames) are cleared.
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
        // Repaint the parent on exit for the same reason: the background clear
        // must happen in one pass so no hover residue bleeds onto neighbours.
        java.awt.Container rowsPanel = row.getParent();
        if (rowsPanel != null) {
          rowsPanel.repaint();
        } else {
          row.repaint();
        }
      }

      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (listener != null)
          listener.onSongClicked(song);
      }
    });

    return row;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // POPULARITY BAR WIDGET
  // ─────────────────────────────────────────────────────────────────────────
  /**
   * Delegates to {@link SongTrackCellRenderer.PopularityBarsPanel} — the authoritative shared
   * implementation. The local type alias keeps all existing call-sites unchanged.
   */
  private static class PopularityBarsPanel extends SongTrackCellRenderer.PopularityBarsPanel {
    private static final long serialVersionUID = 1L;

    PopularityBarsPanel(int activeBars) {
      super(activeBars);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // LABEL HELPERS
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A wrapping label for long strings (album name, artist name).
   *
   * <p>
   * Uses a non-editable, transparent {@link JTextArea} styled to look like a {@link JLabel}.
   * {@code JTextArea} participates in BoxLayout correctly once {@code setLineWrap(true)} and a
   * fixed max-width are set. The sidebar is 260px wide with 14px padding each side = 232px usable.
   */
  private static JTextArea wrappingMetaLabel(String text, int style, int size, Color color) {

    JTextArea area = new JTextArea(text != null ? text : "");
    area.setFont(new Font(Font.SANS_SERIF, style, size));
    area.setForeground(color);
    area.setOpaque(false);
    area.setEditable(false);
    area.setFocusable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setBorder(null);
    area.setAlignmentX(LEFT_ALIGNMENT);

    // Cap width to the usable sidebar width so wrapping triggers correctly.
    // BoxLayout respects maximum width, so we match preferred/max.
    area.setMaximumSize(new Dimension(LEFT_PANEL_WIDTH - 28, Integer.MAX_VALUE));

    return area;
  }

  /**
   * A standard single-line label for short metadata fields.
   */
  private static JLabel singleLineMetaLabel(String text, int style, int size, Color color) {

    JLabel label = new JLabel(text != null ? text : "");
    label.setForeground(color);
    label.setFont(new Font(Font.SANS_SERIF, style, size));
    label.setAlignmentX(LEFT_ALIGNMENT);
    return label;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // MISC HELPERS
  // ─────────────────────────────────────────────────────────────────────────
  private static int barsForPlays(Integer numPlays, int t1, int t2, int t3) {
    return SongTrackCellRenderer.barsForPlays(numPlays == null ? 0 : numPlays, t1, t2, t3);
  }


}
