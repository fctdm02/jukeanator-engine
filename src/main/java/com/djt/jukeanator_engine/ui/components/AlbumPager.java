package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JPanel;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;

/**
 * Shared paging + "# A-Z" letter-navigation bookkeeping for an album grid — the current-page
 * {@code startIndex}/{@code selectedLetter} state and the index/letter math around it, plus the
 * letter-strip button UI. Extracted from {@link AlbumGridPanel} so {@link LegacyAlbumGridPanel}
 * can reuse the exact same browsing behavior without duplicating it.
 */
class AlbumPager {

  private final List<AlbumDto> albums;
  private final Map<String, List<AlbumDto>> letterMap; // "#", "A"–"Z" → albums
  // Extracts the sort key (artist name or album title) from an album — used to resolve which
  // letter a given album belongs to when paging across letter buckets. Null when letter
  // navigation is disabled.
  private final Function<AlbumDto, String> letterKeyExtractor;

  private int startIndex = 0;
  private String selectedLetter = "#";

  /**
   * @param pickRandomStart when {@code true} (the root "All Albums" grid), seeds the initial
   *        {@code startIndex}/{@code selectedLetter} at a random non-empty letter bucket (skipping
   *        W/X/Y/Z, matching the original behavior) instead of always starting at the first page.
   */
  AlbumPager(List<AlbumDto> albums, Map<String, List<AlbumDto>> letterMap,
      Function<AlbumDto, String> letterKeyExtractor, boolean pickRandomStart) {

    this.albums = albums;
    this.letterMap = letterMap;
    this.letterKeyExtractor = letterKeyExtractor;

    if (pickRandomStart && !letterMap.isEmpty()) {

      Map<String, List<AlbumDto>> filtered = new java.util.HashMap<>();
      for (Map.Entry<String, List<AlbumDto>> entry : letterMap.entrySet()) {
        String key = entry.getKey();
        List<AlbumDto> albumList = entry.getValue();
        if (albumList != null && !albumList.isEmpty()) {
          String upperKey = key.toUpperCase();
          if (!upperKey.startsWith("W") && !upperKey.startsWith("X") && !upperKey.startsWith("Y")
              && !upperKey.startsWith("Z")) {
            filtered.put(key, albumList);
          }
        }
      }

      Map<String, List<AlbumDto>> selectionMap = filtered.isEmpty() ? letterMap : filtered;
      List<String> keys = new ArrayList<>(selectionMap.keySet());
      selectedLetter = keys.get(new java.util.Random().nextInt(keys.size()));
      startIndex = letterStartIndex(selectedLetter);
    }
  }

  int startIndex() {
    return startIndex;
  }

  String selectedLetter() {
    return selectedLetter;
  }

  /** Clamps {@code startIndex} into {@code [0, total-1]} (or 0 when {@code total} is 0). */
  void clamp(int total) {
    startIndex = Math.max(0, Math.min(startIndex, Math.max(0, total - 1)));
  }

  void reset() {
    startIndex = 0;
    if (!letterMap.isEmpty()) {
      selectedLetter = letterMap.keySet().iterator().next();
    }
  }

  void selectLetter(String letter) {
    selectedLetter = letter;
    startIndex = letterStartIndex(letter);
  }

  /**
   * Selects {@code letter} (falling back to the first available letter if it has no bucket here)
   * and jumps {@code pageOffset} pages into that bucket.
   */
  void selectLetterAtPage(String letter, int pageOffset, int pageSize) {
    if (letterMap.isEmpty())
      return;
    selectedLetter = letterMap.containsKey(letter) ? letter : letterMap.keySet().iterator().next();
    startIndex = letterStartIndex(selectedLetter) + pageOffset * pageSize;
  }

  /** How many pages into the selected letter's bucket the current page is (0 = bucket's first page). */
  int pageOffsetWithinLetter(int pageSize) {
    int letterStart = letterStartIndex(selectedLetter);
    return Math.max(0, (startIndex - letterStart) / pageSize);
  }

  void prevPage(int pageSize) {
    startIndex = Math.max(0, startIndex - pageSize);
    selectedLetter = letterForIndex(startIndex);
  }

  void nextPage(int pageSize, int total) {
    startIndex = Math.min(startIndex + pageSize, Math.max(0, total - 1));
    selectedLetter = letterForIndex(startIndex);
  }

