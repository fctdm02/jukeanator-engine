package com.djt.jukeanator_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryJpaImpl;

/**
 * Integration tests for {@link SongQueueRepositoryJpaImpl}, run against a real MySQL
 * Testcontainer (see {@link MySqlTestcontainersConfiguration}), the same combination already
 * proven out by {@link SongLibraryRepositoryJpaImplTest}.
 *
 * <p>Unlike {@code SongLibraryRepositoryJpaImplTest} (which autowires {@code SongLibraryRepository}
 * directly, since locationId is just a method parameter there), {@link SongQueueRepositoryJpaImpl}
 * resolves its own {@code locationId} internally via {@link SongLibraryService#getOwnLocationId()}
 * -- the same collaborator {@code SongQueueRepositoryFileSystemImpl} depends on to resolve queued
 * songs. So every test here constructs the repository directly (autowiring only the shared
 * {@code EntityManagerFactory}/{@code PlatformTransactionManager} from the Testcontainer-backed
 * context) against a mocked {@link SongLibraryService}, letting each test pin down exactly which
 * locationId and library fixture it's exercising without needing a real filesystem scan.
 *
 * <p>{@code location.repository-type=jpa} is set purely so {@code song_queue_entries.location_id}'s
 * {@code NOT NULL} foreign key into {@code locations} (see {@code
 * db/migration/mysql/V5__init_song_queue_schema.sql}) has a real row to point at, exactly as
 * {@code SongLibraryRepositoryJpaImplTest} does for the song-library tables' same FK.
 * {@code song-queue.repository-type=jpa} is set to additionally satisfy {@code
 * JpaRepositoryRequiredCondition} via the branch this refactor added for it.
 *
 * @author tmyers
 */
