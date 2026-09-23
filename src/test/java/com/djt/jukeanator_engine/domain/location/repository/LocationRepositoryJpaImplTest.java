package com.djt.jukeanator_engine.domain.location.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Integration tests for {@link LocationRepositoryJpaImpl#changeLocationId}, run against a live
 * local MySQL instance -- same setup as {@code FinancialLedgerRepositoryJpaImplTest} (see {@code
 * src/test/resources/application-test.yml}).
 *
 * <p>Every fixture row uses ids in the 900000 range, well clear of anything the Spring context's
 * own startup (e.g. SongLibraryServiceImpl creating its own location) or any other test creates,
 * and {@link #deleteFixtures()} removes exactly those rows before and after each test, so this
 * class never disturbs data it didn't create.
 *
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.repository-type=jpa" })
class LocationRepositoryJpaImplTest {

  private static final int OLD_LOCATION_ID = 900001;
  private static final int NEW_LOCATION_ID = 900002;
  private static final int OTHER_LOCATION_ID = 900003;

  private static final int USER_ID = 900001;
  private static final int PLAYLIST_ID = 900001;

  // Every non-FK location-tagged column changeLocationId() re-points, as {table, column}.
  private static final String[][] LOCATION_TAGGED_COLUMNS = {
      { "song_library", "parent_location_id" },
      { "song_queue_entries", "location_id" },
      { "location_jukebox_split", "parent_location_id" },
      { "location_transaction", "location_id" },
      { "user_song_play_history", "location_id" },
      { "user_playlist_song", "location_id" },
      { "user_song_credit_usage", "location_id" },
      { "user_activity", "location_id" },
      { "background_music_songs", "location_id" },
      { "smart_background_music_songs", "location_id" },
      { "smart_background_music_songs", "source_location_id" } };

  @Autowired
  private DataSource dataSource;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private LocationRepositoryJpaImpl newRepository() {
    return new LocationRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  @BeforeEach
  @AfterEach
  void deleteFixtures() throws SQLException {

    List<Integer> locationIds = List.of(OLD_LOCATION_ID, NEW_LOCATION_ID, OTHER_LOCATION_ID);
    for (Integer locationId : locationIds) {
      // Children before parents; song_library's own self-reference cascades on delete.
      execute("delete from song_library where parent_location_id = ?", locationId);
      execute("delete from song_queue_entries where location_id = ?", locationId);
      execute("delete from location_jukebox_split where parent_location_id = ?", locationId);
      execute("delete from location_transaction where location_id = ?", locationId);
      execute("delete from user_activity where location_id = ?", locationId);
      execute("delete from background_music_songs where location_id = ?", locationId);
      execute("delete from smart_background_music_songs where location_id = ?", locationId);
    }
    execute("delete from user_song_play_history where user_id = ?", USER_ID);
    execute("delete from user_song_credit_usage where user_id = ?", USER_ID);
    execute("delete from user_playlist_song where playlist_id = ?", PLAYLIST_ID);
    execute("delete from user_playlist where persistent_identity = ?", PLAYLIST_ID);
    execute("delete from user_account where persistent_identity = ?", USER_ID);
    for (Integer locationId : locationIds) {
      execute("delete from location where id = ?", locationId);
    }
  }

  // ── happy path ───────────────────────────────────────────────────────────

  @Test
  void changeLocationId_rekeysLocationRowAndEveryReferencingRow() throws SQLException {

    insertLocation(OLD_LOCATION_ID, "rekey-old");
    insertLocation(OTHER_LOCATION_ID, "rekey-other");
    insertUser();
    insertLocationTaggedRows(OLD_LOCATION_ID, 0);
    insertLocationTaggedRows(OTHER_LOCATION_ID, 1);

    newRepository().changeLocationId(OLD_LOCATION_ID, NEW_LOCATION_ID);

    assertEquals(0, count("select count(*) from location where id = ?", OLD_LOCATION_ID));
    assertEquals(1, count("select count(*) from location where id = ? and name = 'rekey-old'",
        NEW_LOCATION_ID));

    for (String[] tableColumn : LOCATION_TAGGED_COLUMNS) {
      String table = tableColumn[0];
      String column = tableColumn[1];
      String sql = "select count(*) from " + table + " where " + column + " = ?";
      assertEquals(0, count(sql, OLD_LOCATION_ID), table + "." + column + " still has old id");
      assertEquals(expectedRowsPerLocation(table), count(sql, NEW_LOCATION_ID),
          table + "." + column + " rows were not re-pointed to the new id");
      assertEquals(expectedRowsPerLocation(table), count(sql, OTHER_LOCATION_ID),
          table + "." + column + " rows under an unrelated location should be untouched");
    }

    // The self-referencing child must still resolve to its parent under the new id.
    assertEquals(1, count("select count(*) from song_library child join song_library parent "
        + "on parent.parent_location_id = child.parent_location_id "
        + "and parent.id = child.parent_folder_id where child.parent_location_id = ?",
        NEW_LOCATION_ID));

    assertForeignKeyChecksEnabledOnEveryPooledConnection();
  }

  @Test
  void changeLocationId_isANoOp_whenIdsMatch() throws SQLException {

    insertLocation(OLD_LOCATION_ID, "rekey-old");

    newRepository().changeLocationId(OLD_LOCATION_ID, OLD_LOCATION_ID);

    assertEquals(1, count("select count(*) from location where id = ?", OLD_LOCATION_ID));
  }

  // ── failure ──────────────────────────────────────────────────────────────

  @Test
  void changeLocationId_rollsBackEverything_andRestoresForeignKeyChecks_whenNewIdIsTaken()
      throws SQLException {

    insertLocation(OLD_LOCATION_ID, "rekey-old");
    insertLocation(OTHER_LOCATION_ID, "rekey-other");
    insertUser();
    insertLocationTaggedRows(OLD_LOCATION_ID, 0);

    // OTHER_LOCATION_ID is already a location row's primary key, so the very first update fails.
    assertThrows(RuntimeException.class,
        () -> newRepository().changeLocationId(OLD_LOCATION_ID, OTHER_LOCATION_ID));

    assertEquals(1, count("select count(*) from location where id = ?", OLD_LOCATION_ID));
    for (String[] tableColumn : LOCATION_TAGGED_COLUMNS) {
      String table = tableColumn[0];
      String column = tableColumn[1];
      assertEquals(expectedRowsPerLocation(table),
          count("select count(*) from " + table + " where " + column + " = ?", OLD_LOCATION_ID),
          table + "." + column + " should be unchanged after a failed re-key");
    }

    assertForeignKeyChecksEnabledOnEveryPooledConnection();
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  // song_library gets a parent/child pair per location (to exercise the self-referencing FK);
  // every other table gets exactly one row.
  private static int expectedRowsPerLocation(String table) {
    return "song_library".equals(table) ? 2 : 1;
  }

  private void insertLocation(int locationId, String name) throws SQLException {
    execute("insert into location (id, name, api_key_hash, status) "
        + "values (?, ?, 'test-api-key-hash', 'PROVISIONED')", locationId, name);
  }

  private void insertUser() throws SQLException {
    execute("insert into user_account (persistent_identity, first_name, last_name, "
        + "email_address, password_hash, num_credits, role) "
        + "values (?, 'Rekey', 'Test', 'rekey-test@example.com', 'hash', 6, 'ROLE_USER')",
        USER_ID);
    execute("insert into user_playlist (persistent_identity, user_id, owner, name) "
        + "values (?, ?, 'rekey-test@example.com', 'My Favorites')", PLAYLIST_ID, USER_ID);
  }

  /**
   * Inserts one row tagged with {@code locationId} into every table changeLocationId() touches.
   * {@code offset} keeps persistent identities and element-collection order columns distinct when
   * called for more than one location in the same test.
   */
  private void insertLocationTaggedRows(int locationId, int offset) throws SQLException {

    int id = 900001 + offset;
    Timestamp now = Timestamp.from(Instant.now());

    execute("insert into song_library (id, name, parent_location_id, parent_folder_id, "
        + "class_discriminator) values (1, 'C:\\\\Music', ?, null, 'ROOT')", locationId);
    execute("insert into song_library (id, name, parent_location_id, parent_folder_id, "
        + "class_discriminator) values (2, 'Rock', ?, 1, 'FOLDER')", locationId);
    execute("insert into song_queue_entries (persistent_identity, location_id, album_id, "
        + "song_id, queue_order, username, priority, queued_at_time) "
        + "values (?, ?, 1, 1, 0, 'LOCAL', 0, ?)", id, locationId, now);
    execute("insert into location_jukebox_split (persistent_identity, parent_location_id, "
        + "start_date) values (?, ?, ?)", id, locationId, now);
    execute("insert into location_transaction (persistent_identity, transaction_type, "
        + "amount_dollars, timestamp, location_id) values (?, 'CASH', 1, ?, ?)",
        id, now, locationId);
    execute("insert into user_song_play_history (user_id, play_order, album_id, song_id, "
        + "location_id) values (?, ?, 1, 1, ?)", USER_ID, offset, locationId);
    execute("insert into user_playlist_song (playlist_id, song_order, album_id, song_id, "
        + "location_id) values (?, ?, 1, 1, ?)", PLAYLIST_ID, offset, locationId);
    execute("insert into user_song_credit_usage (persistent_identity, user_id, location_id, "
        + "amount, type, timestamp, song_album_id, song_id, resulting_balance) "
        + "values (?, ?, ?, -1, 'QUEUE_ADD', ?, 1, 1, 5)", id, USER_ID, locationId, now);
    execute("insert into user_activity (persistent_identity, location_id, source, username, "
        + "activity_type, occurred_at) values (?, ?, 'SWING_UI', 'LOCAL', 'TAB_NAVIGATION', ?)",
        id, locationId, now);
    execute("insert into background_music_songs (persistent_identity, song_file_path, "
        + "number_of_plays, location_id, album_id, song_id) values (?, 'rekey.mp3', 0, ?, 1, 1)",
        id, locationId);
    execute("insert into smart_background_music_songs (persistent_identity, song_file_path, "
        + "number_of_plays, reason, location_id, album_id, song_id, source_location_id, "
        + "source_album_id, source_song_id) "
        + "values (?, 'rekey-smart.mp3', 0, 'SAME_ARTIST', ?, 1, 1, ?, 1, 2)",
        id, locationId, locationId);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /**
   * FOREIGN_KEY_CHECKS is session-scoped, so changeLocationId() turning it off and back on only
   * ever affects whichever pooled connection it ran on. Holding every connection the pool can hand
   * out at once guarantees that one is among them, whichever it was.
   */
  private void assertForeignKeyChecksEnabledOnEveryPooledConnection() throws SQLException {

    int poolSize = dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();
    List<Connection> held = new ArrayList<>();
    try {
      for (int i = 0; i < poolSize; i++) {
        Connection connection = dataSource.getConnection();
        held.add(connection);
        try (PreparedStatement statement =
            connection.prepareStatement("select @@session.foreign_key_checks");
            ResultSet resultSet = statement.executeQuery()) {
          resultSet.next();
          assertEquals(1, resultSet.getInt(1),
              "A pooled connection was returned with foreign_key_checks still disabled");
        }
      }
    } finally {
      for (Connection connection : held) {
        connection.close();
      }
    }
  }

  private void execute(String sql, Object... params) throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, params);
      statement.executeUpdate();
    }
  }

  private int count(String sql, Object... params) throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, params);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getInt(1);
      }
    }
  }

  private static void bind(PreparedStatement statement, Object... params) throws SQLException {
    for (int i = 0; i < params.length; i++) {
      statement.setObject(i + 1, params[i]);
    }
  }
}
