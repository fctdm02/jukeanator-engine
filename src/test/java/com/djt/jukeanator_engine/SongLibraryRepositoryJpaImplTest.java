package com.djt.jukeanator_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.DockerClientFactory;
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
 * Integration tests for {@link SongLibraryRepositoryJpaImpl}, run against a real MySQL
 * Testcontainer (see {@link MySqlTestcontainersConfiguration}), the same combination already
 * proven out by {@link MySqlMasterModeJukeanatorEngineApplicationTests}.
 *
 * <p>{@code location.repository-type=jpa} is set purely to satisfy {@code
 * JpaRepositoryRequiredCondition} -- it only looks at {@code user}/{@code location}
 * repository-type, not {@code song-library}'s -- so the JPA datasource/Hibernate/Flyway stack
 * actually comes up. It's also put to direct use here: {@code song_library_folders.location_id}
 * and {@code song_library_files.location_id} are both {@code NOT NULL} foreign keys into {@code
 * locations} (see {@code db/migration/mysql/V3__init_song_library_schema.sql}), so every fixture
 * root below is built under a real, freshly registered {@link LocationRepository} row rather than
 * an arbitrary int.
 *
 * <p>This class lives in the root package (alongside {@link MySqlTestcontainersConfiguration}
 * and its siblings) rather than under {@code domain.songlibrary.repository}, because that
 * Testcontainers config is package-private.
 *
 * @author tmyers
 */