@Import(MySqlTestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@TestPropertySource(
    properties = { "song-queue.repository-type=jpa", "location.repository-type=jpa" })
class SongQueueRepositoryJpaImplTest {

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
  private LocationRepository locationRepository;

  @Autowired
  private DataSource dataSource;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Autowired
  private PlatformTransactionManager transactionManager;

  // ── round-trip ───────────────────────────────────────────────────────────

  @Test
  void storeAggregateRoot_thenLoadAggregateRoot_roundTripsEveryFieldInPriorityOrder()
      throws Exception {

    Integer locationId = registerLocation("Round Trip Loc");
    RootFolderEntity libraryRoot = buildTwoAlbumRoot();
    SongQueueRepositoryJpaImpl repository = newRepository(locationId, libraryRoot);

    SongQueueRootEntity root = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    SongFileEntity songA = libraryRoot.getSongById(ALBUM_ONE_ID, SONG_A_ID);
    SongFileEntity songB = libraryRoot.getSongById(ALBUM_ONE_ID, SONG_B_ID);
    root.addSongToQueue("alice@example.com", songA, 5);
    root.addSongToQueue("bob@example.com", songB, 10); // higher priority -> sorts first

    repository.storeAggregateRoot(root);

    SongQueueRootEntity reloaded =
        repository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);

    assertEquals(2, reloaded.getSongs().size());
    SongQueueEntryEntity first = reloaded.getSongs().get(0);
    SongQueueEntryEntity second = reloaded.getSongs().get(1);

    assertEquals("bob@example.com", first.getUsername());
    assertEquals(Integer.valueOf(10), first.getPriority());
    assertEquals(songB, first.getSong());

    assertEquals("alice@example.com", second.getUsername());
    assertEquals(Integer.valueOf(5), second.getPriority());
    assertEquals(songA, second.getSong());
  }

  @Test
  void storeAggregateRoot_preservesManuallyReorderedPosition_acrossAReload() throws Exception {

    Integer locationId = registerLocation("Reorder Loc");
    RootFolderEntity libraryRoot = buildTwoAlbumRoot();
    SongQueueRepositoryJpaImpl repository = newRepository(locationId, libraryRoot);

    SongQueueRootEntity root = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    SongFileEntity songA = libraryRoot.getSongById(ALBUM_ONE_ID, SONG_A_ID);
    SongFileEntity songB = libraryRoot.getSongById(ALBUM_ONE_ID, SONG_B_ID);
    SongFileEntity songC = libraryRoot.getSongById(ALBUM_TWO_ID, SONG_C_ID);
    // All the same priority, so insertion order alone decides position -- moveSongUpInQueue below
    // is the only thing that can change it, and does so independent of priority/queuedAtTime.
    root.addSongToQueue("u1", songA, 1);
    root.addSongToQueue("u2", songB, 1);
    root.addSongToQueue("u3", songC, 1);

    // Move songC (currently last) up to the front.
    root.moveSongUpInQueue(songC);
    root.moveSongUpInQueue(songC);
    List<String> expectedOrder = new ArrayList<>();
    for (SongQueueEntryEntity entry : root.getSongs()) {
      expectedOrder.add(entry.getSong().getNaturalIdentity());
    }

    repository.storeAggregateRoot(root);

    SongQueueRootEntity reloaded =
        repository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    List<String> actualOrder = new ArrayList<>();
    for (SongQueueEntryEntity entry : reloaded.getSongs()) {
      actualOrder.add(entry.getSong().getNaturalIdentity());
    }

    assertEquals(expectedOrder, actualOrder,
        "Reload must reproduce the manually-reordered position, which priority/queuedAtTime alone "
            + "cannot reconstruct");
  }

  @Test
  void storeAggregateRoot_truncatesToSeconds_butOtherwisePreservesQueuedAtTime() throws Exception {

    Integer locationId = registerLocation("Timestamp Loc");
    RootFolderEntity libraryRoot = buildTwoAlbumRoot();
    SongQueueRepositoryJpaImpl repository = newRepository(locationId, libraryRoot);

    SongQueueRootEntity root = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    SongFileEntity songA = libraryRoot.getSongById(ALBUM_ONE_ID, SONG_A_ID);
    SongQueueEntryEntity entry = root.addSongToQueue("alice@example.com", songA, 1);
    Instant expected = entry.getQueuedAtTime().truncatedTo(ChronoUnit.SECONDS);

    repository.storeAggregateRoot(root);

    SongQueueRootEntity reloaded =
        repository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    assertEquals(expected, reloaded.getSongs().get(0).getQueuedAtTime());
  }

  // ── skip-and-warn on a song that no longer exists in the library ───────────

  @Test
  void loadAggregateRoot_skipsAPersistedEntryWhoseSongNoLongerExistsInTheLibrary()
      throws Exception {

    Integer locationId = registerLocation("Missing Song Loc");
    RootFolderEntity libraryRootAtStoreTime = buildTwoAlbumRoot();
    SongQueueRepositoryJpaImpl storingRepository = newRepository(locationId, libraryRootAtStoreTime);

    SongQueueRootEntity root = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    root.addSongToQueue("alice@example.com",
        libraryRootAtStoreTime.getSongById(ALBUM_ONE_ID, SONG_A_ID), 1);
    root.addSongToQueue("bob@example.com",
        libraryRootAtStoreTime.getSongById(ALBUM_TWO_ID, SONG_C_ID), 1);
    storingRepository.storeAggregateRoot(root);

    // Reload against a library that no longer has album two (songC) -- simulates the song being
    // removed from the catalog between being queued and the queue being reloaded.
    RootFolderEntity libraryRootMissingAlbumTwo = buildOneAlbumRoot();
    SongQueueRepositoryJpaImpl loadingRepository =
        newRepository(locationId, libraryRootMissingAlbumTwo);

    SongQueueRootEntity reloaded =
        loadingRepository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);

    assertEquals(1, reloaded.getSongs().size(),
        "The entry for the no-longer-existing song should be skipped, not throw");
    assertEquals("alice@example.com", reloaded.getSongs().get(0).getUsername());
  }

  // ── delete-all-and-reinsert on every store ──────────────────────────────────

  @Test
  void storeAggregateRoot_churnsEverySurrogateKey_becauseAQueuedSongHasNoStableIdentity()
      throws Exception {

    Integer locationId = registerLocation("Churn Loc");
    RootFolderEntity libraryRoot = buildTwoAlbumRoot();
    SongQueueRepositoryJpaImpl repository = newRepository(locationId, libraryRoot);

    SongQueueRootEntity root = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    root.addSongToQueue("alice@example.com", libraryRoot.getSongById(ALBUM_ONE_ID, SONG_A_ID), 1);
    repository.storeAggregateRoot(root);

    java.util.Set<Integer> idsAfterFirstStore = persistentIdentities(locationId);

    repository.storeAggregateRoot(root);
    java.util.Set<Integer> idsAfterSecondStore = persistentIdentities(locationId);

    assertEquals(idsAfterFirstStore.size(), idsAfterSecondStore.size());
    assertTrue(java.util.Collections.disjoint(idsAfterFirstStore, idsAfterSecondStore),
        "Every store deletes and reinserts the location's rows, so surrogate keys are always new");
  }

  @Test
  void storeAggregateRoot_isTenantScoped_neverTouchesAnotherLocationsRows() throws Exception {

    Integer locationIdA = registerLocation("Tenant A");
    Integer locationIdB = registerLocation("Tenant B");
    RootFolderEntity libraryRoot = buildTwoAlbumRoot();

    SongQueueRepositoryJpaImpl repositoryA = newRepository(locationIdA, libraryRoot);
    SongQueueRepositoryJpaImpl repositoryB = newRepository(locationIdB, libraryRoot);

    SongQueueRootEntity rootA = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    rootA.addSongToQueue("a@example.com", libraryRoot.getSongById(ALBUM_ONE_ID, SONG_A_ID), 1);
    repositoryA.storeAggregateRoot(rootA);

    SongQueueRootEntity rootB = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    rootB.addSongToQueue("b1@example.com", libraryRoot.getSongById(ALBUM_ONE_ID, SONG_B_ID), 1);
    rootB.addSongToQueue("b2@example.com", libraryRoot.getSongById(ALBUM_TWO_ID, SONG_C_ID), 1);
    repositoryB.storeAggregateRoot(rootB);

    SongQueueRootEntity reloadedA =
        repositoryA.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    assertEquals(1, reloadedA.getSongs().size());
    assertEquals("a@example.com", reloadedA.getSongs().get(0).getUsername());

    SongQueueRootEntity reloadedB =
        repositoryB.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    assertEquals(2, reloadedB.getSongs().size());
  }

  // ── loadAggregateRoot(int) singleton semantics ──────────────────────────────

  @Test
  void loadAggregateRoot_byPersistentIdentityZero_returnsTheSameQueueAsByNaturalIdentity()
      throws Exception {

    Integer locationId = registerLocation("Int Load Loc");
    RootFolderEntity libraryRoot = buildTwoAlbumRoot();
    SongQueueRepositoryJpaImpl repository = newRepository(locationId, libraryRoot);

    SongQueueRootEntity root = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    root.addSongToQueue("alice@example.com", libraryRoot.getSongById(ALBUM_ONE_ID, SONG_A_ID), 1);
    repository.storeAggregateRoot(root);

    SongQueueRootEntity reloaded = repository.loadAggregateRoot(0);
    assertEquals(1, reloaded.getSongs().size());
    assertEquals("alice@example.com", reloaded.getSongs().get(0).getUsername());
  }

  @Test
  void loadAggregateRoot_byAnyOtherPersistentIdentity_throws() throws Exception {

    Integer locationId = registerLocation("Bad Int Load Loc");
    SongQueueRepositoryJpaImpl repository = newRepository(locationId, buildTwoAlbumRoot());

    assertThrows(EntityDoesNotExistException.class, () -> repository.loadAggregateRoot(1));
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  private SongQueueRepositoryJpaImpl newRepository(Integer locationId,
      RootFolderEntity libraryRoot) {

    SongLibraryService songLibraryService = mock(SongLibraryService.class);
    when(songLibraryService.getOwnLocationId()).thenReturn(locationId);
    when(songLibraryService.getSongLibraryRoot(locationId)).thenReturn(libraryRoot);
    return new SongQueueRepositoryJpaImpl(entityManagerFactory, transactionManager,
        songLibraryService);
  }

  private Integer registerLocation(String name) throws EntityDoesNotExistException {

    LocationRootEntity locationRoot = locationRepository.loadAggregateRoot(0);
    Integer locationId = locationRepository.nextPersistentIdentity();
    locationRoot.addLocation(
        new LocationEntity(locationId, name, null, null, "test-api-key-hash-" + locationId));
    locationRepository.storeAggregateRoot(locationRoot);
    return locationId;
  }

  private RootFolderEntity buildTwoAlbumRoot() throws EntityAlreadyExistsException {

    RootFolderEntity root = new RootFolderEntity("/fixture/song-queue-test");

    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    root.addChildFolder(genre);

    ArtistFolderEntity artist = new ArtistFolderEntity(genre, "Artist One");
    genre.addChildFolder(artist);

    AlbumFolderEntity albumOne = new AlbumFolderEntity(artist, "Album One");
    albumOne.setPersistentIdentity(ALBUM_ONE_ID);
    artist.addChildFolder(albumOne);
    albumOne.addChildSong(newSong(albumOne, "01 - Song A.mp3", SONG_A_ID, 1));
    albumOne.addChildSong(newSong(albumOne, "02 - Song B.mp3", SONG_B_ID, 2));

    AlbumFolderEntity albumTwo = new AlbumFolderEntity(artist, "Album Two");
    albumTwo.setPersistentIdentity(ALBUM_TWO_ID);
    artist.addChildFolder(albumTwo);
    albumTwo.addChildSong(newSong(albumTwo, "01 - Song C.mp3", SONG_C_ID, 1));

    root.initialize();
    return root;
  }

  private RootFolderEntity buildOneAlbumRoot() throws EntityAlreadyExistsException {

    RootFolderEntity root = new RootFolderEntity("/fixture/song-queue-test");

    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    root.addChildFolder(genre);

    ArtistFolderEntity artist = new ArtistFolderEntity(genre, "Artist One");
    genre.addChildFolder(artist);

    AlbumFolderEntity albumOne = new AlbumFolderEntity(artist, "Album One");
    albumOne.setPersistentIdentity(ALBUM_ONE_ID);
    artist.addChildFolder(albumOne);
    albumOne.addChildSong(newSong(albumOne, "01 - Song A.mp3", SONG_A_ID, 1));
    albumOne.addChildSong(newSong(albumOne, "02 - Song B.mp3", SONG_B_ID, 2));

    root.initialize();
    return root;
  }

  private SongFileEntity newSong(AlbumFolderEntity album, String filename, Integer songId,
      int trackNumber) {

    SongFileEntity song = new SongFileEntity(album, filename);
    song.setPersistentIdentity(songId);
    song.setArtistName("Artist One");
    song.setSongName(filename);
    song.setTrackNumber(trackNumber);
    song.setNumPlays(0);
    return song;
  }

  // ── raw-JDBC row introspection -- bypasses the repository's own EntityManager/1st-level cache,
  // so it reflects exactly what's on disk ────────────────────────────────────────────────────

  private java.util.Set<Integer> persistentIdentities(Integer locationId) throws SQLException {

    java.util.Set<Integer> ids = new java.util.HashSet<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select persistent_identity from song_queue_entries where location_id = ?")) {

      statement.setInt(1, locationId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          ids.add(resultSet.getInt(1));
        }
      }
    }
    return ids;
  }
}
