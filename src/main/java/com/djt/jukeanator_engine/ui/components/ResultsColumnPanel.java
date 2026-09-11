package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * Shared factory for the "ARTISTS / ALBUMS / SONGS" three-column result layout.
 *
 * <p>
 * All pixel dimensions are sourced from {@link LayoutTheme} so that a different-resolution or
 * portrait-mode theme automatically adjusts every row height, thumbnail size, and navigation button
 * size without touching this class.
 */
public final class ResultsColumnPanel {

  private ResultsColumnPanel() {}

  /**
   * Position of a column within the 3-column Artists/Albums/Songs row. Only the side facing the
   * screen edge gets {@code resultColumnPadH}; the sides facing a neighboring column get none, so
   * adjacent columns sit flush against each other.
   */
  public enum ColumnPosition {
    FIRST, MIDDLE, LAST
  }

  // ─────────────────────────────────────────────────────────────────────────
  // FACTORY METHOD
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Builds a paginated column view panel.
   *
   * @param onOffsetChanged callback accepting the newly calculated integer offset when navigating
   *        pages
   * @param position this column's position within the Artists/Albums/Songs row, controlling which
   *        outer edge (if any) gets {@code resultColumnPadH}
   * @param popularityT1 minimum plays for 1 popularity bar (SONGS rows only)
   * @param popularityT2 minimum plays for 2 popularity bars (SONGS rows only)
   * @param popularityT3 minimum plays for 3 popularity bars (SONGS rows only)
   */
  public static <T> JPanel build(String header, List<T> items, int offset, int previewCount,
      ImageLoader imageLoader, Consumer<Integer> onOffsetChanged, Consumer<T> onItemClick,
      ColumnPosition position, int popularityT1, int popularityT2, int popularityT3) {

    // Snapshot the LayoutTheme once per build call so we read a consistent set
    // of values even if the singleton were to change between calls.
    final LayoutTheme lt = LayoutTheme.get();

    JPanel outerColumn = new JPanel(new BorderLayout());
    outerColumn.setOpaque(false);
    // Only the outward-facing edge of the first/last column gets resultColumnPadH; the
    // inward-facing edges get none so adjacent columns sit flush against each other. The
    // outer-edge cancellation logic in each screen (SearchPanel/HotHerePanel/GenreDetailPanel)
    // still subtracts exactly resultColumnPadH, so screen-edge margins are unaffected.
    int leftPad = position == ColumnPosition.FIRST ? lt.resultColumnPadH : 0;
    int rightPad = position == ColumnPosition.LAST ? lt.resultColumnPadH : 0;
    outerColumn.setBorder(new EmptyBorder(0, leftPad, 0, rightPad));

    String displayTitle = header.substring(0, 1).toUpperCase() + header.substring(1).toLowerCase();
    int total = items.size();

    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.setOpaque(false);
    headerPanel.setBorder(new EmptyBorder(lt.resultHeaderPadV, 4, lt.resultHeaderPadV, 4));

    JLabel headerLabel = new JLabel(displayTitle);
    headerLabel.setForeground(ColorTheme.get().textPrimary);
    headerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, lt.fontSizeResultHeader));
    headerPanel.add(headerLabel, BorderLayout.WEST);

    JPanel innerColumnBody = new JPanel(new BorderLayout()) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        LinearGradientPaint blueGradient = new LinearGradientPaint(new Point2D.Float(0, 0),
            new Point2D.Float(0, getHeight()), new float[] {0.0f, 1.0f},
            new Color[] {ColorTheme.get().columnGradTop, ColorTheme.get().columnGradBottom});
        g2.setPaint(blueGradient);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    innerColumnBody.setOpaque(false);

    JPanel rowsPanel = new JPanel();
    rowsPanel.setOpaque(false);
    rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
    Border rowsPanelBorder = new EmptyBorder(4, 0, 4, 0);
    // Right-edge divider between this column and its neighbor -- same thickness/color as the
    // JSeparator drawn between item rows below, and applied only to rowsPanel (not the header
    // or the nav bar) so it spans just the item-list height. LAST column has no neighbor to its
    // right, so it gets no divider.
    if (position != ColumnPosition.LAST) {
      Border divider =
          new MatteBorder(0, 0, 0, lt.resultColumnGapW, ColorTheme.get().colorColumnSeparator);
      rowsPanelBorder = BorderFactory.createCompoundBorder(rowsPanelBorder, divider);
    }
    rowsPanel.setBorder(rowsPanelBorder);

    for (int slot = 0; slot < previewCount; slot++) {
      int idx = offset + slot;
      JPanel row = (idx < total)
          ? buildItemRow(idx + 1, items.get(idx), header, imageLoader, onItemClick, lt,
              popularityT1, popularityT2, popularityT3)
          : buildEmptyRow(lt);
      rowsPanel.add(row);
      if (slot < previewCount - 1) {
        JSeparator sep = new JSeparator();
        sep.setForeground(ColorTheme.get().colorColumnSeparator);
        sep.setBackground(ColorTheme.get().colorColumnSeparator);
        // Explicit thickness so this row divider renders at exactly the same pixel
        // thickness as the resultColumnGapW border drawn between the Artists/Albums/
        // Songs columns, rather than relying on the current look-and-feel's default
        // JSeparator thickness (which may not match).
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, lt.resultColumnGapW));
        sep.setPreferredSize(new Dimension(0, lt.resultColumnGapW));
        rowsPanel.add(sep);
      }
    }

    JPanel navPanel = new JPanel(new BorderLayout(8, 0));
    navPanel.setBackground(ColorTheme.get().bgFieldDark);
    // Split resultNavBorderV evenly between top and bottom so the nav panel is as
    // compact as the theme requires (small-landscape: 4px total; landscape: 20px total).
    int navBorderEach = lt.resultNavBorderV / 2;
    navPanel.setBorder(new EmptyBorder(navBorderEach, 12, navBorderEach, 12));

    // actualCount is the number of real item rows rendered this page (may be less
    // than previewCount on the last page, or equal to previewCount otherwise).
    // The nav buttons jump by this value so that pagination always advances by
    // exactly the number of visible rows -- matching the AlbumViewCard fix.
    final int actualCount = Math.min(previewCount, Math.max(0, total - offset));

    JButton upBtn = navButton(true, lt);
    upBtn.setEnabled(offset > 0);
    upBtn.addActionListener(e -> {
      if (onOffsetChanged != null) {
        int newOffset = Math.max(0, offset - actualCount);
        onOffsetChanged.accept(newOffset);
      }
    });

    JButton downBtn = navButton(false, lt);
    downBtn.setEnabled(offset + previewCount < total);
    downBtn.addActionListener(e -> {
      if (onOffsetChanged != null) {
        int newOffset = offset + actualCount;
        onOffsetChanged.accept(newOffset);
      }
    });

    navPanel.add(upBtn, BorderLayout.WEST);
    navPanel.add(downBtn, BorderLayout.EAST);

    innerColumnBody.add(rowsPanel, BorderLayout.CENTER);
    innerColumnBody.add(navPanel, BorderLayout.SOUTH);

    outerColumn.add(headerPanel, BorderLayout.NORTH);
    outerColumn.add(innerColumnBody, BorderLayout.CENTER);

    return outerColumn;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // THREE-COLUMN CONTAINER LAYOUT
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Lays the three already-built Artists/Albums/Songs columns into {@code container} with
   * proportional widths (see {@link LayoutTheme#resultColumnWeightArtists},
   * {@code resultColumnWeightAlbums}, {@code resultColumnWeightSongs}). The right-edge divider
   * between adjacent columns is drawn by {@link #build} directly on the item-rows area (not here),
   * so it spans only the row list and not the header or nav bar. Replaces any existing
   * children/layout of {@code container}, so callers can pass the same container across rebuilds.
   */
  public static void layoutThreeColumns(JPanel container, JPanel artistsColumn,
      JPanel albumsColumn, JPanel songsColumn) {

    final LayoutTheme lt = LayoutTheme.get();

    container.removeAll();
    container.setLayout(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 1.0;

    gbc.gridx = 0;
    gbc.weightx = lt.resultColumnWeightArtists;
    container.add(artistsColumn, gbc);

    gbc.gridx = 1;
    gbc.weightx = lt.resultColumnWeightAlbums;
    container.add(albumsColumn, gbc);

    gbc.gridx = 2;
    gbc.weightx = lt.resultColumnWeightSongs;
    container.add(songsColumn, gbc);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ITEM ROW
  // ─────────────────────────────────────────────────────────────────────────

  private static <T> JPanel buildItemRow(int rowNum, T item, String category,
      ImageLoader imageLoader, Consumer<T> onItemClick, LayoutTheme lt, int popularityT1,
      int popularityT2, int popularityT3) {

    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setOpaque(false);
    row.setBackground(ColorTheme.get().bgRowTransparent);
    // Left inset (before the index number) reduced 25% from 14 to 11 to free up more room
    // for the item text; top/bottom (8) and the right inset (14) are unrelated to the index
    // and left unchanged.
    row.setBorder(new EmptyBorder(8, 11, 8, 14));
    // Previously: new Dimension(Integer.MAX_VALUE, 72) — hard-coded 72px max height.
    // Now: lt.resultRowMaxH so a scaled theme can increase row height proportionally.
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, lt.resultRowMaxH));
    row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    // Previously: new Dimension(36, 56) — hard-coded.
    // Now: lt.resultNumLabelW × lt.resultThumbSize.
    JLabel numLabel = new JLabel(String.format("%02d", rowNum));
    numLabel.setForeground(ColorTheme.get().textResultsSecondary);
    numLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, lt.fontSizeResultNum));
    numLabel.setPreferredSize(new Dimension(lt.resultNumLabelW, lt.resultThumbSize));
    numLabel.setHorizontalAlignment(SwingConstants.CENTER);

    // Previously: new Dimension(56, 56) — hard-coded.
    // Now: lt.resultThumbSize (square).
    JLabel thumb = new JLabel();
    thumb.setPreferredSize(new Dimension(lt.resultThumbSize, lt.resultThumbSize));
    thumb.setHorizontalAlignment(SwingConstants.CENTER);
    thumb.setOpaque(true);
    thumb.setBackground(ColorTheme.get().bgThumb);

    JLabel line1 = new JLabel();
    JLabel line2 = new JLabel();
    // Matches the Home Screen's album tile name size (AlbumGridPanel.fontSizeAlbumLabel)
    // rather than the larger fontSizeResultLine1 (which is still used, unchanged, by the
    // song-queue list rows in SongTrackCellRenderer).
    line1.setFont(new Font(Font.SANS_SERIF, Font.BOLD, lt.fontSizeAlbumLabel));
    line2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, lt.fontSizeResultLine2));
    line1.setForeground(ColorTheme.get().textPrimary);
    line2.setForeground(ColorTheme.get().textResultsSecondary);

    String coverPath = extractFields(item, category, line1, line2);

    // JLabel's minimum size defaults to its full, untruncated-text preferred size. Left
    // uncapped, the longest artist/album/song name in the visible page would force
    // GridBagLayout to grow that whole column past its resultColumnWeight* share (since
    // GridBagLayout can never shrink a column below its children's minimum size), breaking
    // the Artists/Albums equal-width layout. Capping the width lets the label shrink freely;
    // actual rendering still clips/ellipsizes normally since that happens against the real
    // allocated bounds at paint time, not against preferred size.
    capTextWidth(line1);
    capTextWidth(line2);

    if (coverPath != null && imageLoader != null) {
      try {
        ImageIcon icon =
            imageLoader.loadFilesystemImage(coverPath, lt.resultThumbSize, lt.resultThumbSize);
        if (icon != null)
          thumb.setIcon(icon);
      } catch (Exception ignored) {
      }
    }

    JPanel textPanel = new JPanel();
    textPanel.setOpaque(false);
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
    textPanel.add(line1);
    textPanel.add(Box.createVerticalStrut(3));
    textPanel.add(line2);

    // Index cluster: the small green popularity indicator (SONGS rows only) sits directly to
    // the left of the index number, smaller than the one on the Album Details Screen since
    // screen real estate is at a premium here.
    JPanel numCluster = new JPanel(new BorderLayout(3, 0));
    numCluster.setOpaque(false);
    if ("SONGS".equals(category) && item instanceof SongDto s) {
      int active =
          SongTrackCellRenderer.barsForPlays(s.numPlays(), popularityT1, popularityT2, popularityT3);
      SongTrackCellRenderer.PopularityBarsPanel bars = new SongTrackCellRenderer.PopularityBarsPanel(
          active, lt.popularityBarWidthSmall, lt.popularityBarGapSmall,
          SongTrackCellRenderer.computeBarHeights(lt.popularityBarMaxHSmall));
      bars.setOpaque(false);
      bars.setPreferredSize(new Dimension(
          3 * (lt.popularityBarWidthSmall + lt.popularityBarGapSmall), lt.popularityBarMaxHSmall + 4));
      numCluster.add(bars, BorderLayout.WEST);
    }
    numCluster.add(numLabel, BorderLayout.CENTER);

    // Index-to-thumbnail gap reduced 25% from 8 to 6 to free up more room for the item text.
    JPanel left = new JPanel(new BorderLayout(6, 0));
    left.setOpaque(false);
    left.add(numCluster, BorderLayout.WEST);
    left.add(thumb, BorderLayout.CENTER);

    row.add(left, BorderLayout.WEST);
    row.add(textPanel, BorderLayout.CENTER);

    row.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        row.setOpaque(true);
        row.setBackground(ColorTheme.get().bgRowHover);
        // Repaint the entire rows panel so the hover background is drawn
        // in a single pass and no adjacent row text or animated-GIF frames
        // are left as stale pixels within this row's screen bounds.
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
        row.setBackground(ColorTheme.get().bgRowTransparent);
        // Repaint the parent panel on exit for the same reason: clearing the
        // hover fill must happen in one pass so no residue bleeds onto neighbours.
        java.awt.Container rowsPanel = row.getParent();
        if (rowsPanel != null) {
          rowsPanel.repaint();
        } else {
          row.repaint();
        }
      }

      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        onItemClick.accept(item);
      }
    });

    return row;
  }

  /**
   * Zeroes a label's preferred/minimum width while preserving its font-driven height, so it no
   * longer dictates its container chain's layout width. See the call site in {@link #buildItemRow}
   * for why this matters for the Artists/Albums/Songs column proportions.
   */
  private static void capTextWidth(JLabel label) {
    int h = label.getPreferredSize().height;
    Dimension d = new Dimension(0, h);
    label.setMinimumSize(d);
    label.setPreferredSize(d);
  }

  private static <T> String extractFields(T item, String category, JLabel line1, JLabel line2) {
    if ("ARTISTS".equals(category) && item instanceof ArtistDto a) {
      line1.setText(a.artistName());

      int songCount = a.songCount();
      int albumCount = a.albumCount();

      line2.setText(songCount + " " + (songCount == 1 ? "song" : "songs") + ", " + albumCount + " "
          + (albumCount == 1 ? "album" : "albums"));

      return a.coverArtPath();
    }

    if ("ALBUMS".equals(category) && item instanceof AlbumDto a) {
      line1.setText(AlbumGridPanel.albumDisplayName(a.albumName(), a.genreName()));
      line2.setText(a.artistName());
      return a.coverArtPath();
    }

    if ("SONGS".equals(category) && item instanceof SongDto s) {
      line1.setText(s.songName());
      line2.setText(s.artistName());
      return s.coverArtPath();
    }

    return null;
  }

  private static JPanel buildEmptyRow(LayoutTheme lt) {
    JPanel row = new JPanel(new BorderLayout());
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(8, 10, 8, 10));
    // Previously: new Dimension(Integer.MAX_VALUE, 72) — hard-coded.
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, lt.resultRowMaxH));
    return row;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // FIXED NAV BUTTON: ISOSCELES GEOMETRY VECTOR ENGINE & GLASS OVERLAY
  // ─────────────────────────────────────────────────────────────────────────

  private static JButton navButton(final boolean isUpDirection, LayoutTheme lt) {

    JButton btn = new JButton() {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();

        if (isEnabled() && getBackground() != null && getBackground().getAlpha() > 0) {
          g2.setColor(getBackground());
          g2.fillRoundRect(0, 0, w, h, 8, 8);
        }

        if (isEnabled()) {
          g2.setColor(getForeground());
        } else {
          g2.setColor(ColorTheme.get().trackNavDisabledTint);
        }

        g2.setStroke(new java.awt.BasicStroke(3.0f, java.awt.BasicStroke.CAP_ROUND,
            java.awt.BasicStroke.JOIN_ROUND));

        int paddingX = Math.round(w * 0.32f);
        int paddingY = Math.round(h * 0.34f);

        int leftX = paddingX;
        int rightX = w - paddingX;
        int centerX = w / 2;

        if (isUpDirection) {
          int topY = paddingY;
          int bottomY = h - paddingY;
          g2.drawLine(leftX, bottomY, centerX, topY);
          g2.drawLine(centerX, topY, rightX, bottomY);
        } else {
          int topY = paddingY;
          int bottomY = h - paddingY;
          g2.drawLine(leftX, topY, centerX, bottomY);
          g2.drawLine(centerX, bottomY, rightX, topY);
        }

        g2.dispose();
      }
    };

    btn.setOpaque(false);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);

    // Previously: new Dimension(75, 45) — hard-coded.
    // Now: lt.resultNavBtnW × lt.resultNavBtnH.
    btn.setPreferredSize(new Dimension(lt.resultNavBtnW, lt.resultNavBtnH));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    btn.setForeground(ColorTheme.get().textPrimary);
    btn.setBackground(ColorTheme.get().frameTabsTransparent);

    btn.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        if (btn.isEnabled()) {
          btn.setBackground(ColorTheme.get().accentBlue);
          btn.setForeground(ColorTheme.get().bgFieldDark);
          btn.repaint();
        }
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        btn.setBackground(ColorTheme.get().frameTabsTransparent);
        btn.setForeground(ColorTheme.get().textPrimary);
        btn.repaint();
      }
    });

    return btn;
  }


}
