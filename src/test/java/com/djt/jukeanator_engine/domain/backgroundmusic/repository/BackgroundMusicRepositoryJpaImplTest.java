package com.djt.jukeanator_engine.domain.backgroundmusic.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartAdditionReason;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;

/**
 * Integration tests for {@link BackgroundMusicRepositoryJpaImpl} and {@link
 * SmartBackgroundMusicRepositoryJpaImpl}, run against a live local MySQL instance -- same setup as
 * {@code UserActivityRepositoryJpaImplTest} (see {@code src/test/resources/application-test.yml}).
 *
 * <p>
 * Focuses on {@code storeAll} assigning the generated {@code persistentIdentity} to the caller's
 * own (new) entity instances. {@code BackgroundMusicServiceImpl} keeps those same instances in
 * memory and later passes them to {@code updatePlayStats}; if {@code storeAll} only assigned the id
 * to a managed copy (as {@code merge} does), the post-rescan in-memory songs would all carry a null
 * id and {@code time_last_played} would never be persisted.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.repository-type=jpa" })
class BackgroundMusicRepositoryJpaImplTest {

  @Autowired
  private DataSource dataSource;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @BeforeEach
  void cleanTable() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("delete from song_background_music");
    }
  }

  private BackgroundMusicRepositoryJpaImpl newRepository() {
    return new BackgroundMusicRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  private SmartBackgroundMusicRepositoryJpaImpl newSmartRepository() {
    return new SmartBackgroundMusicRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  private Timestamp readTimeLastPlayed(Integer persistentIdentity) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select time_last_played from song_background_music where persistent_identity = ?")) {
      statement.setInt(1, persistentIdentity);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "Expected a row for persistentIdentity " + persistentIdentity);
        return rs.getTimestamp("time_last_played");
      }
    }
  }

  private int countRows(String type) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection
            .prepareStatement("select count(*) as c from song_background_music where type = ?")) {
      statement.setString(1, type);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next());
        return rs.getInt("c");
      }
    }
  }

  @Test
  void storeAll_assignsPersistentIdentityToCallersNewInstances() {

    BackgroundMusicRepositoryJpaImpl repository = newRepository();

    BackgroundMusicSongEntity first = new BackgroundMusicSongEntity(null, "C:/music/a/01.mp3");
    BackgroundMusicSongEntity second = new BackgroundMusicSongEntity(null, "C:/music/a/02.mp3");

    repository.storeAll(new ArrayList<>(List.of(first, second)));

    assertNotNull(first.getPersistentIdentity());
    assertNotNull(second.getPersistentIdentity());
    assertFalse(first.getPersistentIdentity().equals(second.getPersistentIdentity()));
  }

  @Test
  void storeAll_thenUpdatePlayStats_onSameInstance_persistsTimeLastPlayed() throws Exception {

    BackgroundMusicRepositoryJpaImpl repository = newRepository();

    BackgroundMusicSongEntity song = new BackgroundMusicSongEntity(null, "C:/music/a/01.mp3");
    List<BackgroundMusicSongEntity> allSongs = new ArrayList<>(List.of(song));
    repository.storeAll(allSongs);

    assertNull(readTimeLastPlayed(song.getPersistentIdentity()));

    song.markPlayed(Instant.now());
    repository.updatePlayStats(allSongs, song);

    assertNotNull(readTimeLastPlayed(song.getPersistentIdentity()));
    assertEquals(1, repository.loadAll().get(0).getNumberOfPlays());
  }

  @Test
  void storeAll_withFullyRebuiltList_replacesOldRows_andNewInstancesAcceptPlayStats()
      throws Exception {

    BackgroundMusicRepositoryJpaImpl repository = newRepository();

    // Initial load, with one song already played.
    BackgroundMusicSongEntity original = new BackgroundMusicSongEntity(null, "C:/music/a/01.mp3");
    List<BackgroundMusicSongEntity> initial = new ArrayList<>(List.of(original));
    repository.storeAll(initial);
    original.markPlayed(Instant.now());
    repository.updatePlayStats(initial, original);
    Integer originalId = original.getPersistentIdentity();

    // Rescan: the service rebuilds every entity from scratch with a null id, exactly like
    // BackgroundMusicServiceImpl.reinitializeAfterRescan.
    BackgroundMusicSongEntity rebuiltFirst =
        new BackgroundMusicSongEntity(null, "C:/music/a/01.mp3");
    BackgroundMusicSongEntity rebuiltSecond =
        new BackgroundMusicSongEntity(null, "C:/music/a/02.mp3");
    List<BackgroundMusicSongEntity> rebuilt =
        new ArrayList<>(List.of(rebuiltFirst, rebuiltSecond));
    repository.storeAll(rebuilt);

    assertEquals(2, countRows("REGULAR"));
    assertNotNull(rebuiltFirst.getPersistentIdentity());
    assertNotNull(rebuiltSecond.getPersistentIdentity());
    assertFalse(originalId.equals(rebuiltFirst.getPersistentIdentity()),
        "The pre-rescan row should have been deleted and replaced by a fresh one");
    assertNull(readTimeLastPlayed(rebuiltFirst.getPersistentIdentity()));

    // First song played after the rescan -- the in-memory instance must still be able to persist
    // its play stats.
    rebuiltFirst.markPlayed(Instant.now());
    repository.updatePlayStats(rebuilt, rebuiltFirst);

    assertNotNull(readTimeLastPlayed(rebuiltFirst.getPersistentIdentity()));
    assertNull(readTimeLastPlayed(rebuiltSecond.getPersistentIdentity()));
  }

  @Test
  void storeAll_mergesExistingInstances_withoutChangingTheirIdentity() {

    BackgroundMusicRepositoryJpaImpl repository = newRepository();

    BackgroundMusicSongEntity song = new BackgroundMusicSongEntity(null, "C:/music/a/01.mp3");
    List<BackgroundMusicSongEntity> allSongs = new ArrayList<>(List.of(song));
    repository.storeAll(allSongs);
    Integer id = song.getPersistentIdentity();

    // A second storeAll with the now-identified instance plus a new one must merge the former in
    // place and only insert the latter.
    BackgroundMusicSongEntity added = new BackgroundMusicSongEntity(null, "C:/music/a/02.mp3");
    allSongs.add(added);
    repository.storeAll(allSongs);

    assertEquals(id, song.getPersistentIdentity());
    assertNotNull(added.getPersistentIdentity());
    List<BackgroundMusicSongEntity> loaded = repository.loadAll();
    assertEquals(2, loaded.size());
    assertTrue(loaded.stream().anyMatch(s -> id.equals(s.getPersistentIdentity())));
  }

  @Test
  void smartStoreAll_assignsIdentityToNewInstances_andUpdatePlayStatsPersistsTimeLastPlayed()
      throws Exception {

    SmartBackgroundMusicRepositoryJpaImpl smartRepository = newSmartRepository();

    SmartBackgroundMusicSongEntity smartSong = new SmartBackgroundMusicSongEntity(null,
        "C:/music/b/01.mp3", "C:/music/a/01.mp3", Integer.valueOf(5),
        SmartAdditionReason.SAME_ARTIST);
    List<SmartBackgroundMusicSongEntity> smartPool = new ArrayList<>(List.of(smartSong));
    smartRepository.storeAll(smartPool);

    assertNotNull(smartSong.getPersistentIdentity());
    assertEquals(1, countRows("SMART"));
    assertNull(readTimeLastPlayed(smartSong.getPersistentIdentity()));

    smartSong.markPlayed(Instant.now());
    smartRepository.updatePlayStats(smartPool, smartSong);

    assertNotNull(readTimeLastPlayed(smartSong.getPersistentIdentity()));
  }

  @Test
  void regularAndSmartStoreAll_doNotDeleteEachOthersRows() throws Exception {

    BackgroundMusicRepositoryJpaImpl repository = newRepository();
    SmartBackgroundMusicRepositoryJpaImpl smartRepository = newSmartRepository();

    repository.storeAll(
        new ArrayList<>(List.of(new BackgroundMusicSongEntity(null, "C:/music/a/01.mp3"))));
    smartRepository.storeAll(new ArrayList<>(List.of(new SmartBackgroundMusicSongEntity(null,
        "C:/music/b/01.mp3", null, null, SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM))));

    // Re-storing each table with a fresh list must only replace rows of its own type.
    repository.storeAll(
        new ArrayList<>(List.of(new BackgroundMusicSongEntity(null, "C:/music/a/02.mp3"))));
    smartRepository.storeAll(new ArrayList<>(List.of(new SmartBackgroundMusicSongEntity(null,
        "C:/music/b/02.mp3", null, null, SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM))));

    assertEquals(1, countRows("REGULAR"));
    assertEquals(1, countRows("SMART"));
  }
}
