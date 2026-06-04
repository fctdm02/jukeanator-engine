package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;

public class AlbumGridPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ── Palette ───────────────────────────────────────────────────────────────
  static final Color ACCENT_BLUE = new Color(0, 210, 255);
  static final Color TEXT_PRIMARY = Color.WHITE;
  static final Color TEXT_SECONDARY = new Color(180, 180, 180);

  // ── State ─────────────────────────────────────────────────────────────────
  private final List<AlbumDto> albums;
  private final ImageLoader imageLoader;
  private final int cols;
  private final int rows;
  private final int artW;
  private final int artH;
  private final AlbumClickListener listener;

  private int currentPage = 0;

  // ── Panels rebuilt on each page turn ──────────────────────────────────────
  private final JPanel gridPanel = new JPanel();
  private final JPanel navPanel = new JPanel(new BorderLayout(8, 0));

  // ── Callback ──────────────────────────────────────────────────────────────
  public interface AlbumClickListener {
    void onAlbumClicked(AlbumDto album);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * @param albums Full album list to paginate across.
   * @param imageLoader Shared loader.
   * @param cols Number of tile columns per page.
   * @param rows Number of tile rows per page.
   * @param artW Pixel width of each album art image.
   * @param artH Pixel height of each album art image.
   * @param listener Called when the user taps/clicks an album tile.
   */
  public AlbumGridPanel(List<AlbumDto> albums, ImageLoader imageLoader, int cols, int rows,
      int artW, int artH, AlbumClickListener listener) {

    this.albums = albums != null ? albums : List.of();
    this.imageLoader = imageLoader;
    this.cols = cols;
    this.rows = rows;
    this.artW = artW;
    this.artH = artH;
    this.listener = listener;

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);

    gridPanel.setOpaque(false);
    navPanel.setBorder(new EmptyBorder(8, 16, 8, 16));
    navPanel.setOpaque(false);

    add(gridPanel, BorderLayout.CENTER);
    add(navPanel, BorderLayout.SOUTH);

    refresh();
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /** Go to page 0 and repaint — call this when the album list is replaced. */
  public void reset() {
    currentPage = 0;
    refresh();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PAGE RENDERING
  // ─────────────────────────────────────────────────────────────────────────
  private void refresh() {

    int pageSize = cols * rows;
    int totalPages = Math.max(1, (int) Math.ceil(albums.size() / (double) pageSize));
    currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

    int start = currentPage * pageSize;
    int end = Math.min(start + pageSize, albums.size());

    // ── Grid ──────────────────────────────────────────────────────────────
    gridPanel.removeAll();
    gridPanel.setLayout(new GridLayout(rows, cols, 10, 10));
    gridPanel.setBorder(new EmptyBorder(12, 12, 4, 12));

    for (int i = start; i < end; i++) {
      gridPanel.add(buildTile(albums.get(i)));
    }

    // Fill remaining slots with blank panels so the grid stays uniform
    int filled = end - start;
    for (int i = filled; i < pageSize; i++) {
      JPanel blank = new JPanel();
      blank.setOpaque(false);
      gridPanel.add(blank);
    }

    // ── Navigation ────────────────────────────────────────────────────────
    navPanel.removeAll();

    JButton prevBtn = createButton("❮");
    prevBtn.setVisible(currentPage > 0);
    prevBtn.addActionListener(e -> {
      currentPage--;
      refresh();
    });

    JButton nextBtn = createButton("❯");
    nextBtn.setVisible(currentPage < totalPages - 1);
    nextBtn.addActionListener(e -> {
      currentPage++;
      refresh();
    });

    JLabel pageLabel = new JLabel((currentPage + 1) + " / " + totalPages, SwingConstants.CENTER);
    pageLabel.setForeground(TEXT_SECONDARY);
    pageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));

    navPanel.add(prevBtn, BorderLayout.WEST);
    navPanel.add(pageLabel, BorderLayout.CENTER);
    navPanel.add(nextBtn, BorderLayout.EAST);

    // Only show nav bar when there is more than one page
    navPanel.setVisible(totalPages > 1);

    gridPanel.revalidate();
    gridPanel.repaint();
    navPanel.revalidate();
    navPanel.repaint();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ALBUM TILE
  // ─────────────────────────────────────────────────────────────────────────
  private JPanel buildTile(AlbumDto album) {

    // Structural layout wrapper featuring internal interaction tracking state variables,
    // matching the frosted-glass hover style used by GenrePanel genre tiles.
    JPanel tile = new JPanel(new BorderLayout(0, 0)) {
      private static final long serialVersionUID = 1L;
      private boolean isHovered = false;

      {
        // Attach lighting focus adapter properties locally
        addMouseListener(new java.awt.event.MouseAdapter() {
          @Override
          public void mouseEntered(java.awt.event.MouseEvent e) {
            isHovered = true;
            repaint();
          }

          @Override
          public void mouseExited(java.awt.event.MouseEvent e) {
            isHovered = false;
            repaint();
          }
        });
      }

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Match GenrePanel genre tiles: Frosted glass backing plate translucent metrics
        if (isHovered) {
          g2.setColor(new Color(255, 255, 255, 30)); // Brightened backdrop glow
        } else {
          g2.setColor(new Color(255, 255, 255, 15)); // Soft resting backdrop mesh
        }
        g2.fillRoundRect(0, 0, w, h, 16, 16);

        // Match GenrePanel genre tiles: Perimeter highlight rings
        if (isHovered) {
          g2.setColor(ACCENT_BLUE);
          g2.setStroke(new java.awt.BasicStroke(2.0f));
          g2.drawRoundRect(1, 1, w - 2, h - 2, 16, 16);
        } else {
          g2.setColor(new Color(255, 255, 255, 35));
          g2.setStroke(new java.awt.BasicStroke(1.0f));
          g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);
        }

        g2.dispose();
        super.paintComponent(g);
      }
    };
    tile.setOpaque(false);
    tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    tile.setBorder(new EmptyBorder(1, 1, 1, 1)); // breathing room inside border

    // ── Cover art ─────────────────────────────────────────────────────────
    JLabel artLabel = new JLabel();
    artLabel.setPreferredSize(new Dimension(artW, artH));
    artLabel.setHorizontalAlignment(SwingConstants.CENTER);
    artLabel.setVerticalAlignment(SwingConstants.CENTER);
    artLabel.setOpaque(false);
    artLabel.setBackground(new Color(30, 30, 42));

    if (album.getCoverArtPath() != null) {
      try {
        ImageIcon icon = imageLoader.loadFilesystemImage(album.getCoverArtPath(), artW, artH);
        if (icon != null)
          artLabel.setIcon(icon);
      } catch (Exception ignored) {
      }
    }

    if (artLabel.getIcon() == null) {
      artLabel.setText("♫");
      artLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, artH / 3));
      artLabel.setForeground(new Color(80, 80, 100));
    }

    // ── Text panel ────────────────────────────────────────────────────────
    // GridLayout gives each label the full tile width; CENTER alignment centres the text.
    JPanel textPanel = new JPanel(new java.awt.GridLayout(2, 1, 0, 2));
    textPanel.setOpaque(false);
    textPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

    JLabel albumLabel = new JLabel(truncate(album.getAlbumName(), 24), SwingConstants.CENTER);
    albumLabel.setForeground(TEXT_PRIMARY);
    albumLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

    JLabel artistLabel = new JLabel(truncate(album.getArtistName(), 24), SwingConstants.CENTER);
    artistLabel.setForeground(TEXT_SECONDARY);
    artistLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

    textPanel.add(albumLabel);
    textPanel.add(artistLabel);

    tile.add(artLabel, BorderLayout.CENTER);
    tile.add(textPanel, BorderLayout.SOUTH);

    // ── Click ─────────────────────────────────────────────────────────────
    tile.addMouseListener(new java.awt.event.MouseAdapter() {

      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (listener != null)
          listener.onAlbumClicked(album);
      }
    });

    return tile;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HELPERS
  // ─────────────────────────────────────────────────────────────────────────
  private JButton createButton(String text) {

    // Idle and hover fill colours mirror the sort button's active gradient
    final Color GRAD_TOP = new Color(0, 160, 210);
    final Color GRAD_BOTTOM = new Color(0, 80, 130);
    final Color HOVER_TOP = new Color(0, 190, 240);
    final Color HOVER_BOTTOM = new Color(0, 100, 160);

    JButton button = new JButton(text) {

      private static final long serialVersionUID = 1L;
      private boolean hovered = false;

      {
        addMouseListener(new java.awt.event.MouseAdapter() {
          @Override
          public void mouseEntered(java.awt.event.MouseEvent e) {
            hovered = true;
            repaint();
          }

          @Override
          public void mouseExited(java.awt.event.MouseEvent e) {
            hovered = false;
            repaint();
          }
        });
      }

      @Override
      protected void paintComponent(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Gradient fill — brighter on hover
        Color top = hovered ? HOVER_TOP : GRAD_TOP;
        Color bottom = hovered ? HOVER_BOTTOM : GRAD_BOTTOM;
        g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

        // Accent border
        g2.setColor(ACCENT_BLUE);
        g2.setStroke(new java.awt.BasicStroke(1.5f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

        g2.dispose();
        super.paintComponent(g);
      }
    };

    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    button.setForeground(Color.WHITE);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(new Dimension(140, 52));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    return button;
  }

  private static String truncate(String s, int maxChars) {
    if (s == null)
      return "";
    if (s.length() <= maxChars)
      return s;
    return s.substring(0, maxChars - 1) + "…";
  }
}
