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

public class AlbumViewPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  private static final int LEFT_PANEL_WIDTH = 320;
  private static final int COVER_SIZE = 320;
  // Number of track rows shown per page in the nav-paginated track listing.
  private static final int TRACKS_PER_PAGE = 16;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color BG_ROW_HOVER = new Color(255, 255, 255, 25);
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color ACCENT_GREEN = new Color(60, 210, 80);
  private static final Color ACCENT_EXPLICIT = new Color(220, 60, 60);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_SECONDARY = new Color(180, 180, 180);
  private static final Color SEPARATOR = new Color(50, 50, 65);

  // ── Popularity bar geometry ───────────────────────────────────────────────
  private static final int BAR_WIDTH = 5;
  private static final int BAR_GAP = 3;
  private static final int BAR_MAX_H = 18;
  private static final int[] BAR_HEIGHTS = {8, 13, 18};

  // ── Track list pagination state ───────────────────────────────────────────
  private int trackOffset = 0;
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

  // ── Album-click callback ───────────────────────────────────────────────────
  public interface AlbumClickListener {
    void onAlbumClicked(AlbumDto album);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public AlbumViewPanel(AlbumDto album, ImageLoader imageLoader, int threshold1, int threshold2,
      int threshold3, SongClickListener songClickListener, AlbumClickListener albumClickListener) {

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);
    // Outer left/right padding matches the ResultsColumnPanel column gutter (10px each side),
    // giving the same screen margins as the genre details view.
    setBorder(new EmptyBorder(0, 10, 0, 10));

    add(buildSidebar(album, imageLoader, albumClickListener), BorderLayout.WEST);
    add(buildTrackList(album, threshold1, threshold2, threshold3, songClickListener),
        BorderLayout.CENTER);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // LEFT SIDEBAR — cover art + album metadata
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildSidebar(AlbumDto album, ImageLoader imageLoader,
      AlbumClickListener albumClickListener) {

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
      cover.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
      cover.setForeground(new Color(80, 80, 100));
    }

    cover.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 65), 1));

    if (albumClickListener != null) {

      cover.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      cover.addMouseListener(new java.awt.event.MouseAdapter() {

        @Override
        public void mouseEntered(java.awt.event.MouseEvent e) {
          cover.setBorder(BorderFactory.createLineBorder(ACCENT_BLUE, 3));
        }

        @Override
        public void mouseExited(java.awt.event.MouseEvent e) {
          cover.setBorder(null);
        }

        @Override
        public void mouseClicked(java.awt.event.MouseEvent e) {
          albumClickListener.onAlbumClicked(album);
        }
      });
    }

    // ── Metadata ──────────────────────────────────────────────────────────
    JPanel meta = new JPanel();
    meta.setOpaque(false);
    meta.setLayout(new BoxLayout(meta, BoxLayout.Y_AXIS));
    meta.setBorder(new EmptyBorder(14, 14, 14, 14));

    // Album name — wraps if long
    meta.add(wrappingMetaLabel(album.getAlbumName(), Font.BOLD, 20, TEXT_PRIMARY));
    meta.add(Box.createVerticalStrut(6));

    // Artist name — wraps if long
    meta.add(wrappingMetaLabel(album.getArtistName(), Font.BOLD, 16, ACCENT_BLUE));
    meta.add(Box.createVerticalStrut(6));

    if (album.getReleaseDate() != null && !album.getReleaseDate().isBlank()) {
      meta.add(singleLineMetaLabel(album.getReleaseDate(), Font.PLAIN, 13, TEXT_SECONDARY));
      meta.add(Box.createVerticalStrut(4));
    }

    if (album.getRecordLabel() != null && !album.getRecordLabel().isBlank()) {
      meta.add(singleLineMetaLabel(album.getRecordLabel(), Font.PLAIN, 13, TEXT_SECONDARY));
      meta.add(Box.createVerticalStrut(4));
    }

    if (Boolean.TRUE.equals(album.getHasExplicit())) {
      JLabel explicit = singleLineMetaLabel("EXPLICIT", Font.BOLD, 12, ACCENT_EXPLICIT);
      explicit.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(ACCENT_EXPLICIT, 1), new EmptyBorder(2, 6, 2, 6)));
      meta.add(Box.createVerticalStrut(6));
      meta.add(explicit);
    }

    int trackCount = album.getSongs() == null ? 0 : album.getSongs().size();
    meta.add(Box.createVerticalStrut(6));
    meta.add(singleLineMetaLabel(trackCount + " tracks", Font.PLAIN, 13, TEXT_SECONDARY));

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
    navPanel.setBorder(new EmptyBorder(8, 12, 12, 12));

    trackPrevBtn = trackNavButton(true);
    trackPrevBtn.addActionListener(e -> {
      trackOffset = Math.max(0, trackOffset - TRACKS_PER_PAGE);
      rebuildTrackRows();
    });

    trackNextBtn = trackNavButton(false);
    trackNextBtn.addActionListener(e -> {
      trackOffset += TRACKS_PER_PAGE;
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

    trackPrevBtn.setEnabled(trackOffset > 0);
    trackNextBtn.setEnabled(trackOffset + TRACKS_PER_PAGE < total);

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

    btn.setPreferredSize(new Dimension(75, 45));
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
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

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
    numLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
    numLabel.setPreferredSize(new Dimension(34, 30));
    numLabel.setHorizontalAlignment(SwingConstants.CENTER);

    String songDescription = "";
    if (album.getArtistName().equals("Compilations")) {
      songDescription = song.getSongName() + " - " + song.getArtistName();
    } else {
      songDescription = song.getSongName();
    }

    // ── Song name ─────────────────────────────────────────────────────────
    JLabel songDescriptionLabel = new JLabel(songDescription);
    songDescriptionLabel.setForeground(TEXT_PRIMARY);
    songDescriptionLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));

    // ── Left cluster: bars + track num ────────────────────────────────────
    JPanel left = new JPanel(new BorderLayout(6, 0));
    left.setOpaque(false);
    left.add(barsPanel, BorderLayout.WEST);
    left.add(numLabel, BorderLayout.CENTER);

    row.add(left, BorderLayout.WEST);
    row.add(songDescriptionLabel, BorderLayout.CENTER);

    // ── Hover + click ─────────────────────────────────────────────────────
    row.addMouseListener(new java.awt.event.MouseAdapter() {

      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        row.setOpaque(true);
        row.setBackground(BG_ROW_HOVER);
        repaintRowChildren(row);
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        row.setOpaque(false);
        row.setBackground(null);
        repaintRowChildren(row);
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
  private static class PopularityBarsPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private final int activeBars;

    PopularityBarsPanel(int activeBars) {
      this.activeBars = activeBars;
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {

      super.paintComponent(g);

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int baseline = getHeight() - 2;

      for (int i = 0; i < 3; i++) {

        int barH = BAR_HEIGHTS[i];
        int x = i * (BAR_WIDTH + BAR_GAP);
        int y = baseline - barH;

        if (i < activeBars) {
          int alpha = 180 + (i * 25);
          g2.setColor(new Color(ACCENT_GREEN.getRed(), ACCENT_GREEN.getGreen(),
              ACCENT_GREEN.getBlue(), Math.min(alpha, 255)));
        } else {
          g2.setColor(new Color(60, 60, 70, 120));
        }

        g2.fillRoundRect(x, y, BAR_WIDTH, barH, 2, 2);
      }

      g2.dispose();
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

    int plays = (numPlays == null) ? 0 : numPlays;
    if (plays >= t3)
      return 3;
    if (plays >= t2)
      return 2;
    if (plays >= t1)
      return 1;
    return 0;
  }

  private static void repaintRowChildren(java.awt.Container c) {
    for (java.awt.Component child : c.getComponents()) {
      child.repaint();
    }
  }
}
