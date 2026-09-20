package com.djt.jukeanator_engine.domain.useractivity.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link UserActivityRepositoryJpaImpl}, run against a live local MySQL
 * instance -- same setup as {@code FinancialLedgerRepositoryJpaImplTest} (see {@code
 * src/test/resources/application-test.yml}). Requires a real MySQL server with a {@code
 * jukeanator_test} database the {@code jukeanator} user can access. No Docker/Testcontainers
 * dependency.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.repository-type=jpa" })
class UserActivityRepositoryJpaImplTest {

  private static final ObjectMapper MAPPER = ObjectMappers.create();

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
      statement.executeUpdate("delete from user_activity");
    }
  }

  private UserActivityRepositoryJpaImpl newRepository() {
    return new UserActivityRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  private static Map<String, Object> readDetails(String json) throws Exception {
    return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  @Test
  void record_insertsOneRow_withActivityTypeAndSourceStoredAsNames() throws Exception {

    UserActivityRepositoryJpaImpl repository = newRepository();

    Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    repository.record(new UserActivityRecord(Integer.valueOf(9), UserActivitySource.MOBILE_WEB,
        "user@example.com", UserActivityType.QUEUE_SONG_ADDED, occurredAt,
        Map.of("albumId", 1, "songId", 2)));

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("select location_id, source, username, "
            + "activity_type, occurred_at, details from user_activity")) {

      assertTrue(rs.next(), "Expected exactly one row to have been inserted");
      assertEquals(9, rs.getInt("location_id"));
      assertEquals("MOBILE_WEB", rs.getString("source"));
      assertEquals("user@example.com", rs.getString("username"));
      assertEquals("QUEUE_SONG_ADDED", rs.getString("activity_type"));
      assertNotNull(rs.getTimestamp("occurred_at"));
      Map<String, Object> details = readDetails(rs.getString("details"));
      assertEquals(1, details.get("albumId"));
      assertEquals(2, details.get("songId"));
      assertTrue(!rs.next(), "Expected only one row");
    }
  }

  @Test
  void record_mintsAUniquePersistentIdentity_perCall() throws Exception {

    UserActivityRepositoryJpaImpl repository = newRepository();

    Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, occurredAt, Map.of()));
    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, occurredAt, Map.of()));

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement
            .executeQuery("select count(distinct persistent_identity) as c from user_activity")) {

      assertTrue(rs.next());
      assertEquals(2, rs.getInt("c"));
    }
  }

  @Test
  void findRecentActivity_returnsNewestFirst_scopedToLocationAndLimit() {

    UserActivityRepositoryJpaImpl repository = newRepository();

    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, base, Map.of("label", "first")));
    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.MOBILE_WEB,
        "user@example.com", UserActivityType.QUEUE_SONG_ADDED, base.plusSeconds(1),
        Map.of("label", "second")));
    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.ARTIST_VIEWED, base.plusSeconds(2), Map.of("label", "third")));
    // A different location's activity must never leak into location 1's results.
    repository.record(new UserActivityRecord(Integer.valueOf(2), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, base.plusSeconds(3),
        Map.of("label", "other-location")));

    List<UserActivityRecord> recent = repository.findRecentActivity(Integer.valueOf(1), 2);

    assertEquals(2, recent.size());
    assertEquals("third", recent.get(0).details().get("label"));
    assertEquals("second", recent.get(1).details().get("label"));
    assertEquals(UserActivitySource.MOBILE_WEB, recent.get(1).source());
  }

  @Test
  void purgeOlderThan_deletesOnlyRecordsBeforeCutoff_acrossAllLocations() throws Exception {

    UserActivityRepositoryJpaImpl repository = newRepository();

    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant fortyDaysAgo = now.minus(40, ChronoUnit.DAYS);
    Instant cutoff = now.minus(30, ChronoUnit.DAYS);

    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, fortyDaysAgo, Map.of("label", "too-old-loc1")));
    repository.record(new UserActivityRecord(Integer.valueOf(2), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, fortyDaysAgo, Map.of("label", "too-old-loc2")));
    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, now, Map.of("label", "still-fresh")));

    repository.purgeOlderThan(cutoff);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("select details from user_activity")) {

      assertTrue(rs.next(), "Expected exactly one surviving row");
      assertTrue(rs.getString("details").contains("still-fresh"));
      assertTrue(!rs.next(), "Expected the two 40-day-old rows to have been purged");
    }
  }

  @Test
  void purgeOlderThan_keepsRecordsAtOrAfterCutoff() throws Exception {

    UserActivityRepositoryJpaImpl repository = newRepository();

    Instant cutoff = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    repository.record(new UserActivityRecord(Integer.valueOf(1), UserActivitySource.SWING_UI,
        "LOCAL", UserActivityType.TAB_NAVIGATION, cutoff, Map.of()));

    repository.purgeOlderThan(cutoff);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("select count(*) as c from user_activity")) {

      assertTrue(rs.next());
      assertEquals(1, rs.getInt("c"),
          "A record recorded exactly at the cutoff instant is not strictly before it, so it must survive");
    }
  }
}
