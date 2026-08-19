package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base fixture for tests that operate on entities drawn from a populated in-memory song library.
 *
 * <p>Library structure:
 *
 * <pre>
 * Root "/test-root"
 * ├── Genre "Rock"   (genreId=1)
 * │   ├── Artist "Led Zeppelin"   (artistId=1)
 * │   │   ├── Album "Led Zeppelin IV"      (albumId=2)
 * │   │   │   ├── Track 1: "Led Zeppelin-01-Black Dog.mp3"          (songId=0)
 * │   │   │   └── Track 2: "Led Zeppelin-02-Rock and Roll.mp3"      (songId=1)
 * │   │   └── Album "Physical Graffiti"    (albumId=3)
 * │   │       ├── Track 1: "Led Zeppelin-01-Kashmir.mp3"             (songId=0)
 * │   │       └── Track 2: "Led Zeppelin-02-Houses of the Holy.mp3" (songId=1)
 * │   └── Artist "Compilations"
 * │       └── Album "Classic Rock Mix"     (albumId=1)
 * │           ├── Track 1: "Led Zeppelin-01-Whole Lotta Love.mp3"   (songId=0)
 * │           └── Track 2: "AC DC-02-Back in Black.mp3"             (songId=1)
 * └── Genre "Pop"    (genreId=0)
 *     └── Artist "Madonna"   (artistId=0)
 *         └── Album "Like a Virgin"        (albumId=0)
 *             ├── Track 1: "Madonna-01-Material Girl.mp3"           (songId=0)
 *             └── Track 2: "Madonna-02-Like a Virgin.mp3"           (songId=1)
 * </pre>
 *
 * Album IDs are assigned in ascending file-path order:
 * <ol>
 *   <li>{@code /test-root/Pop/Madonna/Like a Virgin} → albumId=0
 *   <li>{@code /test-root/Rock/Compilations/Classic Rock Mix} → albumId=1
 *   <li>{@code /test-root/Rock/Led Zeppelin/Led Zeppelin IV} → albumId=2
 *   <li>{@code /test-root/Rock/Led Zeppelin/Physical Graffiti} → albumId=3
 * </ol>
 */
public abstract class AbstractSongLibraryEntityTest {

  protected static final String ROOT_PATH = "/test-root";

  protected static final int ALBUM_ID_LIKE_A_VIRGIN = 0;
  protected static final int ALBUM_ID_CLASSIC_ROCK_MIX = 1;
  protected static final int ALBUM_ID_LED_ZEPPELIN_IV = 2;
  protected static final int ALBUM_ID_PHYSICAL_GRAFFITI = 3;

  protected static final int GENRE_ID_POP = 0;
  protected static final int GENRE_ID_ROCK = 1;

  protected RootFolderEntity root;

  protected AlbumFolderEntity albumLedZeppelinIV;
  protected SongFileEntity songBlackDog;
  protected SongFileEntity songRockAndRoll;
  protected SongFileEntity songKashmir;
  protected SongFileEntity songMaterialGirl;
  protected SongFileEntity songLikeAVirgin;

  @BeforeEach
  void setUpLibrary() throws Exception {

    root = new RootFolderEntity(ROOT_PATH);

    GenreFolderEntity genreRock = new GenreFolderEntity(root, "Rock");
    root.addChildFolder(genreRock);

    ArtistFolderEntity artistLedZeppelin = new ArtistFolderEntity(genreRock, "Led Zeppelin");
    genreRock.addChildFolder(artistLedZeppelin);

    albumLedZeppelinIV = buildAlbum(artistLedZeppelin, "Led Zeppelin IV");
    songBlackDog =
        buildSong(albumLedZeppelinIV, "Led Zeppelin-01-Black Dog.mp3", "Led Zeppelin", "Black Dog", 1, 0);
    songRockAndRoll =
        buildSong(albumLedZeppelinIV, "Led Zeppelin-02-Rock and Roll.mp3", "Led Zeppelin", "Rock and Roll", 2, 1);

    AlbumFolderEntity albumPhysicalGraffiti = buildAlbum(artistLedZeppelin, "Physical Graffiti");
    songKashmir =
        buildSong(albumPhysicalGraffiti, "Led Zeppelin-01-Kashmir.mp3", "Led Zeppelin", "Kashmir", 1, 0);
    buildSong(albumPhysicalGraffiti, "Led Zeppelin-02-Houses of the Holy.mp3", "Led Zeppelin",
        "Houses of the Holy", 2, 1);

    ArtistFolderEntity artistCompilations = new ArtistFolderEntity(genreRock, "Compilations");
    genreRock.addChildFolder(artistCompilations);

    AlbumFolderEntity albumClassicRockMix = buildAlbum(artistCompilations, "Classic Rock Mix");
    buildSong(albumClassicRockMix, "Led Zeppelin-01-Whole Lotta Love.mp3", "Led Zeppelin",
        "Whole Lotta Love", 1, 0);
    buildSong(albumClassicRockMix, "AC DC-02-Back in Black.mp3", "AC DC", "Back in Black", 2, 1);

    GenreFolderEntity genrePop = new GenreFolderEntity(root, "Pop");
    root.addChildFolder(genrePop);

    ArtistFolderEntity artistMadonna = new ArtistFolderEntity(genrePop, "Madonna");
    genrePop.addChildFolder(artistMadonna);

    AlbumFolderEntity albumLikeAVirgin = buildAlbum(artistMadonna, "Like a Virgin");
    songMaterialGirl =
        buildSong(albumLikeAVirgin, "Madonna-01-Material Girl.mp3", "Madonna", "Material Girl", 1, 0);
    songLikeAVirgin =
        buildSong(albumLikeAVirgin, "Madonna-02-Like a Virgin.mp3", "Madonna", "Like a Virgin", 2, 1);

    assignIds();
    root.initialize();
  }

  protected AlbumFolderEntity buildAlbum(FolderEntity parentFolder, String albumName)
      throws Exception {
    AlbumFolderEntity album = new AlbumFolderEntity(parentFolder, albumName);
    parentFolder.addChildFolder(album);
    album.createCoverArtEntity();
    album.createMetadataEntity();
    return album;
  }

  protected SongFileEntity buildSong(AlbumFolderEntity album, String filename, String artistName,
      String songName, int trackNumber, int songId) throws Exception {
    SongFileEntity song = new SongFileEntity(album, filename);
    song.setArtistName(artistName);
    song.setSongName(songName);
    song.setTrackNumber(trackNumber);
    song.setPersistentIdentity(songId);
    album.addChildSong(song);
    return song;
  }

  private void assignIds() {
    List<AlbumFolderEntity> albums = root.getAllAlbums();
    int genreIndex = 0;
    int artistIndex = 0;

    for (int i = 0; i < albums.size(); i++) {
      AlbumFolderEntity album = albums.get(i);
      album.setPersistentIdentity(i);

      GenreFolderEntity genre = album.getParentGenre();
      if (genre != null && genre.getPersistentIdentity() == null) {
        genre.setPersistentIdentity(genreIndex++);
      }

      ArtistFolderEntity artist = album.getParentArtist();
      if (artist != null && artist.getPersistentIdentity() == null) {
        artist.setPersistentIdentity(artistIndex++);
      }
    }
  }
}
