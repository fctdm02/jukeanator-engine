package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;

public class AlbumGridPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ─────────────────────────────────────────────────────────────────────────
  // SHARED DISPLAY HELPER
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns the album name with the genre appended in parentheses when a non-blank genre name is
   * available — e.g. {@code "Rebel Yell (80s)"}. Falls back to just the album name (or an empty
   * string) when either value is absent.
   */
  public static String albumDisplayName(String albumName, String genreName) {
    String name = albumName != null ? albumName : "";
    if (genreName != null && !genreName.isBlank()) {
      return name + " (" + genreName + ")";
    }
    return name;
  }

  // ── State ─────────────────────────────────────────────────────────────────
  private final List<AlbumDto> albums;
  private final Map<String, List<AlbumDto>> letterMap; // "#", "A"–"Z" → albums
  private final ImageLoader imageLoader;
  private final AlbumClickListener listener;
  private final boolean showLetterNav; // true only for the full "All Albums" grid in HomePanel

  private int startIndex = 0; // index of the album in the upper-left corner of the grid
  private String selectedLetter = "#"; // which letter button is highlighted

  // ── Panels rebuilt on each page turn ──────────────────────────────────────
  private final JPanel gridPanel = new JPanel();
  private final JPanel navPanel = new JPanel(new BorderLayout(8, 0));

  // ── Resolution-aware grid profile for the album sub-grid (artist detail) ──
  private final LayoutTheme.GridProfile albumGridProfile;

  // ── Callback ──────────────────────────────────────────────────────────────
  public interface AlbumClickListener {
    void onAlbumClicked(AlbumDto album);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public AlbumGridPanel(List<AlbumDto> albums, Map<String, List<AlbumDto>> letterMap,
      ImageLoader imageLoader, LayoutTheme.GridProfile albumGridProfile,
      AlbumClickListener listener, boolean showLetterNav) {

    this.albums = albums != null ? albums : List.of();
    this.letterMap = letterMap != null ? letterMap : Map.of();
    this.imageLoader = imageLoader;
    this.albumGridProfile = albumGridProfile;
    this.listener = listener;
    this.showLetterNav = showLetterNav;

    // Pick a random letter from the available buckets at startup (only when letter nav is shown)
    if (showLetterNav && !this.letterMap.isEmpty()) {

      // Build a temporary map filtering out empty lists and keys starting with X, Y, or Z
      java.util.Map<String, List<AlbumDto>> filteredLetterMap = new java.util.HashMap<>();
      for (java.util.Map.Entry<String, List<AlbumDto>> entry : this.letterMap.entrySet()) {

        String key = entry.getKey();
        List<AlbumDto> albumList = entry.getValue();

        if (albumList != null && !albumList.isEmpty()) {
          String upperKey = key.toUpperCase();
          if (!upperKey.startsWith("W") && !upperKey.startsWith("X") && !upperKey.startsWith("Y")
              && !upperKey.startsWith("Z")) {
            filteredLetterMap.put(key, albumList);
          }
        }
      }

      // Fall back to the original map if the filtered map ends up entirely empty
      java.util.Map<String, List<AlbumDto>> selectionMap =
          filteredLetterMap.isEmpty() ? this.letterMap : filteredLetterMap;

      List<String> keys = new java.util.ArrayList<>(selectionMap.keySet());
      selectedLetter = keys.get(new java.util.Random().nextInt(keys.size()));
      startIndex = letterStartIndex(selectedLetter);
    }

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);

    gridPanel.setOpaque(false);
    navPanel.setBorder(new EmptyBorder(4, 0, 4, 0)); // edge-to-edge: was (4, 16, 4, 16)
    navPanel.setOpaque(false);
    // Lock nav panel height to nav-button height + top/bottom padding
    int navH = LayoutTheme.get().navBtnH + 4 + 4;
    navPanel.setPreferredSize(new Dimension(0, navH));
    navPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, navH));

    add(gridPanel, BorderLayout.CENTER);
    add(navPanel, BorderLayout.SOUTH);

    refresh();
    preloadAllPagesInBackground();
  }

  /**
   * Warms the shared {@link ImageLoader} cache with every album's cover art at this grid's tile
   * size, on a background daemon thread, so flipping to a page that hasn't been visited yet still
   * finds its art already decoded and scaled in {@link ImageLoader#cache} instead of stalling the
   * EDT on disk I/O and image scaling.
   */
  private void preloadAllPagesInBackground() {

    if (albums.isEmpty()) {
      return;
    }

    Thread preloader = new Thread(() -> {
      for (AlbumDto album : albums) {
        if (album.getCoverArtPath() != null) {
          try {
            imageLoader.loadFilesystemImage(album.getCoverArtPath(), albumGridProfile.artW(),
                albumGridProfile.artH());
          } catch (Exception ignored) {
          }
        }
      }
    }, "album-art-preloader");
    preloader.setDaemon(true);
    preloader.setPriority(Thread.MIN_PRIORITY);
    preloader.start();
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /** Go to page 0 and repaint — call this when the album list is replaced. */
  public void reset() {

    startIndex = 0;
    if (!letterMap.isEmpty()) {
      selectedLetter = letterMap.keySet().iterator().next();
    }
    refresh();
  }

  /** Returns the currently highlighted letter button's key (e.g. "#" or "A"-"Z"). */
  public String getSelectedLetter() {
    return selectedLetter;
  }

  /**
   * Returns how many pages into the currently selected letter's bucket the visible page is — 0 for
   * the bucket's first page, 1 for its second page, etc. Used to restore the same relative page when
   * rebuilding this panel under a different sort order.
   */
  public int getPageOffsetWithinLetter() {
    int pageSize = albumGridProfile.cols() * albumGridProfile.rows();
    int letterStart = letterStartIndex(selectedLetter);
    return Math.max(0, (startIndex - letterStart) / pageSize);
  }

  /**
   * Selects {@code letter} (falling back to the first available letter if it has no bucket here)
   * and jumps {@code pageOffset} pages into that bucket, clamped to the album list's bounds.
   */
  public void selectLetterAtPage(String letter, int pageOffset) {
    if (letterMap.isEmpty())
      return;

    selectedLetter = letterMap.containsKey(letter) ? letter : letterMap.keySet().iterator().next();
    int pageSize = albumGridProfile.cols() * albumGridProfile.rows();
    startIndex = letterStartIndex(selectedLetter) + pageOffset * pageSize;
    refresh();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PAGE RENDERING
  // ─────────────────────────────────────────────────────────────────────────
  private void refresh() {

    int pageSize = albumGridProfile.cols() * albumGridProfile.rows();
    int total = albums.size();

    // Clamp startIndex to valid range
    startIndex = Math.max(0, Math.min(startIndex, Math.max(0, total - 1)));

    int start = startIndex;
    int end = Math.min(start + pageSize, total);

    boolean hasPrev = start > 0;
    boolean hasNext = end < total;

    // ── Grid ──────────────────────────────────────────────────────────────
    gridPanel.removeAll();
    gridPanel.setLayout(new GridLayout(albumGridProfile.rows(), albumGridProfile.cols(),
        LayoutTheme.get().albumGridGapH, LayoutTheme.get().albumGridGapV));
    gridPanel.setBorder(new EmptyBorder(8, 0, 4, 0)); // edge-to-edge: was (8, 12, 4, 12)

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

    JButton prevBtn = ButtonFactory.createNavigationButton("❮");
    prevBtn.setVisible(hasPrev);
    prevBtn.addActionListener(e -> {
      startIndex = Math.max(0, startIndex - pageSize);
      selectedLetter = letterForIndex(startIndex);
      refresh();
    });

    JButton nextBtn = ButtonFactory.createNavigationButton("❯");
    nextBtn.setVisible(hasNext);
    nextBtn.addActionListener(e -> {
      startIndex = Math.min(startIndex + pageSize, total - 1);
      selectedLetter = letterForIndex(startIndex);
      refresh();
    });

    // ── Letter button strip ───────────────────────────────────────────────
    JPanel letterStrip = showLetterNav ? buildLetterStrip() : new JPanel();
    if (!showLetterNav)
      letterStrip.setOpaque(false);

    // Prev/next wrappers keep the strip centred even when one arrow is hidden
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

    // Show nav bar whenever there are letter buttons to display, regardless of
    // whether pagination is needed. Hide only when there is nothing to navigate.
    navPanel.setVisible((showLetterNav && !letterMap.isEmpty()) || hasPrev || hasNext);

    gridPanel.revalidate();
    gridPanel.repaint();
    navPanel.revalidate();
    navPanel.repaint();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // LETTER NAVIGATION HELPERS
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Builds the centre letter-button strip for the nav bar. One AMI 3D button is created per key
   * present in {@code letterMap}. Buttons fill all available horizontal space between the ❮ and ❯
   * wrappers and are exactly 36 px tall — matching the navigation button height.
   */
  private JPanel buildLetterStrip() {

    JPanel strip = new JPanel(new GridLayout(1, 0, 2, 0));
    strip.setOpaque(false);

    if (letterMap.isEmpty())
      return strip;

    int btnCount = letterMap.size();
    // Font size scales down gracefully as more letters compete for space.
    // At 27 letters across ~900 px the buttons are ~31 px wide each.
    int fontSize = btnCount <= 10 ? 14 : btnCount <= 18 ? 12 : 10;

    for (String letter : letterMap.keySet()) {
      boolean isSelected = letter.equals(selectedLetter);
      // Pass a null size — GridLayout controls the bounds; buildLetterButton
      // uses setPreferredSize only as a hint which GridLayout ignores anyway.
      JButton btn = buildLetterButton(letter, new Dimension(30, LayoutTheme.get().navBtnH),
          fontSize, isSelected);
      btn.addActionListener(e -> {
        selectedLetter = letter;
        startIndex = letterStartIndex(letter);
        refresh();
      });
      strip.add(btn);
    }

    return strip;
  }

  /**
   * Returns the absolute index into {@code albums} of the first album in the given letter bucket.
   * This becomes the new {@code startIndex} when a letter button is clicked, placing that album
   * exactly in the upper-left corner of the grid.
   *
   * <p>
   * Matching is done by album name rather than object identity so that this works correctly even
   * when the bucket DTOs are different instances from those in the master {@code albums} list.
   */
  private int letterStartIndex(String letter) {
    List<AlbumDto> bucket = letterMap.get(letter);
    if (bucket == null || bucket.isEmpty())
      return 0;

    String targetName = bucket.get(0).getAlbumName();

    for (int i = 0; i < albums.size(); i++) {
      String name = albums.get(i).getAlbumName();
      if (targetName == null ? name == null : targetName.equals(name)) {
        return i;
      }
    }
    return 0;
  }

  /**
   * Given an absolute album index, returns the letter key of the bucket whose first album is at or
   * before that index. Used to keep the highlighted letter in sync when ❮ / ❯ are clicked.
   */
  private String letterForIndex(int idx) {
    if (letterMap.isEmpty() || idx >= albums.size())
      return selectedLetter;

    AlbumDto albumAtIdx = albums.get(idx);
    String name = albumAtIdx.getAlbumName();

    if (name == null || name.isBlank())
      return "#";
    char first = Character.toUpperCase(name.charAt(0));
    String key = Character.isLetter(first) ? String.valueOf(first) : "#";

    // Walk the map to find the nearest key ≤ the album's letter (handles albums
    // spanning multiple pages within one bucket).
    String best = letterMap.keySet().iterator().next();
    for (String k : letterMap.keySet()) {
      if (k.compareTo(key) <= 0)
        best = k;
    }
    return best;
  }

  /**
   * Builds a single AMI 3D-style letter button for the nav strip, matching the visual language of
   * {@link KeyboardPanel#styledKey}. When {@code highlighted} is {@code true} an ACCENT_BLUE neon
   * border is drawn around the button (same as the ABC / 123@ mode-toggle active state).
   */
  private JButton buildLetterButton(String text, Dimension size, int fontSize,
      boolean highlighted) {

    JButton btn = new JButton(text) {
      private static final long serialVersionUID = 1L;

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 7;

        int shadowH = 4;
        int visH = h - shadowH;

        // Drop-shadow slab
        g2.setColor(ColorTheme.get().keyShadow);
        g2.fillRoundRect(1, shadowH, w - 2, visH, arc, arc);

        // Shelf band (bottom ~25%)
        int shelfH = Math.round(visH * 0.25f);
        int faceH = visH - shelfH;
        g2.setColor(ColorTheme.get().keyShelf);
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        // Face gradient
        boolean pressed = getModel().isArmed();
        Color fTop = pressed ? ColorTheme.get().keyFaceBottom : ColorTheme.get().keyFaceTop;
        Color fMid = ColorTheme.get().keyFaceMid;
        Color fBot = pressed ? ColorTheme.get().keyFaceTop : ColorTheme.get().keyFaceBottom;
        g2.setPaint(new LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.55f, 1f},
            new Color[] {fTop, fMid, fBot}));
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        // Specular top-edge highlight
        g2.setColor(ColorTheme.get().keyHighlight);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        // Side-edge sheens
        g2.setColor(ColorTheme.get().keySide);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.drawLine(1, 2, 1, faceH - 2);
        g2.drawLine(w - 2, 2, w - 2, faceH - 2);

        // Label — vertically centred in faceH
        g2.setFont(getFont());
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = (faceH - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(pressed ? ColorTheme.get().accentBlue : ColorTheme.get().textPrimary);
        g2.drawString(getText(), tx, ty);

        // Neon border when this letter is the selected/active one (Item #4)
        if (highlighted) {
          g2.setColor(ColorTheme.get().accentBlue);
          g2.setStroke(new java.awt.BasicStroke(2.0f));
          g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
        }

        g2.dispose();
      }

      @Override
      protected void paintBorder(Graphics g) {}
    };

    btn.setPreferredSize(size);
    btn.setFocusPainted(false);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setOpaque(false);
    btn.setForeground(ColorTheme.get().textPrimary);
    btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return btn;
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
          g2.setColor(ColorTheme.get().bgFrostedGlassHover);
        } else {
          g2.setColor(ColorTheme.get().bgFrostedGlassRest);
        }
        g2.fillRoundRect(0, 0, w, h, 16, 16);

        // Match GenrePanel genre tiles: Perimeter highlight rings
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
    tile.setOpaque(false);
    tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    tile.setBorder(new EmptyBorder(1, 1, 1, 1)); // breathing room inside border

    // ── Cover art ─────────────────────────────────────────────────────────
    // GridBagLayout centres the label both horizontally and vertically inside
    // whatever space the tile's CENTER region provides. We do NOT set a fixed
    // preferredSize on artLabel — the icon's own pixel dimensions drive the
    // label size, so any surplus tile height becomes equal top/bottom padding.
    JPanel artWrapper = new JPanel(new java.awt.GridBagLayout());
    artWrapper.setOpaque(false);

    JLabel artLabel = new JLabel();
    artLabel.setHorizontalAlignment(SwingConstants.CENTER);
    artLabel.setVerticalAlignment(SwingConstants.CENTER);
    artLabel.setOpaque(false);

    if (album.getCoverArtPath() != null) {
      try {
        ImageIcon icon = imageLoader.loadFilesystemImage(album.getCoverArtPath(),
            albumGridProfile.artW(), albumGridProfile.artH());
        if (icon != null)
          artLabel.setIcon(icon);
      } catch (Exception ignored) {
      }
    }

    if (artLabel.getIcon() == null) {
      // Fallback: fix a size so the musical note placeholder occupies the right space
      artLabel.setText("♫");
      artLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, albumGridProfile.artH() / 3));
      artLabel.setForeground(ColorTheme.get().sidebarPlaceholderFg);
      artLabel.setPreferredSize(new Dimension(albumGridProfile.artW(), albumGridProfile.artH()));
    }

    java.awt.GridBagConstraints artGbc = new java.awt.GridBagConstraints();
    artGbc.gridx = 0;
    artGbc.gridy = 0;
    artWrapper.add(artLabel, artGbc);

    // ── Explicit warning image — overlaid on top of the cover art ────────────
    if (Boolean.TRUE.equals(album.getHasExplicit())) {
      // Scale the warning image to 80% of the art width; height is set to half
      // that to match the standard 2:1 Parental Advisory sticker aspect ratio.
      int overlayW = (albumGridProfile.artW() * 2) / 5;
      int overlayH = overlayW / 2;
      ImageIcon warningIcon = imageLoader.loadClasspathImage(
          "Explicit_Lyrics.jpg", overlayW, overlayH);

      JLabel explicit = new JLabel(warningIcon);
      explicit.setOpaque(false);

      // Same cell as artLabel (gridx=0, gridy=0) — GridBagLayout lets multiple
      // components share a cell. Anchor NORTH centers the image horizontally
      // within the column's full width (driven by artLabel's preferred size)
      // and pins it to the top edge.
      java.awt.GridBagConstraints badgeGbc = new java.awt.GridBagConstraints();
      badgeGbc.gridx = 0;
      badgeGbc.gridy = 0;
      badgeGbc.anchor = java.awt.GridBagConstraints.NORTH;
      badgeGbc.insets = new java.awt.Insets(12, 0, 0, 0);
      artWrapper.add(explicit, badgeGbc);
      // Component z-order index 0 is painted LAST (i.e. on top) — push the
      // overlay to index 0 so it renders over artLabel instead of underneath it.
      artWrapper.setComponentZOrder(explicit, 0);
    }

    // ── Text panel ────────────────────────────────────────────────────────
    // BoxLayout Y_AXIS lets the HTML album-name label expand to two lines
    // when the tile is narrow (e.g. 1024 × 768), preventing clipping (Item 3).
    JPanel textPanel = new JPanel();
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
    textPanel.setOpaque(false);
    textPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

    // Artist is shown first (smaller font), album title second (larger font, bold) —
    // matches the Home screen's default Artist-first ordering.
    JLabel artistLabel = new JLabel(album.getArtistName() != null ? album.getArtistName() : "",
        SwingConstants.CENTER);
    artistLabel.setForeground(ColorTheme.get().textSecondary);
    artistLabel
        .setFont(new Font(Font.SANS_SERIF, Font.PLAIN, LayoutTheme.get().fontSizeArtistLabel));
    artistLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

    JLabel albumLabel = new JLabel(
        "<html><div style='text-align:center;'>"
            + albumDisplayName(album.getAlbumName(), album.getGenreName()).replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
            + "</div></html>",
        SwingConstants.CENTER);
    albumLabel.setForeground(ColorTheme.get().textPrimary);
    albumLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LayoutTheme.get().fontSizeAlbumLabel));
    albumLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
    // Tooltip shows the full untruncated name when the tile is too narrow.
    albumLabel.setToolTipText(albumDisplayName(album.getAlbumName(), album.getGenreName()));

    // Only the full "All Albums" grid (HomePanel, showLetterNav) defaults to browsing
    // by artist, so it leads with the artist line. The per-artist grid (ArtistDetailPanel)
    // already shows one artist's albums, so it keeps the album title on top.
    if (showLetterNav) {
      textPanel.add(artistLabel);
      textPanel.add(Box.createVerticalStrut(2));
      textPanel.add(albumLabel);
    } else {
      textPanel.add(albumLabel);
      textPanel.add(Box.createVerticalStrut(2));
      textPanel.add(artistLabel);
    }

    tile.add(artWrapper, BorderLayout.CENTER);
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
}
