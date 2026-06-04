package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

public class GenrePanel extends JPanel implements TabNavigator {

  private static final long serialVersionUID = 1L;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color ACCENT_BLUE = new Color(0, 210, 255);

  // ── Grid layout ───────────────────────────────────────────────────────────
  private static final int GENRES_PER_PAGE = 12; // 2 rows × 6 cols

  // ── Outer card names ──────────────────────────────────────────────────────
  private static final String CARD_GENRES = "GENRES";
  private static final String CARD_DETAIL = "DETAIL";

  // ── Inner card names (genre sub-navigation) ───────────────────────────────
  private static final String INNER_GRID = "GENRE_GRID";
  private static final String INNER_ALBUMS = "GENRE_ALBUMS";

  // ── Outer layout ──────────────────────────────────────────────────────────
  private final CardLayout outerCardLayout = new CardLayout();
  private final JPanel outerRoot = new JPanel(outerCardLayout);

  // ── Inner layout (genre grid ↔ genre album list) ──────────────────────────
  private final CardLayout innerCardLayout = new CardLayout();
  private final JPanel innerRoot = new JPanel(innerCardLayout);
  private final JPanel genresGridPanel = new JPanel(new GridLayout(2, 6, 20, 20));
  private final JPanel genreAlbumsSlot = new JPanel(new BorderLayout());

  // ── Pagination ────────────────────────────────────────────────────────────
  private final JPanel genresPaginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
  private int currentGenresPage = 0;

  // ── Genre data ────────────────────────────────────────────────────────────
  private final DefaultListModel<GenreDto> genresListModel = new DefaultListModel<>();
  private final Map<String, ImageIcon> genreIconCache = new HashMap<>();

  // ── Active detail card ────────────────────────────────────────────────────
  private AlbumDetailCard currentDetailCard;

  // ── The genre whose albums are currently shown (used by popToRoot) ─────────
  private GenreDto activeGenre;

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final ImageLoader imageLoader;
  private final int normalPlayCost;
  private final int priorityCost;
  private final int popularityT1;
  private final int popularityT2;
  private final int popularityT3;
  private final boolean enableBigScrollBars;
  private final int gridCols;
  private final int gridRows;
  private final int artW;
  private final int artH;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────

  public GenrePanel(SongLibraryService songLibraryService, SongQueueService songQueueService,
      ImageLoader imageLoader, int normalPlayCost, int priorityCost, int popularityT1,
      int popularityT2, int popularityT3, boolean enableBigScrollBars, int gridCols, int gridRows,
      int artW, int artH) {

    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.imageLoader = imageLoader;
    this.normalPlayCost = normalPlayCost;
    this.priorityCost = priorityCost;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;
    this.enableBigScrollBars = enableBigScrollBars;
    this.gridCols = gridCols;
    this.gridRows = gridRows;
    this.artW = artW;
    this.artH = artH;

    setLayout(new BorderLayout());
    setOpaque(false);

    // ── Inner: genre grid ↔ genre album list ──────────────────────────────
    genresGridPanel.setOpaque(false);
    genreAlbumsSlot.setOpaque(false);
    innerRoot.setOpaque(false);

    innerRoot.add(buildGenreGridCard(), INNER_GRID);
    innerRoot.add(genreAlbumsSlot, INNER_ALBUMS);
    innerCardLayout.show(innerRoot, INNER_GRID);

    // ── Outer: full genres view ↔ album detail ─────────────────────────────
    outerRoot.setOpaque(false);

    innerRoot.setName(CARD_GENRES);
    JPanel initialPlaceholder = placeholder();
    initialPlaceholder.setName(CARD_DETAIL);

    outerRoot.add(innerRoot, CARD_GENRES);
    outerRoot.add(initialPlaceholder, CARD_DETAIL);
    outerCardLayout.show(outerRoot, CARD_GENRES);

    add(outerRoot, BorderLayout.CENTER);

    refreshGenresUI();
  }

  public void setGenres(List<GenreDto> genres) {
    genresListModel.clear();
    if (genres != null)
      genres.forEach(genresListModel::addElement);

    int maxPage =
        Math.max(0, (int) Math.ceil(genresListModel.size() / (double) GENRES_PER_PAGE) - 1);
    if (currentGenresPage > maxPage)
      currentGenresPage = maxPage;

    refreshGenresUI();
  }

  @Override
  public void pushAlbumDetail(AlbumDto album) {
    Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
    AlbumDto full = fetchFull(album);

    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
    }

    currentDetailCard =
        new AlbumDetailCard(owner, full, imageLoader, songQueueService, normalPlayCost,
            priorityCost, popularityT1, popularityT2, popularityT3, enableBigScrollBars, this);

