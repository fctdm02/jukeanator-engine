package com.djt.jukeanator_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;
import com.djt.jukeanator_engine.domain.location.repository.LocationRepository;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumMetaDataFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryFileSystemImpl;

/**
 * Proves the filesystem and JPA {@link SongLibraryRepository} implementations are equivalent:
 * building the same {@link RootFolderEntity} tree once and persisting+reloading it through each
 * backend independently must yield structurally identical trees. This is the concrete guarantee
 * behind "filesystem and database persistence should be interchangeable" -- both backends must
 * round-trip the exact same id/name/numPlays/track-order data, even though their storage shapes
 * (one serialized {@code .oos} file vs. flat {@code song_library} rows) are completely different.
 *
 * <p>The JPA half requires a real MySQL server with a {@code jukeanator_test} database the {@code
 * jukeanator} user can access (see {@code src/test/resources/application-mysql.yml} --
 * deliberately a separate database from {@code application.yml}'s own {@code jukeanator}, which
 * is reserved for manual QA against a master instance running locally), so this class lives in
 * the root package alongside {@link SongLibraryRepositoryJpaImplTest} for the same reason that one
 * does. No Docker/Testcontainers dependency.
 *
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@TestPropertySource(properties = { "app.repository-type=jpa" })
class SongLibraryRepositoryEquivalenceTest {

  // Starts at 2, not 1 -- id 1 is reserved for the root itself (see buildTwoAlbumRoot's
  // root.setId(1)), matching SongScanner's own "root is always the first id" convention.
  private static final Integer GENRE_ID = 2;
  private static final Integer ARTIST_ID = 3;
  private static final Integer ALBUM_ONE_ID = 4;
  private static final Integer SONG_A_ID = 5;
  private static final Integer SONG_B_ID = 6;
  private static final Integer ALBUM_TWO_ID = 7;
  private static final Integer SONG_C_ID = 8;

  @Autowired
  private SongLibraryRepository jpaSongLibraryRepository;

  @Autowired
  private LocationRepository locationRepository;

  @Test
  void filesystemAndJpaRepositories_roundTripTheSameTree_toStructurallyIdenticalResults(
      @TempDir Path filesystemStorageRoot) throws Exception {

    LocationEntity location = registerLocation("Equivalence Test Location");
    RootFolderEntity original = buildTwoAlbumRoot(location);

    SongLibraryRepositoryFileSystemImpl filesystemRepository =
        new SongLibraryRepositoryFileSystemImpl(filesystemStorageRoot.toString());
    filesystemRepository.storeAggregateRoot(original);
    RootFolderEntity filesystemLoaded =
        filesystemRepository.loadAggregateRoot(original.getLocationName());

    jpaSongLibraryRepository.storeAggregateRoot(original);
    RootFolderEntity jpaLoaded =
        jpaSongLibraryRepository.loadAggregateRoot(location.getPersistentIdentity().intValue());

    assertEquals(original.getRootPath(), filesystemLoaded.getRootPath());
    assertEquals(original.getRootPath(), jpaLoaded.getRootPath());

    assertEquals(describeGenres(filesystemLoaded), describeGenres(jpaLoaded));
    assertEquals(describeArtists(filesystemLoaded), describeArtists(jpaLoaded));
    assertEquals(describeAlbums(filesystemLoaded), describeAlbums(jpaLoaded));
    assertEquals(describeSongs(filesystemLoaded), describeSongs(jpaLoaded));
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  private LocationEntity registerLocation(String name) throws EntityDoesNotExistException {

    LocationRootEntity locationRoot = locationRepository.loadAggregateRoot(0);
    Integer locationId = locationRepository.nextPersistentIdentity();
    // Suffixed with the (guaranteed-unique) locationId -- see SongLibraryRepositoryJpaImplTest's
    // identical helper for why: against a live, persistent MySQL instance, a plain fixture name
    // can collide across separate runs or with SongLibraryServiceImpl's own bootstrap default.
    LocationEntity location = new LocationEntity(locationId, name + " " + locationId, 42.4883,
        -83.143, "test-api-key-hash-" + locationId);
    locationRoot.addLocation(location);
    locationRepository.storeAggregateRoot(locationRoot);
    return location;
  }

  private RootFolderEntity buildTwoAlbumRoot(LocationEntity location)
      throws EntityAlreadyExistsException {

    RootFolderEntity root = new RootFolderEntity("/fixture/equivalence");
    root.setId(1);
    root.setParentLocation(location);

    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(GENRE_ID);
    root.addChildFolder(genre);

    ArtistFolderEntity artist = new ArtistFolderEntity(genre, "Artist One");
    artist.setId(ARTIST_ID);
    genre.addChildFolder(artist);

    AlbumFolderEntity albumOne = new AlbumFolderEntity(artist, "Album One");
    albumOne.setId(ALBUM_ONE_ID);
    artist.addChildFolder(albumOne);
    albumOne.createCoverArtEntity();
    albumOne.createMetadataEntity();
    AlbumMetaDataFileEntity albumOneMetadata = albumOne.getMetaData();
    albumOneMetadata.setGenre("Rock");
    albumOneMetadata.setRecordLabel("Test Records");
    albumOneMetadata.setReleaseDate("2020-01-01");
    albumOneMetadata.setHasExplicit(false);
    albumOneMetadata.setLoaded(true);
    albumOne.addChildSong(newSong(albumOne, "01 - Song A.mp3", SONG_A_ID, 1, 3));
    albumOne.addChildSong(newSong(albumOne, "02 - Song B.mp3", SONG_B_ID, 2, 5));

    AlbumFolderEntity albumTwo = new AlbumFolderEntity(artist, "Album Two");
    albumTwo.setId(ALBUM_TWO_ID);
    artist.addChildFolder(albumTwo);
    albumTwo.createCoverArtEntity();
    albumTwo.createMetadataEntity();
    AlbumMetaDataFileEntity albumTwoMetadata = albumTwo.getMetaData();
    albumTwoMetadata.setGenre("Rock");
    albumTwoMetadata.setRecordLabel("Other Records");
    albumTwoMetadata.setReleaseDate("2021-06-15");
    albumTwoMetadata.setHasExplicit(true);
    albumTwoMetadata.setLoaded(true);
    albumTwo.addChildSong(newSong(albumTwo, "01 - Song C.mp3", SONG_C_ID, 1, 1));

    root.initialize();
    return root;
  }

  private SongFileEntity newSong(AlbumFolderEntity album, String filename, Integer songId,
      int trackNumber, int numPlays) {

    SongFileEntity song = new SongFileEntity(album, filename);
    song.setId(songId);
    song.setArtistName("Artist One");
    song.setSongName(filename);
    song.setTrackNumber(trackNumber);
    song.setNumPlays(numPlays);
    return song;
  }

  // ── structural description helpers -- deliberately not relying on any DTO's equals(), several
  // of which compare only by id (see SongDto.equals()), which would be too weak a check here ──

  private List<String> describeGenres(RootFolderEntity root) {
    return root.getGenres().stream().map(g -> g.getId() + "|" + g.getName())
        .sorted().toList();
  }

  private List<String> describeArtists(RootFolderEntity root) {
    return root.getArtists().stream().map(a -> a.getId() + "|" + a.getName())
        .sorted().toList();
  }

  private List<String> describeAlbums(RootFolderEntity root) {
    return root.getAlbums().stream()
        .map(a -> a.getId() + "|" + a.getName() + "|" + a.getParentGenre().getName() + "|"
            + a.getParentArtist().getName() + "|" + a.getMetaData().getGenre() + "|"
            + a.getMetaData().getRecordLabel() + "|" + a.getMetaData().getReleaseDate() + "|"
            + a.getMetaData().hasExplicit())
        .sorted().toList();
  }

  private List<String> describeSongs(RootFolderEntity root) {
    List<String> descriptions = new ArrayList<>();
    for (SongFileEntity song : root.getSongs()) {
      descriptions.add(song.getId() + "|" + song.getAlbum().getId() + "|" + song.getSongName() + "|"
          + song.getArtistName() + "|" + song.getTrackNumber() + "|" + song.getNumPlays());
    }
    return descriptions.stream().sorted(Comparator.naturalOrder()).toList();
  }
}
