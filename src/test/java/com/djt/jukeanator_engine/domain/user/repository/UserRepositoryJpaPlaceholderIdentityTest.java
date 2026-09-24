package com.djt.jukeanator_engine.domain.user.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;

/**
 * Integration tests for how {@link UserRepositoryJpaImpl} stores new users and credit usages
 * carrying placeholder ids, against a live local MySQL instance (see {@code
 * src/test/resources/application-test.yml}) -- same setup as {@code UserServiceJpaConcurrencyTest}.
 * {@code UserEntityPlaceholderIdentityTest} covers the id minting itself; this covers what the
 * store then does with those ids. Uses its own repository instance (loaded fresh from the
 * database) rather than the context's {@code UserService}, so each test controls exactly which
 * users the in-memory root holds.
 *
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.repository-type=jpa" })
class UserRepositoryJpaPlaceholderIdentityTest {

  @Autowired
  private DataSource dataSource;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private UserRepositoryJpaImpl newRepository() {
    return new UserRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  @Test
  void registeringAUser_neverOverwritesTheExistingUserItsCountWouldHaveCollidedWith()
      throws Exception {

    UserRepositoryJpaImpl repository = newRepository();

    // Arrange a user row whose real id is exactly what the old users.size() + 1 placeholder
    // would be for the next registration: one more row brings the count to `size + 1`, so that
    // placeholder becomes `size + 2`.
    int sizeBeforeFixture = repository.loadAggregateRoot("users").getUsers().size();
    int collidingId = sizeBeforeFixture + 2;
    int fixtureId = userIdExists(collidingId) ? maxUserId() + 1000 : collidingId;
    insertUser(fixtureId, unique("existing") + "@example.com");
    bumpSequencePast(Math.max(fixtureId, maxUserId()));
    String emailHeldByCollidingId = emailOfUser(collidingId);

    UserRootEntity root = repository.loadAggregateRoot("users");
    assertEquals(collidingId, root.getUsers().size() + 1,
        "Precondition: the old count-based placeholder must land on an existing user's id");

    String newEmail = unique("new") + "@example.com";
    root.addUser(new UserEntity(root.nextUserPersistentIdentity(), "New", "User", newEmail,
        "hash", Integer.valueOf(6), UserRole.ROLE_USER));
    repository.storeAggregateRoot(root);

    assertEquals(emailHeldByCollidingId, emailOfUser(collidingId),
        "The existing user must not have been overwritten by the new registration");
    Integer newUserId = idOfUser(newEmail);
    assertNotEquals(Integer.valueOf(collidingId), newUserId);
  }

  @Test
  void storedCreditUsages_stayFindableInTheirSet_andLaterOnesKeepPersisting() throws Exception {

    UserRepositoryJpaImpl repository = newRepository();
    UserRootEntity root = repository.loadAggregateRoot("users");

    String email = unique("spender") + "@example.com";
    UserEntity user = new UserEntity(root.nextUserPersistentIdentity(), "Spend", "Er", email,
        "hash", Integer.valueOf(20), UserRole.ROLE_USER);
    root.addUser(user);
    repository.storeAggregateRoot(root);

    UserSongCreditUsageEntity first = user.addUserSongCreditUsage(
        usage(user.nextUserSongCreditUsageIdentity(), "first"));
    repository.storeAggregateRoot(root);

    // persist() replaced the placeholder with a real id in place; the set must still find it.
    assertTrue(user.getUserSongCreditUsages().contains(first));

    UserSongCreditUsageEntity second = user.addUserSongCreditUsage(
        usage(user.nextUserSongCreditUsageIdentity(), "second"));
    repository.storeAggregateRoot(root);

    assertTrue(user.getUserSongCreditUsages().contains(second));
    assertEquals(2, countUsagesOf(email));
  }

  // ── fixtures ────────────────────────────────────────────────────────────

  private static String unique(String base) {
    return base + "-" + System.nanoTime();
  }

  private static UserSongCreditUsageEntity usage(Integer persistentIdentity, String label) {
    return new UserSongCreditUsageEntity(persistentIdentity, Integer.valueOf(7), -2,
        UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 1, 2, 4, unique(label));
  }

  private void insertUser(int id, String email) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "insert into user_account (persistent_identity, first_name, last_name, "
                + "email_address, password_hash, num_credits, role) "
                + "values (?, 'Existing', 'User', ?, 'hash', 6, 'ROLE_USER')")) {
      statement.setInt(1, id);
      statement.setString(2, email);
      statement.executeUpdate();
    }
  }

  // Keeps later sequence-issued ids clear of the hand-inserted fixture row.
  private void bumpSequencePast(int id) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "update persistent_identity_seq set next_val = greatest(next_val, ?)")) {
      statement.setInt(1, id + 1);
      statement.executeUpdate();
    }
  }

  private boolean userIdExists(int id) throws SQLException {
    return emailOfUser(id) != null;
  }

  private int maxUserId() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(
            "select coalesce(max(persistent_identity), 0) from user_account")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private String emailOfUser(int id) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select email_address from user_account where persistent_identity = ?")) {
      statement.setInt(1, id);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  private Integer idOfUser(String email) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select persistent_identity from user_account where email_address = ?")) {
      statement.setString(1, email);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "Expected a user_account row for " + email);
        return Integer.valueOf(rs.getInt(1));
      }
    }
  }

  private int countUsagesOf(String email) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select count(*) from user_song_credit_usage c join user_account u "
                + "on u.persistent_identity = c.user_id where u.email_address = ?")) {
      statement.setString(1, email);
      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }
}