@Import(MySqlTestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@TestPropertySource(
    properties = { "song-library.repository-type=jpa", "location.repository-type=jpa" })
class SongLibraryRepositoryJpaImplTest {

  private static final Integer GENRE_ID = 1;
  private static final Integer ARTIST_ID = 101;
  private static final Integer ALBUM_ONE_ID = 501;
  private static final Integer ALBUM_TWO_ID = 502;
  private static final Integer SONG_A_ID = 9001; // under album one
  private static final Integer SONG_B_ID = 9002; // under album one
  private static final Integer SONG_C_ID = 9003; // under album two

  @BeforeAll
  static void requiresDocker() {
    Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required to run this test");
  }

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
  void updateNumPlaysForSong_updatesOnlyTheTargetSongRow_leavingEverySurrogateKeyUntouched()
      throws Exception {

    Integer locationId = registerLocation("Rock On Third");
    RootFolderEntity root = buildTwoAlbumRoot(locationId);
    songLibraryRepository.storeAggregateRoot(root);

    Set<Integer> folderIdsBefore = folderPersistentIdentities(locationId);
    Set<Integer> fileIdsBefore = filePersistentIdentities(locationId);

    Integer result =
        songLibraryRepository.updateNumPlaysForSong(root, locationId, ALBUM_ONE_ID, SONG_A_ID, 42);

    assertEquals(Integer.valueOf(42), result);
    assertEquals(Integer.valueOf(42), numPlaysOf(locationId, SONG_A_ID));
    assertEquals(Integer.valueOf(5), numPlaysOf(locationId, SONG_B_ID),
        "Sibling song in the same album should be untouched");
    assertEquals(Integer.valueOf(1), numPlaysOf(locationId, SONG_C_ID),
        "Song in a different album should be untouched");

    // The whole point of a targeted UPDATE over storeAggregateRoot's delete+reinsert is that no
    // row's surrogate key churns: every persistent_identity present before must still be present,
    // completely unchanged, after.
    assertEquals(folderIdsBefore, folderPersistentIdentities(locationId),
        "Folder rows' surrogate keys should be completely unaffected by a song numPlays update");
    assertEquals(fileIdsBefore, filePersistentIdentities(locationId),
        "File rows' surrogate keys should be completely unaffected by a song numPlays update");
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

  // ── validating the targeted-UPDATE strategy against a whole-root store ─────────────────────

  @Test
  void storeAggregateRoot_churnsEverySurrogateKey_unlikeTargetedUpdateNumPlaysForSong()
      throws Exception {

    Integer locationId = registerLocation("Churn Comparison Loc");
    RootFolderEntity root = buildTwoAlbumRoot(locationId);
    songLibraryRepository.storeAggregateRoot(root);

    Set<Integer> folderIdsAfterFirstStore = folderPersistentIdentities(locationId);
    Set<Integer> fileIdsAfterFirstStore = filePersistentIdentities(locationId);

    // This is what the old code path did for every single numPlays bump: delete every row for the
    // location and reinsert the whole tree. Simulate that here to make the contrast concrete.
    songLibraryRepository.storeAggregateRoot(root);

    Set<Integer> folderIdsAfterSecondStore = folderPersistentIdentities(locationId);
    Set<Integer> fileIdsAfterSecondStore = filePersistentIdentities(locationId);

    assertEquals(folderIdsAfterFirstStore.size(), folderIdsAfterSecondStore.size());
    assertEquals(fileIdsAfterFirstStore.size(), fileIdsAfterSecondStore.size());
    assertTrue(Collections.disjoint(folderIdsAfterFirstStore, folderIdsAfterSecondStore),
        "storeAggregateRoot deletes and reinserts every folder row, so every surrogate key "
            + "should be brand new -- churn a full re-store would inflict for a single song's "
            + "numPlays bump");
    assertTrue(Collections.disjoint(fileIdsAfterFirstStore, fileIdsAfterSecondStore),
        "storeAggregateRoot deletes and reinserts every file row, so every surrogate key should "
            + "be brand new -- churn a full re-store would inflict for a single song's numPlays "
            + "bump");

    // Contrast: a numPlays bump via the targeted UPDATE leaves every key exactly as it was.
    Set<Integer> fileIdsBeforeTargetedUpdate = filePersistentIdentities(locationId);
    songLibraryRepository.updateNumPlaysForSong(root, locationId, ALBUM_ONE_ID, SONG_A_ID, 100);
    assertEquals(fileIdsBeforeTargetedUpdate, filePersistentIdentities(locationId),
        "Unlike storeAggregateRoot, updateNumPlaysForSong must not reassign any surrogate key");
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  private Integer registerLocation(String name) throws EntityDoesNotExistException {

    LocationRootEntity locationRoot = locationRepository.loadAggregateRoot(0);
    Integer locationId = locationRepository.nextPersistentIdentity();
    locationRoot.addLocation(
        new LocationEntity(locationId, name, null, null, "test-api-key-hash-" + locationId));
    locationRepository.storeAggregateRoot(locationRoot);
    return locationId;
  }

  private RootFolderEntity buildTwoAlbumRoot(Integer locationId)
      throws EntityAlreadyExistsException {

    RootFolderEntity root = new RootFolderEntity("/fixture/" + locationId);
    root.getMetadata().setLocationId(locationId);
    root.getMetadata().setLocationName("Location " + locationId);
    // Avoids a real filesystem read/write against a rootPath that doesn't exist on this machine --
    // see SongLibraryRepositoryJpaImpl's class javadoc "Caller contract for a synthetically-built
    // root".
    root.getMetadata().setLoaded(true);

    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setPersistentIdentity(GENRE_ID);
    root.addChildFolder(genre);

    ArtistFolderEntity artist = new ArtistFolderEntity(genre, "Artist One");
    artist.setPersistentIdentity(ARTIST_ID);
    genre.addChildFolder(artist);

    AlbumFolderEntity albumOne = new AlbumFolderEntity(artist, "Album One");
    albumOne.setPersistentIdentity(ALBUM_ONE_ID);
    artist.addChildFolder(albumOne);
    albumOne.addChildSong(newSong(albumOne, "01 - Song A.mp3", SONG_A_ID, 1, 3));
    albumOne.addChildSong(newSong(albumOne, "02 - Song B.mp3", SONG_B_ID, 2, 5));

    AlbumFolderEntity albumTwo = new AlbumFolderEntity(artist, "Album Two");
    albumTwo.setPersistentIdentity(ALBUM_TWO_ID);
    artist.addChildFolder(albumTwo);
    albumTwo.addChildSong(newSong(albumTwo, "01 - Song C.mp3", SONG_C_ID, 1, 1));

    root.initialize();
    return root;
  }

  private SongFileEntity newSong(AlbumFolderEntity album, String filename, Integer songId,
      int trackNumber, int numPlays) {

    SongFileEntity song = new SongFileEntity(album, filename);
    song.setPersistentIdentity(songId);
    song.setArtistName("Artist One");
    song.setSongName(filename);
    song.setTrackNumber(trackNumber);
    song.setNumPlays(numPlays);
    return song;
  }

  // ── raw-JDBC row introspection -- bypasses the repository's own EntityManager/1st-level cache,
  // so it reflects exactly what's on disk ────────────────────────────────────────────────────

  private Set<Integer> folderPersistentIdentities(Integer locationId) throws SQLException {
    return persistentIdentities("song_library_folders", locationId);
  }

  private Set<Integer> filePersistentIdentities(Integer locationId) throws SQLException {
    return persistentIdentities("song_library_files", locationId);
  }

  private Set<Integer> persistentIdentities(String table, Integer locationId) throws SQLException {

    Set<Integer> ids = new HashSet<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection
            .prepareStatement("select persistent_identity from " + table + " where location_id = ?")) {

      statement.setInt(1, locationId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          ids.add(resultSet.getInt(1));
        }
      }
    }
    return ids;
  }

  private Integer numPlaysOf(Integer locationId, Integer songSourceId) throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select num_plays from song_library_files where location_id = ? "
                + "and file_type = 'SONG' and source_id = ?")) {

      statement.setInt(1, locationId);
      statement.setInt(2, songSourceId);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        int value = resultSet.getInt(1);
        return resultSet.wasNull() ? null : Integer.valueOf(value);
      }
    }
  }
}
