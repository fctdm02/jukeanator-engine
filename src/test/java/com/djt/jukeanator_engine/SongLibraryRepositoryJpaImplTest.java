package com.djt.jukeanator_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;
import com.djt.jukeanator_engine.domain.location.repository.LocationRepository;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryJpaImpl;

/**
 * Integration tests for {@link SongLibraryRepositoryJpaImpl}, run against a live local MySQL
 * instance (see {@code application.yml}'s {@code spring.datasource.*} defaults), the same
 * combination already proven out by {@link MySqlMasterModeJukeanatorEngineApplicationTests}.
 *
 * <p>{@code app.repository-type=jpa} is set so the JPA datasource/Hibernate/Flyway stack actually
 * comes up (see {@code JpaDataSourceAutoConfigurationImport}). It's also put to direct use here:
 * {@code song_library.parent_location_id} is a {@code NOT NULL} foreign key into {@code location}
 * (see {@code db/migration/mysql/V8__consolidate_song_library_folders_and_files.sql}), so every
 * fixture root below is built under a real, freshly registered {@link LocationRepository} row
 * rather than an arbitrary int.
 *
 * <p>This class lives in the root package (alongside {@link MySqlTestcontainersConfiguration}
 * and its siblings) rather than under {@code domain.songlibrary.repository}, because that
 * Testcontainers config is package-private.
 *
 * <p>Requires a real MySQL server with a {@code jukeanator_test} database the {@code jukeanator}
 * user can access -- see {@code src/test/resources/application-test.yml}. Deliberately a
 * separate database from {@code application.yml}'s own {@code jukeanator}, which is reserved for
 * manual QA against a master instance running locally. No Docker/Testcontainers dependency.
 *
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.repository-type=jpa" })
class SongLibraryRepositoryJpaImplTest {

  // GENRE_ID starts at 2, not 1 -- id 1 is reserved for the root itself (see buildTwoAlbumRoot's
  // root.setId(1)), matching SongScanner's own "root is always the first id" convention.
  private static final Integer GENRE_ID = 2;
  private static final Integer ARTIST_ID = 101;
  private static final Integer ALBUM_ONE_ID = 501;
  private static final Integer ALBUM_TWO_ID = 502;
  private static final Integer SONG_A_ID = 9001; // under album one
  private static final Integer SONG_B_ID = 9002; // under album one
  private static final Integer SONG_C_ID = 9003; // under album two

  @Autowired
  private SongLibraryRepository songLibraryRepository;

  @Autowired
  private LocationRepository locationRepository;

  @Autowired
  private DataSource dataSource;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Autowired
  private PlatformTransactionManager transactionManager;

  // ── updateNumPlaysForSong ────────────────────────────────────────────────

  @Test
  void updateNumPlaysForSong_updatesOnlyTheTargetSongRow_leavingEveryOtherRowsIdUntouched()
      throws Exception {

    Integer locationId = registerLocation("Rock On Third");
    RootFolderEntity root = buildTwoAlbumRoot(locationId);
    songLibraryRepository.storeAggregateRoot(root);

    Set<Integer> folderIdsBefore = folderIds(locationId);
    Set<Integer> songRowIdsBefore = songRowIds(locationId);

    Integer result =
        songLibraryRepository.updateNumPlaysForSong(root, locationId, ALBUM_ONE_ID, SONG_A_ID, 42);

    assertEquals(Integer.valueOf(42), result);
    assertEquals(Integer.valueOf(42), numPlaysOf(locationId, SONG_A_ID));
    assertEquals(Integer.valueOf(5), numPlaysOf(locationId, SONG_B_ID),
        "Sibling song in the same album should be untouched");
    assertEquals(Integer.valueOf(1), numPlaysOf(locationId, SONG_C_ID),
        "Song in a different album should be untouched");

    // id is application-assigned (SongScanner), not Hibernate-generated, so it never churns
    // regardless of write path -- this just confirms the targeted UPDATE only touches the one
    // song row's num_plays column, not its id or any other row.
    assertEquals(folderIdsBefore, folderIds(locationId),
        "Folder rows' ids should be completely unaffected by a song numPlays update");
    assertEquals(songRowIdsBefore, songRowIds(locationId),
        "Song rows' ids should be completely unaffected by a song numPlays update");
  }

  @Test
  void updateNumPlaysForSong_isVisibleOnReload() throws Exception {

    Integer locationId = registerLocation("Rox on 3rd");
    RootFolderEntity root = buildTwoAlbumRoot(locationId);
    songLibraryRepository.storeAggregateRoot(root);

    songLibraryRepository.updateNumPlaysForSong(root, locationId, ALBUM_ONE_ID, SONG_A_ID, 7);

    RootFolderEntity reloaded = songLibraryRepository.loadAggregateRoot(locationId.intValue());
    assertEquals(Integer.valueOf(7), reloaded.getSongById(ALBUM_ONE_ID, SONG_A_ID).getNumPlays());
    assertEquals(Integer.valueOf(5), reloaded.getSongById(ALBUM_ONE_ID, SONG_B_ID).getNumPlays());
  }

  @Test
  void updateNumPlaysForSong_throws_whenSongDoesNotExist() throws Exception {

    Integer locationId = registerLocation("No Such Song Loc");
    RootFolderEntity root = buildTwoAlbumRoot(locationId);
    songLibraryRepository.storeAggregateRoot(root);

    assertThrows(EntityDoesNotExistException.class, () -> songLibraryRepository
        .updateNumPlaysForSong(root, locationId, ALBUM_ONE_ID, 999999, 1));
  }

  @Test
  void updateNumPlaysForSong_throws_whenAlbumIdDoesNotMatchTheSongsRealAlbum() throws Exception {

    Integer locationId = registerLocation("Wrong Album Loc");
    RootFolderEntity root = buildTwoAlbumRoot(locationId);
    songLibraryRepository.storeAggregateRoot(root);

    // SONG_C_ID really lives under ALBUM_TWO_ID -- asking to update it under ALBUM_ONE_ID must not
    // match, proving the update's subquery join actually constrains by album, not just by song
    // sourceId.
    assertThrows(EntityDoesNotExistException.class, () -> songLibraryRepository
        .updateNumPlaysForSong(root, locationId, ALBUM_ONE_ID, SONG_C_ID, 1));
    assertEquals(Integer.valueOf(1), numPlaysOf(locationId, SONG_C_ID),
        "A mismatched-album update should not have touched the song's row");
  }

  @Test
  void updateNumPlaysForSong_throws_andNeverCrossesTenants_whenLocationIdIsAnotherLocation()
      throws Exception {

    Integer locationIdA = registerLocation("Tenant A");
    Integer locationIdB = registerLocation("Tenant B");

    RootFolderEntity rootA = buildTwoAlbumRoot(locationIdA);
    songLibraryRepository.storeAggregateRoot(rootA);

    assertThrows(EntityDoesNotExistException.class, () -> songLibraryRepository
        .updateNumPlaysForSong(rootA, locationIdB, ALBUM_ONE_ID, SONG_A_ID, 999));
    assertEquals(Integer.valueOf(3), numPlaysOf(locationIdA, SONG_A_ID),
        "A mismatched locationId must never update another tenant's row");
  }

  @Test
  void updateNumPlaysForSong_updatesTheInMemoryRootCache_soStoreSongLibraryAsyncNoLongerThrows()
      throws Exception {

    Integer locationId = registerLocation("Cache Sync Loc");
    RootFolderEntity root = buildTwoAlbumRoot(locationId);
    songLibraryRepository.storeAggregateRoot(root);

    // A brand new instance never had loadAggregateRoot/storeAggregateRoot called on it -- its
    // internal root cache starts null, so storeSongLibraryAsync() throws immediately.
    SongLibraryRepositoryJpaImpl freshRepository =
        new SongLibraryRepositoryJpaImpl(entityManagerFactory, transactionManager);
    assertThrows(EntityDoesNotExistException.class, freshRepository::storeSongLibraryAsync);

    freshRepository.updateNumPlaysForSong(root, locationId, ALBUM_ONE_ID, SONG_A_ID, 11);

    // No exception now that updateNumPlaysForSong has populated the cache as a side effect.
    freshRepository.storeSongLibraryAsync();
  }

  // ── id stability across write paths ─────────────────────────────────────

  @Test
  void storeAggregateRoot_preservesEveryIdOnReStore_sinceIdIsApplicationAssignedNotGenerated()
      throws Exception {

    Integer locationId = registerLocation("Re-store Id Stability Loc");
    RootFolderEntity root = buildTwoAlbumRoot(locationId);
    songLibraryRepository.storeAggregateRoot(root);

    Set<Integer> folderIdsAfterFirstStore = folderIds(locationId);
    Set<Integer> songRowIdsAfterFirstStore = songRowIds(locationId);

    // Unlike the old split-table schema (id an auto-generated GenerationType.SEQUENCE surrogate,
    // distinct from the domain object's own scan-local sourceId), id is now assigned once by
    // SongScanner and carried directly on the domain objects -- a full delete+reinsert of the
    // exact same tree re-persists the exact same ids, not fresh ones.
    songLibraryRepository.storeAggregateRoot(root);

    assertEquals(folderIdsAfterFirstStore, folderIds(locationId),
        "id is application-assigned now, not Hibernate-generated -- a full re-store must not "
            + "change any folder row's id");
    assertEquals(songRowIdsAfterFirstStore, songRowIds(locationId),
        "id is application-assigned now, not Hibernate-generated -- a full re-store must not "
            + "change any song row's id");

    // updateNumPlaysForSong remains preferable to a full re-store -- not for id stability (both
    // now preserve ids identically) but because it's a single targeted UPDATE rather than
    // deleting and reinserting the entire tree on every song play.
    songLibraryRepository.updateNumPlaysForSong(root, locationId, ALBUM_ONE_ID, SONG_A_ID, 100);
    assertEquals(songRowIdsAfterFirstStore, songRowIds(locationId));
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  private Integer registerLocation(String name) throws EntityDoesNotExistException {

    LocationRootEntity locationRoot = locationRepository.loadAggregateRoot(0);
    Integer locationId = locationRepository.nextPersistentIdentity();
    // Suffixed with the (guaranteed-unique) locationId: against a live, persistent MySQL instance
    // (unlike an ephemeral Testcontainer, this database survives across separate test classes and
    // runs), a plain fixture name can collide with either another test class's own fixture (see
    // SongQueueRepositoryJpaImplTest's identical "Tenant A"/"Tenant B" names) or with the default
    // location SongLibraryServiceImpl's own bootstrap creates automatically on every context load.
    locationRoot.addLocation(new LocationEntity(locationId, name + " " + locationId, null, null,
        "test-api-key-hash-" + locationId));
    locationRepository.storeAggregateRoot(locationRoot);
    return locationId;
  }

  private RootFolderEntity buildTwoAlbumRoot(Integer locationId)
      throws EntityAlreadyExistsException {

    RootFolderEntity root = new RootFolderEntity("/fixture/" + locationId);
    root.setId(1);
    try {
      LocationRootEntity locationRoot = locationRepository.loadAggregateRoot(0);
      root.setParentLocation(locationRoot.getLocationByIdNullIfNotExists(locationId));
    } catch (EntityDoesNotExistException ednee) {
      throw new IllegalStateException("registerLocation() must be called before buildTwoAlbumRoot()",
          ednee);
    }

    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(GENRE_ID);
    root.addChildFolder(genre);

    ArtistFolderEntity artist = new ArtistFolderEntity(genre, "Artist One");
    artist.setId(ARTIST_ID);
    genre.addChildFolder(artist);

    AlbumFolderEntity albumOne = new AlbumFolderEntity(artist, "Album One");
    albumOne.setId(ALBUM_ONE_ID);
    artist.addChildFolder(albumOne);
    albumOne.addChildSong(newSong(albumOne, "01 - Song A.mp3", SONG_A_ID, 1, 3));
    albumOne.addChildSong(newSong(albumOne, "02 - Song B.mp3", SONG_B_ID, 2, 5));

    AlbumFolderEntity albumTwo = new AlbumFolderEntity(artist, "Album Two");
    albumTwo.setId(ALBUM_TWO_ID);
    artist.addChildFolder(albumTwo);
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

  // ── raw-JDBC row introspection -- bypasses the repository's own EntityManager/1st-level cache,
  // so it reflects exactly what's on disk ────────────────────────────────────────────────────

  private Set<Integer> folderIds(Integer locationId) throws SQLException {
    return songLibraryIds(locationId, "class_discriminator <> 'SONG'");
  }

  private Set<Integer> songRowIds(Integer locationId) throws SQLException {
    return songLibraryIds(locationId, "class_discriminator = 'SONG'");
  }

  private Set<Integer> songLibraryIds(Integer locationId, String discriminatorFilter)
      throws SQLException {

    Set<Integer> ids = new HashSet<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select id from song_library where parent_location_id = ? and " + discriminatorFilter)) {

      statement.setInt(1, locationId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          ids.add(resultSet.getInt(1));
        }
      }
    }
    return ids;
  }

  private Integer numPlaysOf(Integer locationId, Integer songId) throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select song_num_plays from song_library where parent_location_id = ? "
                + "and class_discriminator = 'SONG' and id = ?")) {

      statement.setInt(1, locationId);
      statement.setInt(2, songId);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        int value = resultSet.getInt(1);
        return resultSet.wasNull() ? null : Integer.valueOf(value);
      }
    }
  }
}
