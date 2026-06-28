package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

/**
 * Shared queue-entry cell renderer that renders a song row with green popularity bars, a
 * queue-position / priority badge, and song name + artist/album sub-label.
 *
 * <p>
 * Used by both {@link AdminPanel} and {@link SongQueueCard}.
 */
public class SongTrackCellRenderer extends JPanel
    implements javax.swing.ListCellRenderer<SongQueueEntryDto> {

  private static final long serialVersionUID = 1L;

  // ── Popularity bar geometry — sourced from LayoutTheme ───────────────────
  // These public fields are kept for backward compatibility with AlbumViewCard
  // (which reads BAR_WIDTH/GAP/MAX_H) and SongQueueCard (which reads CELL_HEIGHT).
  // LayoutTheme is the single source of truth; changing it here changes all consumers.
  public static final int BAR_WIDTH = LayoutTheme.get().popularityBarWidth;
  public static final int BAR_GAP = LayoutTheme.get().popularityBarGap;
  public static final int BAR_MAX_H = LayoutTheme.get().popularityBarMaxH;
  public static final int[] BAR_HEIGHTS = {BAR_MAX_H / 2, // bar 1 — ~half height
      (int) Math.round(BAR_MAX_H * 0.72), // bar 2 — ~72 % height
      BAR_MAX_H // bar 3 — full height
  };

  // ── Colours — sourced from ColorTheme.get() ──────────────────────────────

  // ── Priority-based row colours (queue views only) ─────────────────────────
  /** Row background colours keyed by queue priority (index = priority level). */
  public static final Color[] PRIORITY_COLORS = {
      new Color(130, 130, 130), // 0 — gray
      new Color(220, 220, 220), // 1 — white
      new Color(200, 170, 0), // 2 — yellow
      new Color(200, 100, 0), // 3 — orange
      new Color(180, 30, 30), // 4 — red
      new Color(120, 0, 180), // 5+ — purple
  };

  /** Human-readable labels for the priority legend (parallel to {@link #PRIORITY_COLORS}). */
  public static final String[] PRIORITY_LABELS = {"0", "1", "2", "3", "4", "5+"};

  // ── Popularity thresholds (configurable per use-site) ─────────────────────
  private final int t1;
  private final int t2;
  private final int t3;

  /** When {@code true}, row background is driven by queue-entry priority instead of alternating. */
  private final boolean usePriorityColor;

  // ── Sub-widgets ───────────────────────────────────────────────────────────
  private final PopularityBarsPanel barsPanel;
  private final JLabel numLabel = new JLabel();
  private final JLabel thumb = new JLabel();
  private final JLabel song = new JLabel();
  private final JLabel sub = new JLabel();
  private final ImageLoader imageLoader;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  /**
   * @param t1 Minimum plays for 1 bar.
   * @param t2 Minimum plays for 2 bars.
   * @param t3 Minimum plays for 3 bars.
   * @param usePriorityColor When {@code true}, row background is coloured by queue-entry priority.
   * @param imageLoader Used to load the album-art thumbnail (matching the Search / Hot Here / Genre
   *        result rows). May be {@code null}, in which case the thumbnail stays a blank swatch.
   */
  public SongTrackCellRenderer(int t1, int t2, int t3, boolean usePriorityColor,
      ImageLoader imageLoader) {
    this.t1 = t1;
    this.t2 = t2;
    this.t3 = t3;
    this.usePriorityColor = usePriorityColor;
    this.imageLoader = imageLoader;

    barsPanel = new PopularityBarsPanel(0);

    setLayout(new BorderLayout(8, 0));
    setBorder(new EmptyBorder(8, 14, 8, 14));

    // Popularity bars — width derived from LayoutTheme bar geometry
    barsPanel.setPreferredSize(new Dimension(3 * (BAR_WIDTH + BAR_GAP) + 6, BAR_MAX_H + 4));
    barsPanel.setOpaque(false);

    // Row number — same style as the Search / Hot Here / Genre result rows.
    numLabel.setForeground(ColorTheme.get().textResultsSecondary);
    numLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeResultNum));
    numLabel.setHorizontalAlignment(SwingConstants.CENTER);

    // Thumbnail — same square-swatch style as the Search / Hot Here / Genre result rows,
    // sized to fit within the fixed queue cell height.
    int thumbSize = Math.max(24, CELL_HEIGHT - 8);
    numLabel.setPreferredSize(new Dimension(LayoutTheme.get().resultNumLabelW, thumbSize));
    thumb.setPreferredSize(new Dimension(thumbSize, thumbSize));
    thumb.setHorizontalAlignment(SwingConstants.CENTER);
    thumb.setOpaque(true);
    thumb.setBackground(ColorTheme.get().bgThumb);

    JPanel numAndThumb = new JPanel(new BorderLayout(8, 0));
    numAndThumb.setOpaque(false);
    numAndThumb.add(numLabel, BorderLayout.WEST);
    numAndThumb.add(thumb, BorderLayout.CENTER);

    JPanel left = new JPanel(new BorderLayout(8, 0));
    left.setOpaque(false);
    left.add(barsPanel, BorderLayout.WEST);
    left.add(numAndThumb, BorderLayout.CENTER);

    // Text cluster — bold song name (line1) over muted artist/album (line2), matching the
    // result-row two-line text block.
    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    song.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeResultLine1));
    sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeResultLine2));
    text.add(Box.createVerticalGlue());
    text.add(song);
    text.add(Box.createVerticalStrut(3));
    text.add(sub);
    text.add(Box.createVerticalGlue());

    add(left, BorderLayout.WEST);
    add(text, BorderLayout.CENTER);
  }

  /** Backward-compatible overload — no priority colouring, no thumbnail (used by AdminPanel). */
  public SongTrackCellRenderer(int t1, int t2, int t3) {
    this(t1, t2, t3, false, null);
  }

  @Override
  public java.awt.Component getListCellRendererComponent(JList<? extends SongQueueEntryDto> list,
      SongQueueEntryDto entry, int index, boolean isSelected, boolean cellHasFocus) {

    // ── Popularity bars ────────────────────────────────────────────────────
    int plays = entry.getSong().getNumPlays() == null ? 0 : entry.getSong().getNumPlays();
    int active = barsForPlays(plays, t1, t2, t3);
    barsPanel.setActiveBars(active);

    // ── Row number ─────────────────────────────────────────────────────────
    numLabel.setText(String.format("%02d", index + 1));

    // ── Song / sub text ────────────────────────────────────────────────────
    song.setText(entry.getSong().getSongName());
    sub.setText(entry.getSong().getArtistName() + "  •  " + entry.getSong().getAlbumName());

    // Bottom separator line — matches the JSeparator drawn between rows in the
    // Search / Hot Here / Genre result columns. Omitted on the last row.
    boolean isLast = index == list.getModel().getSize() - 1;
    setBorder(isLast ? new EmptyBorder(8, 14, 8, 14)
        : javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0,
                ColorTheme.get().colorColumnSeparator),
            new EmptyBorder(8, 14, 7, 14)));

    // ── Thumbnail ──────────────────────────────────────────────────────────
    thumb.setIcon(null);
    String coverPath = entry.getSong().getCoverArtPath();
    if (coverPath != null && imageLoader != null) {
      int thumbSize = thumb.getPreferredSize().width;
      ImageIcon icon = imageLoader.loadFilesystemImage(coverPath, thumbSize, thumbSize);
      if (icon != null) {
        thumb.setIcon(icon);
      }
    }

    // Row background is transparent (letting the screen gradient show through, matching the
    // Search / Hot Here / Genre result rows) except when selected, which gets a translucent
    // highlight. Sub-label foreground is always the standard theme colour; only the song-name
    // foreground is tinted by priority when the flag is set.
    if (isSelected) {
      setOpaque(true);
      setBackground(ColorTheme.get().bgListSelected);
      song.setForeground(ColorTheme.get().accentBlue);
    } else {
      setOpaque(false);
      if (usePriorityColor) {
        int priority = entry.getPriority() == null ? 0 : entry.getPriority();
        int slot = Math.min(priority, PRIORITY_COLORS.length - 1);
        song.setForeground(PRIORITY_COLORS[slot]);
      } else {
        song.setForeground(ColorTheme.get().textPrimary);
      }
    }
    sub.setForeground(ColorTheme.get().textMuted);
    return this;
  }

  // ── Popularity helper ──────────────────────────────────────────────────────
  public static int barsForPlays(int plays, int t1, int t2, int t3) {
    if (plays >= t3)
      return 3;
    if (plays >= t2)
      return 2;
    if (plays >= t1)
      return 1;
    return 0;
  }

  // ── Inner popularity-bars widget ───────────────────────────────────────────
  /**
   * Three staggered vertical bars painted in {@code ACCENT_GREEN}, identical to the
   * {@code PopularityBarsPanel} previously duplicated in {@code AdminPanel} and
   * {@code AlbumViewCard}.
   */
  public static class PopularityBarsPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private int activeBars;

    public PopularityBarsPanel(int activeBars) {
      this.activeBars = activeBars;
      setOpaque(false);
    }

    public void setActiveBars(int n) {
      this.activeBars = n;
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
          int alpha = Math.min(255, 180 + (i * 25));
          g2.setColor(new Color(ColorTheme.get().accentGreen.getRed(),
              ColorTheme.get().accentGreen.getGreen(), ColorTheme.get().accentGreen.getBlue(),
              alpha));
        } else {
          g2.setColor(ColorTheme.get().popularityBarInactive);
        }
        g2.fillRoundRect(x, y, BAR_WIDTH, barH, 2, 2);
      }
      g2.dispose();
    }
  }

  // ── Default cell height — sourced from LayoutTheme ───────────────────────
  // Kept public so SongQueueCard can still reference SongTrackCellRenderer.CELL_HEIGHT.
  public static final int CELL_HEIGHT = LayoutTheme.get().songTrackCellHeight;

  // ── Convenience factory ────────────────────────────────────────────────────
  /**
   * Convenience method: configure a {@link JList} to use this renderer with the correct fixed cell
   * height.
   */
  public static void install(JList<SongQueueEntryDto> list, int t1, int t2, int t3) {
    list.setCellRenderer(new SongTrackCellRenderer(t1, t2, t3));
    list.setFixedCellHeight(CELL_HEIGHT);
  }

  /**
   * Like {@link #install} but enables priority-based row colouring — intended for
   * {@code SongQueueCard} and {@code AdminPanel} queue lists. No thumbnail is shown since no
   * {@link ImageLoader} is supplied.
   */
  public static void installWithPriority(JList<SongQueueEntryDto> list, int t1, int t2, int t3) {
    list.setCellRenderer(new SongTrackCellRenderer(t1, t2, t3, true, null));
    list.setFixedCellHeight(CELL_HEIGHT);
  }

  /**
   * Like {@link #installWithPriority(JList, int, int, int)} but also renders the album-art
   * thumbnail (matching the Search / Hot Here / Genre result rows) using the given
   * {@link ImageLoader}. Used by {@code QueuePanel}.
   */
  public static void installWithPriority(JList<SongQueueEntryDto> list, int t1, int t2, int t3,
      ImageLoader imageLoader) {
    list.setCellRenderer(new SongTrackCellRenderer(t1, t2, t3, true, imageLoader));
    list.setFixedCellHeight(CELL_HEIGHT);
  }

  // ── Priority legend builder ────────────────────────────────────────────────
  /**
   * Builds a compact horizontal legend panel. A "PRIORITY LEGEND" title (styled like the section
   * header labels) sits on the left, followed by one swatch+number entry per priority level.
   *
   * <p>
   * Background is transparent so it inherits whatever the parent section header paints.
   */
  public static JPanel buildPriorityLegend() {
    JPanel legend = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
    legend.setOpaque(false);

    // "PRIORITY LEGEND" title — styled to match the section header labels
    JLabel title = new JLabel("Priorities: ");
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeTrackArtist));
    title.setForeground(ColorTheme.get().accentGreen);
    legend.add(title);

    // One swatch + number per priority level
    for (int i = 0; i < PRIORITY_COLORS.length; i++) {
      final Color swatchColor = PRIORITY_COLORS[i];
      final String numLabel = PRIORITY_LABELS[i];

      // Colour swatch — rounded rectangle filled with the priority colour
      JPanel swatch = new JPanel() {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics g) {
          Graphics2D g2 = (Graphics2D) g.create();
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setColor(swatchColor);
          g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
          g2.setColor(new Color(0, 0, 0, 80));
          g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
          g2.dispose();
        }
      };
      swatch.setOpaque(false);
      // Swatch size: 12×12 at canonical resolution; scales with bar width in LayoutTheme
      int swatchSize = Math.max(8, BAR_WIDTH * 2 + 2);
      swatch.setPreferredSize(new Dimension(swatchSize, swatchSize));

      JLabel lbl = new JLabel(numLabel);
      lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD,
          Math.max(9, LayoutTheme.get().fontSizeTrackArtist - 3)));
      lbl.setForeground(new Color(200, 200, 200));

      // Wrapper keeps swatch and number snug and vertically centred
      JPanel entry = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 3, 0));
      entry.setOpaque(false);
      entry.add(swatch);
      entry.add(lbl);

      legend.add(entry);
    }
    return legend;
  }
}
