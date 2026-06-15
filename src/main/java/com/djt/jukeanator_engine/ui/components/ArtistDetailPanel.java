package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;

public class ArtistDetailPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public ArtistDetailPanel(ArtistDto artist, ImageLoader imageLoader, int gridCols, int gridRows,
      int artW, int artH, String backLabel, Runnable onBack,
      AlbumGridPanel.AlbumClickListener onAlbumClicked) {

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);

    List<AlbumDto> rawAlbums = artist.getAlbums() != null ? artist.getAlbums() : List.of();

    // Sort alphabetically by album name, symbols/numbers first — mirrors HomePanel ordering
    List<AlbumDto> albums = new ArrayList<>(rawAlbums);
    albums.sort(Comparator.comparing(a -> {
      String name = a.getAlbumName();
      if (name == null || name.isBlank())
        return "\uFFFF";
      char first = Character.toUpperCase(name.charAt(0));
      return Character.isLetter(first) ? ("~" + name.toUpperCase()) : name.toUpperCase();
    }));

    // Build the letter → albums map required by AlbumGridPanel
    Map<String, List<AlbumDto>> letterMap = new LinkedHashMap<>();
    letterMap.put("#", new ArrayList<>());
    for (char c = 'A'; c <= 'Z'; c++) {
      letterMap.put(String.valueOf(c), new ArrayList<>());
    }
    for (AlbumDto album : albums) {
      String name = album.getAlbumName();
      if (name == null || name.isBlank()) {
        letterMap.get("#").add(album);
        continue;
      }
      char first = Character.toUpperCase(name.charAt(0));
      if (Character.isLetter(first)) {
        letterMap.get(String.valueOf(first)).add(album);
      } else {
        letterMap.get("#").add(album);
      }
    }
    letterMap.entrySet().removeIf(e -> e.getValue().isEmpty());

    ImageIcon artistImage = null;
    if (artist.getCoverArtPath() != null) {
      try {
        artistImage = imageLoader.loadFilesystemImage(artist.getCoverArtPath(), 72, 72);
      } catch (Exception ignored) {
      }
    }

    int numAlbums = artist.getAlbums() != null ? artist.getAlbums().size() : 0;
    int numSongs = artist.getSongCount() != null ? artist.getSongCount() : 0;

    String subtitle = numAlbums + " albums  •  " + numSongs + " songs";

    add(new DetailHeaderPanel(backLabel, onBack, artistImage, "♪", artist.getArtistName(),
        subtitle), BorderLayout.NORTH);

    add(new AlbumGridPanel(albums, letterMap, imageLoader, gridCols, gridRows, artW, artH,
        onAlbumClicked), BorderLayout.CENTER);
  }
}
