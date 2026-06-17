package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.ArrayList;
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
import com.djt.jukeanator_engine.ui.model.CreditManager;

public class GenrePanel extends JPanel implements TabNavigator {

  private static final long serialVersionUID = 1L;

  // ── Colours — sourced from ColorTheme.get() ──────────────────────────────


  // ── Grid layout ───────────────────────────────────────────────────────────
  private static final int GENRES_PER_PAGE = 12; // 2 rows × 6 cols

  // ── Card names ────────────────────────────────────────────────────────────
  private static final String CARD_GENRES = "GENRES";
  private static final String CARD_ALBUMS = "ALBUMS";
  private static final String CARD_ARTIST = "ARTIST";
  private static final String CARD_DETAIL = "DETAIL";

  // ── Layout ────────────────────────────────────────────────────────────────
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel rootPanel = new JPanel(cardLayout);
  private final JPanel genresGridPanel = new JPanel(new GridLayout(2, 6, 20, 20));
  private final JPanel genreAlbumsSlot = new JPanel(new BorderLayout());

  // ── Pagination ────────────────────────────────────────────────────────────
  private final JPanel genresPaginationPanel = new JPanel(new BorderLayout(8, 0));
  private int currentPage = 0;

  // ── Genre data ────────────────────────────────────────────────────────────
  private List<GenreDto> genres = new ArrayList<>();
  private final DefaultListModel<GenreDto> genresListModel = new DefaultListModel<>();
  private final Map<String, ImageIcon> genreIconCache = new HashMap<>();

  // ── Active detail card ────────────────────────────────────────────────────
  private AlbumDetailCard currentDetailCard;

  // ── Tracks which card to return to when the detail card's BACK button is
  // pressed — CARD_ALBUMS if the album was opened from the genre album list,
  // CARD_ARTIST if it was opened from the artist detail panel. ────────────────
  private String detailReturnCard = CARD_GENRES;

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final char incrementCreditsKey;
  private final CreditManager creditManager;
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final ImageLoader imageLoader;
  private final int priorityCostMultiplier;
  private final int popularityT1;
  private final int popularityT2;
  private final int popularityT3;
  private final int gridCols;
  private final int gridRows;
  private final int artW;
  private final int artH;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public GenrePanel(char incrementCreditsKey, CreditManager creditManager,
      SongLibraryService songLibraryService, SongQueueService songQueueService,
      ImageLoader imageLoader, int priorityCostMultiplier, int popularityT1, int popularityT2,
      int popularityT3, int gridCols, int gridRows, int artW, int artH) {

    this.incrementCreditsKey = incrementCreditsKey;
    this.creditManager = creditManager;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.imageLoader = imageLoader;
    this.priorityCostMultiplier = priorityCostMultiplier;
    this.popularityT1 = popularityT1;
    this.popularityT2 = popularityT2;
    this.popularityT3 = popularityT3;
    this.gridCols = gridCols;
    this.gridRows = gridRows;
    this.artW = artW;
    this.artH = artH;

    setLayout(new BorderLayout());
    setOpaque(false);

    genresGridPanel.setOpaque(false);
    genreAlbumsSlot.setOpaque(false);

    rootPanel.setOpaque(false);
    add(rootPanel, BorderLayout.CENTER);

    JPanel genresCard = buildGenreGridCard();
    genresCard.setName(CARD_GENRES);
    rootPanel.add(genresCard, CARD_GENRES);

    genreAlbumsSlot.setName(CARD_ALBUMS);
    rootPanel.add(genreAlbumsSlot, CARD_ALBUMS);

    JPanel artistPlaceholder = placeholder();
    artistPlaceholder.setName(CARD_ARTIST);
    rootPanel.add(artistPlaceholder, CARD_ARTIST);

    JPanel detailPlaceholder = placeholder();
    detailPlaceholder.setName(CARD_DETAIL);
    rootPanel.add(detailPlaceholder, CARD_DETAIL);

    cardLayout.show(rootPanel, CARD_GENRES);

    refreshGenresUI();
  }

