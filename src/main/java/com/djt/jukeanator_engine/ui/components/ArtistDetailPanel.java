package com.djt.jukeanator_engine.ui.components;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;

public class ArtistDetailPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  // ─────────────────────────────────────────────────────────────────────────
  // CONSTRUCTOR
  // ─────────────────────────────────────────────────────────────────────────
  public ArtistDetailPanel(ArtistDto artist, ImageLoader imageLoader,
      LayoutTheme.GridProfile albumGridProfile, String backLabel, Runnable onBack,
      AlbumGridPanel.AlbumClickListener onAlbumClicked) {

    setLayout(new BorderLayout(0, 0));
    setOpaque(false);

    // Albums arrive pre-sorted by popularity (numPlays descending) from
    // SongLibraryMapper.toArtistDto().
    // Do not re-sort here; preserve that ordering for display.
    List<AlbumDto> albums = artist.albums() != null ? artist.albums() : List.of();

    ImageIcon artistImage = null;
    if (artist.coverArtPath() != null) {
      try {
        artistImage = imageLoader.loadFilesystemImage(artist.coverArtPath(), 72, 72);
      } catch (Exception ignored) {
      }
    }

    int numAlbums = artist.albums() != null ? artist.albums().size() : 0;
    int numSongs = artist.songCount() != null ? artist.songCount() : 0;

    String subtitle = numAlbums + " albums  •  " + numSongs + " songs";

    add(new DetailHeaderPanel(backLabel, onBack, artistImage, "♪", artist.artistName(),
        subtitle), BorderLayout.NORTH);

    // Unpack the profile here — AlbumGridPanel still receives four ints so its
    // own API is unchanged. Only the call-site (ArtistDetailPanel) simplifies.
    add(new AlbumGridPanel(albums, null, imageLoader, albumGridProfile, onAlbumClicked, false, null),
        BorderLayout.CENTER);
  }
}