    replaceOuterCard(CARD_DETAIL, currentDetailCard);
    outerCardLayout.show(outerRoot, CARD_DETAIL);
  }

  @Override
  public void popToRoot() {
    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
      currentDetailCard = null;
    }
    outerCardLayout.show(outerRoot, CARD_GENRES);
    innerCardLayout.show(innerRoot, activeGenre != null ? INNER_ALBUMS : INNER_GRID);
  }

  private JPanel buildGenreGridCard() {
    JPanel pageWrapper = new JPanel(new BorderLayout());
    pageWrapper.setOpaque(false);
    pageWrapper.setBorder(new EmptyBorder(30, 60, 20, 60));
    pageWrapper.add(genresGridPanel, BorderLayout.CENTER);

    JPanel bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.setOpaque(false);
    bottomPanel.add(genresPaginationPanel, BorderLayout.CENTER);

    JPanel card = new JPanel(new BorderLayout());
    card.setOpaque(false);
    card.add(pageWrapper, BorderLayout.CENTER);
    card.add(bottomPanel, BorderLayout.SOUTH);
    return card;
  }

  private void refreshGenresUI() {
    rebuildPagination();
    refreshGenresPage();
  }

  private void refreshGenresPage() {
    genresGridPanel.removeAll();

    int start = currentGenresPage * GENRES_PER_PAGE;
    int end = Math.min(start + GENRES_PER_PAGE, genresListModel.size());

    for (int i = start; i < end; i++) {
      genresGridPanel.add(buildGenreTile(genresListModel.get(i)));
    }

    for (int i = end; i < start + GENRES_PER_PAGE; i++) {
      JPanel emptyPlaceholder = new JPanel();
      emptyPlaceholder.setOpaque(false);
      genresGridPanel.add(emptyPlaceholder);
    }

    genresGridPanel.revalidate();
    genresGridPanel.repaint();
  }

  private void rebuildPagination() {
    genresPaginationPanel.removeAll();
    genresPaginationPanel.setLayout(new BorderLayout());
    genresPaginationPanel.setOpaque(false);

    int pageCount = Math.max(1, (int) Math.ceil(genresListModel.size() / (double) GENRES_PER_PAGE));

    JButton prevBtn = createButton("❮");
    prevBtn.addActionListener(e -> {
      if (currentGenresPage > 0) {
        currentGenresPage--;
        refreshGenresUI();
      }
    });
    prevBtn.setVisible(currentGenresPage > 0);

    JButton nextBtn = createButton("❯");
    nextBtn.addActionListener(e -> {
      if (currentGenresPage < pageCount - 1) {
        currentGenresPage++;
        refreshGenresUI();
      }
    });
    nextBtn.setVisible(currentGenresPage < pageCount - 1);

    JPanel dots = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    dots.setOpaque(false);
    for (int i = 0; i < pageCount; i++) {
      JButton dot = new JButton("●");
      dot.setForeground(i == currentGenresPage ? ACCENT_BLUE : Color.WHITE);
      dot.setOpaque(false);
      dot.setBorderPainted(false);
      dot.setFocusPainted(false);
      dot.setContentAreaFilled(false);
      dot.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
      final int page = i;
      dot.addActionListener(e -> {
        currentGenresPage = page;
        refreshGenresUI();
      });
      dots.add(dot);
    }

    JPanel leftWrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
    leftWrap.setOpaque(false);
    leftWrap.add(prevBtn);

    JPanel rightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    rightWrap.setOpaque(false);
    rightWrap.add(nextBtn);

    genresPaginationPanel.add(leftWrap, BorderLayout.WEST);
    genresPaginationPanel.add(dots, BorderLayout.CENTER);
    genresPaginationPanel.add(rightWrap, BorderLayout.EAST);
    genresPaginationPanel.revalidate();
    genresPaginationPanel.repaint();
  }

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
  
  /*
  private JButton navButton(final boolean isLeftDirection) {
    JButton b = new JButton() {
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
          g2.setColor(new Color(255, 255, 255, 40));
        }

        g2.setStroke(new java.awt.BasicStroke(3.0f, java.awt.BasicStroke.CAP_ROUND,
            java.awt.BasicStroke.JOIN_ROUND));

        int paddingX = Math.round(w * 0.38f);
        int paddingY = Math.round(h * 0.28f);

        int topY = paddingY;
        int bottomY = h - paddingY;
        int centerY = h / 2;

        if (isLeftDirection) {
          int leftX = paddingX;
          int rightX = w - paddingX;
          g2.drawLine(rightX, topY, leftX, centerY);
          g2.drawLine(leftX, centerY, rightX, bottomY);
        } else {
          int leftX = paddingX;
          int rightX = w - paddingX;
          g2.drawLine(leftX, topY, rightX, centerY);
          g2.drawLine(rightX, centerY, leftX, bottomY);
        }

        g2.dispose();
      }
    };

    b.setPreferredSize(new java.awt.Dimension(120, 60));
    b.setForeground(Color.WHITE);
    b.setBackground(new Color(255, 255, 255, 0));
    b.setOpaque(false);
    b.setContentAreaFilled(false);
    b.setBorderPainted(false);
    b.setFocusPainted(false);
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    b.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        if (b.isEnabled()) {
          b.setBackground(ACCENT_BLUE);
          b.setForeground(Color.BLACK);
          b.repaint();
        }
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        b.setBackground(new Color(255, 255, 255, 0));
        b.setForeground(Color.WHITE);
        b.repaint();
      }
    });

    return b;
  }
  */

  // ── FIXED ENHANCEMENT: CHROME GLASS POP-OUT DESIGN ────────────────────────
  private JPanel buildGenreTile(GenreDto genre) {
    // Structural layout wrapper featuring internal interaction tracking state variables
    JPanel panel = new JPanel(new BorderLayout()) {
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

          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            showGenreAlbums(genre);
          }
        });
      }

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Match SearchPanel Hero: Frosted glass backing plate translucent metrics
        if (isHovered) {
          g2.setColor(new Color(255, 255, 255, 30)); // Brightened backdrop glow
        } else {
          g2.setColor(new Color(255, 255, 255, 15)); // Soft resting backdrop mesh
        }
        g2.fillRoundRect(0, 0, w, h, 16, 16);

        // Match SearchPanel Hero: Perimeter highlight rings
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

    panel.setOpaque(false);
    panel.setBorder(new EmptyBorder(16, 16, 16, 16)); // Internal component buffer clearance padding
    panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    JLabel imageLabel = new JLabel();
    imageLabel.setOpaque(false);
    imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

    String name = genre.getGenreName();
    try {
      ImageIcon cached = genreIconCache.get(name);
      if (cached != null) {
        imageLabel.setIcon(cached);
      } else {
        String resource = name + ".png";
        if (getClass().getResource(resource) != null) {
          ImageIcon icon = imageLoader.loadImage(resource, 240, 240);
          if (icon != null) {
            Image transparentStrippedImage =
                ImageLoader.createTransparentImage(icon.getImage(), true, 245);
            icon = new ImageIcon(transparentStrippedImage);
          }
          genreIconCache.put(name, icon);
          imageLabel.setIcon(icon);
        } else {
          imageLabel.setText(name);
        }
      }
    } catch (Exception e) {
      imageLabel.setText(name);
    }

    JLabel textLabel = new JLabel(name.toUpperCase(), SwingConstants.CENTER);
    textLabel.setForeground(Color.WHITE);
    textLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
    textLabel.setBorder(new EmptyBorder(10, 0, 10, 0));
    textLabel.setOpaque(false);

    panel.add(imageLabel, BorderLayout.CENTER);
    panel.add(textLabel, BorderLayout.SOUTH);

    return panel;
  }

  private void showGenreAlbums(GenreDto genre) {
    activeGenre = genre;

    SearchResultDto results;
    try {
      results = songLibraryService.getGenreMusicByPopularity(genre.getGenreName());
    } catch (Exception e) {
      results = new SearchResultDto();
    }

    GenreDetailPanel detailPanel = new GenreDetailPanel(genre, results, imageLoader,
        songQueueService, normalPlayCost, priorityCost, "← BACK", () -> {
          activeGenre = null;
          innerCardLayout.show(innerRoot, INNER_GRID);
        }, album -> pushAlbumDetail(album), artist -> pushArtistFromGenre(artist),
        songLibraryService);

    genreAlbumsSlot.removeAll();
    genreAlbumsSlot.add(detailPanel, BorderLayout.CENTER);
    genreAlbumsSlot.revalidate();
    genreAlbumsSlot.repaint();

    innerCardLayout.show(innerRoot, INNER_ALBUMS);
  }

  /**
   * Navigates to an artist detail view launched from within the genre detail page. The outer DETAIL
   * card slot is reused so the back button on AlbumDetailCard routes correctly back through
   * popToRoot → INNER_ALBUMS.
   */
  private void pushArtistFromGenre(ArtistDto artist) {
    ArtistDto full;
    try {
      full = songLibraryService.getArtistById(artist.getArtistId());
    } catch (Exception e) {
      return;
    }

    ArtistDetailPanel panel =
        new ArtistDetailPanel(full, imageLoader, gridCols, gridRows, artW, artH, "← BACK",
            () -> outerCardLayout.show(outerRoot, CARD_GENRES), album -> pushAlbumDetail(album));

    replaceOuterCard(CARD_DETAIL, panel);
    outerCardLayout.show(outerRoot, CARD_DETAIL);
  }

  private AlbumDto fetchFull(AlbumDto album) {
    try {
      return songLibraryService.getAlbumById(album.getAlbumId());
    } catch (Exception e) {
      return album;
    }
  }

  private void replaceOuterCard(String name, JPanel newPanel) {
    for (int i = outerRoot.getComponentCount() - 1; i >= 0; i--) {
      java.awt.Component comp = outerRoot.getComponent(i);
      if (comp != null && name.equals(comp.getName())) {
        outerRoot.remove(i);
        break;
      }
    }
    newPanel.setName(name);
    outerRoot.add(newPanel, name);
    outerRoot.revalidate();
    outerRoot.repaint();
  }

  private JPanel placeholder() {
    JPanel p = new JPanel();
    p.setOpaque(false);
    return p;
  }
}