  /**
   * Returns the absolute index into {@code albums} of the first album in the given letter bucket.
   * Matching is done by album ID (unique) so duplicate album names across different artists never
   * resolve to the wrong album in the master list.
   */
  private int letterStartIndex(String letter) {
    List<AlbumDto> bucket = letterMap.get(letter);
    if (bucket == null || bucket.isEmpty())
      return 0;

    Integer targetId = bucket.get(0).albumId();
    for (int i = 0; i < albums.size(); i++) {
      if (targetId != null && targetId.equals(albums.get(i).albumId())) {
        return i;
      }
    }
    return 0;
  }

  /**
   * Given an absolute album index, returns the letter key of the bucket whose first album is at or
   * before that index. Used to keep the highlighted letter in sync when paging.
   */
  private String letterForIndex(int idx) {
    if (letterMap.isEmpty() || idx >= albums.size())
      return selectedLetter;

    AlbumDto albumAtIdx = albums.get(idx);
    String name =
        letterKeyExtractor != null ? letterKeyExtractor.apply(albumAtIdx) : albumAtIdx.albumName();

    if (name == null || name.isBlank())
      return "#";
    char first = Character.toUpperCase(name.charAt(0));
    String key = Character.isLetter(first) ? String.valueOf(first) : "#";

    String best = letterMap.keySet().iterator().next();
    for (String k : letterMap.keySet()) {
      if (k.compareTo(key) <= 0)
        best = k;
    }
    return best;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // LETTER STRIP UI — shared "# A-Z" button row
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Builds the centre letter-button strip for a grid's nav bar. One AMI 3D button is created per
   * key present in {@code letterMap}; {@code onLetterClick} is invoked with the clicked letter so
   * the caller can update its pager and re-render.
   */
  static JPanel buildLetterStrip(Map<String, List<AlbumDto>> letterMap, String selectedLetter,
      Consumer<String> onLetterClick) {

    JPanel strip = new JPanel(new GridLayout(1, 0, 2, 0));
    strip.setOpaque(false);

    if (letterMap.isEmpty())
      return strip;

    int btnCount = letterMap.size();
    int fontSize = btnCount <= 10 ? 14 : btnCount <= 18 ? 12 : 10;

    for (String letter : letterMap.keySet()) {
      boolean isSelected = letter.equals(selectedLetter);
      JButton btn = buildLetterButton(letter, new Dimension(30, LayoutTheme.get().navBtnH),
          fontSize, isSelected);
      btn.addActionListener(e -> onLetterClick.accept(letter));
      strip.add(btn);
    }

    return strip;
  }

  /**
   * Builds a single AMI 3D-style letter button for the nav strip, matching the visual language of
   * {@link KeyboardPanel#styledKey}. When {@code highlighted} is {@code true} an ACCENT_BLUE neon
   * border is drawn around the button (same as the ABC / 123@ mode-toggle active state).
   */
  static JButton buildLetterButton(String text, Dimension size, int fontSize,
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

        g2.setColor(ColorTheme.get().keyShadow);
        g2.fillRoundRect(1, shadowH, w - 2, visH, arc, arc);

        int shelfH = Math.round(visH * 0.25f);
        int faceH = visH - shelfH;
        g2.setColor(ColorTheme.get().keyShelf);
        g2.fillRoundRect(1, faceH, w - 2, shelfH + arc / 2, arc, arc);

        boolean pressed = getModel().isArmed();
        Color fTop = pressed ? ColorTheme.get().keyFaceBottom : ColorTheme.get().keyFaceTop;
        Color fMid = ColorTheme.get().keyFaceMid;
        Color fBot = pressed ? ColorTheme.get().keyFaceTop : ColorTheme.get().keyFaceBottom;
        g2.setPaint(new LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.55f, 1f},
            new Color[] {fTop, fMid, fBot}));
        g2.fillRoundRect(1, 0, w - 2, faceH + arc / 2, arc, arc);

        g2.setColor(ColorTheme.get().keyHighlight);
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawLine(arc, 1, w - arc - 1, 1);

        g2.setColor(ColorTheme.get().keySide);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.drawLine(1, 2, 1, faceH - 2);
        g2.drawLine(w - 2, 2, w - 2, faceH - 2);

        g2.setFont(getFont());
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = (faceH - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(pressed ? ColorTheme.get().accentBlue : ColorTheme.get().textPrimary);
        g2.drawString(getText(), tx, ty);

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
}
