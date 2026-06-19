package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
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

  // ── Colours — sourced from ColorTheme.get() ──────────────────────────────

  private ResultsColumnPanel() {}

  // ─────────────────────────────────────────────────────────────────────────
  // FACTORY METHOD
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Builds a paginated column view panel.
   *
   * @param onOffsetChanged callback accepting the newly calculated integer offset when navigating
   *        pages
   */
  public static <T> JPanel build(String header, List<T> items, int offset, int previewCount,
      ImageLoader imageLoader, Consumer<Integer> onOffsetChanged, Consumer<T> onItemClick) {

    // Snapshot the LayoutTheme once per build call so we read a consistent set
    // of values even if the singleton were to change between calls.
    final LayoutTheme lt = LayoutTheme.get();

    JPanel outerColumn = new JPanel(new BorderLayout());
    outerColumn.setOpaque(false);
    // Previously: new EmptyBorder(0, 10, 0, 10) — hard-coded 10px.
    // Now: lt.resultColumnPadH so it scales with the theme.
    outerColumn.setBorder(new EmptyBorder(0, lt.resultColumnPadH, 0, lt.resultColumnPadH));

    String displayTitle = header.substring(0, 1).toUpperCase() + header.substring(1).toLowerCase();
    int total = items.size();

    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.setOpaque(false);
    headerPanel.setBorder(new EmptyBorder(12, 4, 12, 4));

    JLabel headerLabel = new JLabel(displayTitle + " (" + total + ")");
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
    rowsPanel.setBorder(new EmptyBorder(4, 0, 4, 0));

    for (int slot = 0; slot < previewCount; slot++) {
      int idx = offset + slot;
      JPanel row = (idx < total)
          ? buildItemRow(idx + 1, items.get(idx), header, imageLoader, onItemClick, lt)
          : buildEmptyRow(lt);
      rowsPanel.add(row);
      if (slot < previewCount - 1) {
        JSeparator sep = new JSeparator();
        sep.setForeground(ColorTheme.get().colorColumnSeparator);
        sep.setBackground(ColorTheme.get().colorColumnSeparator);
        rowsPanel.add(sep);
      }
    }

    JPanel navPanel = new JPanel(new BorderLayout(8, 0));
    navPanel.setBackground(ColorTheme.get().bgFieldDark);
    navPanel.setBorder(new EmptyBorder(8, 12, 12, 12));

    JButton upBtn = navButton(true, lt);
    upBtn.setEnabled(offset > 0);
    upBtn.addActionListener(e -> {
      if (onOffsetChanged != null) {
        int newOffset = Math.max(0, offset - previewCount);
        onOffsetChanged.accept(newOffset);
      }
    });

    JButton downBtn = navButton(false, lt);
    downBtn.setEnabled(offset + previewCount < total);
    downBtn.addActionListener(e -> {
      if (onOffsetChanged != null) {
        int newOffset = offset + previewCount;
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
  // ITEM ROW
  // ─────────────────────────────────────────────────────────────────────────

  private static <T> JPanel buildItemRow(int rowNum, T item, String category,
      ImageLoader imageLoader, Consumer<T> onItemClick, LayoutTheme lt) {

    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setOpaque(false);
    row.setBackground(ColorTheme.get().bgRowTransparent);
    row.setBorder(new EmptyBorder(8, 14, 8, 14));
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
    line1.setFont(new Font(Font.SANS_SERIF, Font.BOLD, lt.fontSizeResultLine1));
    line2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, lt.fontSizeResultLine2));
    line1.setForeground(ColorTheme.get().textPrimary);
    line2.setForeground(ColorTheme.get().textResultsSecondary);

    String coverPath = extractFields(item, category, line1, line2);

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

    JPanel left = new JPanel(new BorderLayout(8, 0));
    left.setOpaque(false);
    left.add(numLabel, BorderLayout.WEST);
    left.add(thumb, BorderLayout.CENTER);

    row.add(left, BorderLayout.WEST);
    row.add(textPanel, BorderLayout.CENTER);

    row.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        row.setOpaque(true);
        row.setBackground(ColorTheme.get().bgRowHover);
        repaintChildren(row);
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        row.setOpaque(false);
        row.setBackground(ColorTheme.get().bgRowTransparent);
        repaintChildren(row);
      }

      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        onItemClick.accept(item);
      }
    });

    return row;
  }

  private static <T> String extractFields(T item, String category, JLabel line1, JLabel line2) {
    if ("ARTISTS".equals(category) && item instanceof ArtistDto a) {
      line1.setText(a.getArtistName());

      int songCount = a.getSongCount();
      int albumCount = a.getAlbumCount();

      line2.setText(songCount + " " + (songCount == 1 ? "song" : "songs") + ", " + albumCount + " "
          + (albumCount == 1 ? "album" : "albums"));

      return a.getCoverArtPath();
    }

    if ("ALBUMS".equals(category) && item instanceof AlbumDto a) {
      line1.setText(AlbumGridPanel.albumDisplayName(a.getAlbumName(), a.getGenreName()));
      line2.setText(a.getArtistName());
      return a.getCoverArtPath();
    }

    if ("SONGS".equals(category) && item instanceof SongDto s) {
      line1.setText(s.getSongName());
      line2.setText(s.getArtistName());
      return s.getCoverArtPath();
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

  private static void repaintChildren(java.awt.Container c) {
    for (java.awt.Component child : c.getComponents())
      child.repaint();
  }
}
