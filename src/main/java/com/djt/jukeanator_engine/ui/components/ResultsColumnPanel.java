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
 * Shared factory for the "ARTISTS / ALBUMS / SONGS" three-column result layout used by both
 * {@link SearchPanel} and {@link HotHerePanel}.
 */
public final class ResultsColumnPanel {

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color BG_ROW = new Color(15, 15, 20, 0);
  private static final Color BG_ROW_HOVER = new Color(255, 255, 255, 25);
  private static final Color BG_THUMB = new Color(40, 40, 55);
  private static final Color COLOR_SEP = new Color(255, 255, 255, 25);
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);
  private static final Color TEXT_PRIMARY = Color.WHITE;
  private static final Color TEXT_SECONDARY = new Color(190, 195, 210);

  private ResultsColumnPanel() {}

  // ─────────────────────────────────────────────────────────────────────────
  // FACTORY METHOD
  // ─────────────────────────────────────────────────────────────────────────

  public static <T> JPanel build(String header, List<T> items, int offset, int previewCount,
      ImageLoader imageLoader, Runnable onUp, Runnable onDown, Consumer<T> onItemClick) {

    JPanel outerColumn = new JPanel(new BorderLayout());
    outerColumn.setOpaque(false);
    outerColumn.setBorder(new EmptyBorder(0, 10, 0, 10));

    String displayTitle = header.substring(0, 1).toUpperCase() + header.substring(1).toLowerCase();
    int total = items.size();

    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.setOpaque(false);
    headerPanel.setBorder(new EmptyBorder(12, 4, 12, 4));

    JLabel headerLabel = new JLabel(displayTitle + " (" + total + ")");
    headerLabel.setForeground(Color.WHITE);
    headerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
    headerPanel.add(headerLabel, BorderLayout.WEST);

    JPanel innerColumnBody = new JPanel(new BorderLayout()) {
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
    innerColumnBody.setOpaque(false);

    JPanel rowsPanel = new JPanel();
    rowsPanel.setOpaque(false);
    rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
    rowsPanel.setBorder(new EmptyBorder(4, 0, 4, 0));

    for (int slot = 0; slot < previewCount; slot++) {
      int idx = offset + slot;
      JPanel row =
          (idx < total) ? buildItemRow(idx + 1, items.get(idx), header, imageLoader, onItemClick)
              : buildEmptyRow();
      rowsPanel.add(row);
      if (slot < previewCount - 1) {
        JSeparator sep = new JSeparator();
        sep.setForeground(COLOR_SEP);
        sep.setBackground(COLOR_SEP);
        rowsPanel.add(sep);
      }
    }

    // Navigation Layout container (Made transparent to allow background column gradient through)
    JPanel navPanel = new JPanel(new BorderLayout(8, 0));
    navPanel.setBackground(Color.BLACK);
    navPanel.setBorder(new EmptyBorder(8, 12, 12, 12));

    JButton upBtn = navButton(true);
    upBtn.setEnabled(offset > 0);
    upBtn.addActionListener(e -> onUp.run());

    JButton downBtn = navButton(false);
    downBtn.setEnabled(offset + previewCount < total);
    downBtn.addActionListener(e -> onDown.run());

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
      ImageLoader imageLoader, Consumer<T> onItemClick) {

    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setOpaque(false);
    row.setBackground(BG_ROW);
    row.setBorder(new EmptyBorder(8, 14, 8, 14));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
    row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    JLabel numLabel = new JLabel(String.format("%02d", rowNum));
    numLabel.setForeground(TEXT_SECONDARY);
    numLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
    numLabel.setPreferredSize(new Dimension(36, 56));
    numLabel.setHorizontalAlignment(SwingConstants.CENTER);

    JLabel thumb = new JLabel();
    thumb.setPreferredSize(new Dimension(56, 56));
    thumb.setHorizontalAlignment(SwingConstants.CENTER);
    thumb.setOpaque(true);
    thumb.setBackground(BG_THUMB);

    JLabel line1 = new JLabel();
    JLabel line2 = new JLabel();
    line1.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
    line2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    line1.setForeground(TEXT_PRIMARY);
    line2.setForeground(TEXT_SECONDARY);

    String coverPath = extractFields(item, category, line1, line2);

    if (coverPath != null && imageLoader != null) {
      try {
        ImageIcon icon = imageLoader.loadFilesystemImage(coverPath, 56, 56);
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
        row.setBackground(BG_ROW_HOVER);
        repaintChildren(row);
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        row.setOpaque(false);
        row.setBackground(BG_ROW);
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
      line2.setText(a.getSongCount() + " songs, " + a.getAlbumCount() + " albums");
      return a.getCoverArtPath();
    }
    if ("ALBUMS".equals(category) && item instanceof AlbumDto a) {
      line1.setText(a.getAlbumName());
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

  private static JPanel buildEmptyRow() {
    JPanel row = new JPanel(new BorderLayout());
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(8, 10, 8, 10));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
    return row;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // FIXED NAV BUTTON: ISOSCELES GEOMETRY VECTOR ENGINE & GLASS OVERLAY
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Generates custom navigation button components designed to cleanly reveal the column gradient
   * below, rendering un-squished wide isosceles triangle carets.
   */
  private static JButton navButton(final boolean isUpDirection) {
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

  private static void repaintChildren(java.awt.Container c) {
    for (java.awt.Component child : c.getComponents())
      child.repaint();
  }
}