  public void setGenres(List<GenreDto> genres) {

    if (!genres.equals(this.genres)) {

      this.genres = genres;
      genresListModel.clear();
      if (genres != null)
        genres.forEach(genresListModel::addElement);

      int maxPage =
          Math.max(0, (int) Math.ceil(genresListModel.size() / (double) GENRES_PER_PAGE) - 1);
      if (currentPage > maxPage)
        currentPage = maxPage;

      refreshGenresUI();
    }
  }

  @Override
  public void pushAlbumDetail(AlbumDto album) {
    Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
    AlbumDto full = fetchFull(album);

    // Remember which card was visible before navigating to the detail card so
    // the BACK button can return the user to the correct screen (the genre
    // album list or the artist detail panel).
    detailReturnCard = currentVisibleCard();

    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
    }

    currentDetailCard =
        new AlbumDetailCard(owner, full, imageLoader, songQueueService, priorityCostMultiplier,
            popularityT1, popularityT2, popularityT3, this, creditManager, incrementCreditsKey);

    replaceCard(CARD_DETAIL, currentDetailCard);
    cardLayout.show(rootPanel, CARD_DETAIL);
  }

  @Override
  public void popToRoot() {
    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
      currentDetailCard = null;
    }
    cardLayout.show(rootPanel, detailReturnCard);
  }

  /**
   * Resets the Genres tab to the top-level genre grid. Called whenever the user switches to this
   * tab.
   */
  public void resetToDefaultView() {
    if (currentDetailCard != null) {
      currentDetailCard.dismiss();
      currentDetailCard = null;
    }
    detailReturnCard = CARD_GENRES;
    currentPage = 0;
    refreshGenresUI();
    cardLayout.show(rootPanel, CARD_GENRES);
  }

  private JPanel buildGenreGridCard() {

    JPanel pageWrapper = new JPanel(new BorderLayout());
    pageWrapper.setOpaque(false);
    pageWrapper.setBorder(new EmptyBorder(30, 60, 20, 60));
    pageWrapper.add(genresGridPanel, BorderLayout.CENTER);

    genresPaginationPanel.setBorder(new EmptyBorder(4, 16, 4, 16));
    genresPaginationPanel.setOpaque(false);

    JPanel card = new JPanel(new BorderLayout());
    card.setOpaque(false);
    card.add(pageWrapper, BorderLayout.CENTER);
    card.add(genresPaginationPanel, BorderLayout.SOUTH);
    return card;
  }

  private void refreshGenresUI() {
    rebuildPagination();
    refreshGenresPage();
  }

  private void refreshGenresPage() {
    genresGridPanel.removeAll();

    int start = currentPage * GENRES_PER_PAGE;
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

    int totalPages =
        Math.max(1, (int) Math.ceil(genresListModel.size() / (double) GENRES_PER_PAGE));

    JButton prevBtn = ButtonFactory.createNavigationButton("❮");
    prevBtn.addActionListener(e -> {
      if (currentPage > 0) {
        currentPage--;
        refreshGenresUI();
      }
    });
    prevBtn.setVisible(currentPage > 0);

    JButton nextBtn = ButtonFactory.createNavigationButton("❯");
    nextBtn.addActionListener(e -> {
      if (currentPage < totalPages - 1) {
        currentPage++;
        refreshGenresUI();
      }
    });
    nextBtn.setVisible(currentPage < totalPages - 1);

    JLabel pageLabel = new JLabel((currentPage + 1) + " / " + totalPages, SwingConstants.CENTER);
    pageLabel.setForeground(ColorTheme.get().textSecondary);
    pageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));

    // Use a wrapper with a phantom button on each side so the label stays
    // centred even when only one navigation button is visible.
    JPanel prevWrapper = new JPanel(new BorderLayout());
    prevWrapper.setOpaque(false);
    prevWrapper.setPreferredSize(new Dimension(140, 36)); // same as button preferred size
    prevWrapper.add(prevBtn, BorderLayout.CENTER);

    JPanel nextWrapper = new JPanel(new BorderLayout());
    nextWrapper.setOpaque(false);
    nextWrapper.setPreferredSize(new Dimension(140, 36));
    nextWrapper.add(nextBtn, BorderLayout.CENTER);

    genresPaginationPanel.add(prevWrapper, BorderLayout.WEST);
    genresPaginationPanel.add(pageLabel, BorderLayout.CENTER);
    genresPaginationPanel.add(nextWrapper, BorderLayout.EAST);

    // Only show nav bar when there is more than one page
    genresPaginationPanel.setVisible(totalPages > 1);

    genresPaginationPanel.revalidate();
    genresPaginationPanel.repaint();
  }

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
          g2.setColor(ColorTheme.get().bgFrostedGlassHover); // Brightened backdrop glow
        } else {
          g2.setColor(ColorTheme.get().bgFrostedGlassRest); // Soft resting backdrop mesh
        }
        g2.fillRoundRect(0, 0, w, h, 16, 16);

        // Match SearchPanel Hero: Perimeter highlight rings
        if (isHovered) {
          g2.setColor(ColorTheme.get().accentBlue);
          g2.setStroke(new java.awt.BasicStroke(2.0f));
          g2.drawRoundRect(1, 1, w - 2, h - 2, 16, 16);
        } else {
          g2.setColor(ColorTheme.get().bgFrostedGlassRing);
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
    textLabel.setForeground(ColorTheme.get().textPrimary);
    textLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
    textLabel.setBorder(new EmptyBorder(10, 0, 10, 0));
    textLabel.setOpaque(false);

    panel.add(imageLabel, BorderLayout.CENTER);
    panel.add(textLabel, BorderLayout.SOUTH);

    return panel;
  }

  private void showGenreAlbums(GenreDto genre) {

    SearchResultDto results;
    try {
      results = songLibraryService.getGenreMusicByPopularity(genre.getGenreName());
    } catch (Exception e) {
      results = new SearchResultDto();
    }

    GenreDetailPanel detailPanel = new GenreDetailPanel(genre, results, imageLoader, "← BACK",
        () -> cardLayout.show(rootPanel, CARD_GENRES), album -> pushAlbumDetail(album),
        artist -> pushArtistFromGenre(artist), songLibraryService);

    genreAlbumsSlot.removeAll();
    genreAlbumsSlot.add(detailPanel, BorderLayout.CENTER);
    genreAlbumsSlot.revalidate();
    genreAlbumsSlot.repaint();

    detailReturnCard = CARD_ALBUMS;
    cardLayout.show(rootPanel, CARD_ALBUMS);
  }

  /**
   * Navigates to an artist detail view launched from within the genre detail page. Reuses the
   * CARD_ARTIST slot so the AlbumDetailCard's BACK button (via popToRoot → detailReturnCard) can
   * return here correctly.
   */
  private void pushArtistFromGenre(ArtistDto artist) {

    ArtistDto full = null;
    String artistName = artist.getArtistName();
    try {
      full = songLibraryService.getArtistByName(artistName);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalStateException("Could not get artist: [" + artistName + "]", e);
    }

    ArtistDetailPanel panel =
        new ArtistDetailPanel(full, imageLoader, gridCols, gridRows, artW, artH, "← BACK",
            () -> cardLayout.show(rootPanel, CARD_ALBUMS), album -> pushAlbumDetail(album));

    replaceCard(CARD_ARTIST, panel);
    cardLayout.show(rootPanel, CARD_ARTIST);
  }

  /**
   * Returns the name of the card currently visible in {@code rootPanel}, falling back to
   * {@code CARD_GENRES} if none is marked visible (e.g. before the first layout pass).
   */
  private String currentVisibleCard() {
    for (java.awt.Component c : rootPanel.getComponents()) {
      if (c.isVisible()) {
        return c.getName();
      }
    }
    return CARD_GENRES;
  }

  private AlbumDto fetchFull(AlbumDto album) {
    try {
      return songLibraryService.getAlbumById(album.getAlbumId());
    } catch (Exception e) {
      return album;
    }
  }

  private void replaceCard(String name, JPanel newPanel) {
    for (int i = rootPanel.getComponentCount() - 1; i >= 0; i--) {
      if (name.equals(rootPanel.getComponent(i).getName())) {
        rootPanel.remove(i);
        break;
      }
    }
    newPanel.setName(name);
    rootPanel.add(newPanel, name);
    rootPanel.revalidate();
    rootPanel.repaint();
  }

  private JPanel placeholder() {
    JPanel p = new JPanel();
    p.setOpaque(false);
    return p;
  }
}
