package com.djt.jukeanator_engine.domain.user.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

/**
 * Deleting a playlist must not break later stores, against a live local MySQL instance (see {@code
 * src/test/resources/application-test.yml}) -- same setup as {@code
 * UserRepositoryJpaPlaceholderIdentityTest}.
 *
 * <p>{@code UserServiceImpl} holds the user root detached for the app's lifetime. A user's
 * playlists list, once loaded or persisted by JPA, is a Hibernate collection that remembers the
 * rows it held at the time. {@code merge()} deletes a playlist removed from that list -- and every
 * later {@code merge()} then tried to resolve the remembered, now-deleted row, failing with
 * {@code EntityNotFoundException} for that user and, since a store merges every user, for every
 * user's change until a restart.
 *
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.repository-type=jpa" })
class UserRepositoryJpaPlaylistDeletionTest {

  private static final String PLAYLIST = "Road Trip";

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
  void deletingAPlaylistOfAUserLoadedFromTheDatabase_neverBreaksLaterStores() throws Exception {

    // Arrange a user with a stored playlist, then load it fresh, as the app does at startup.
    String email = unique("loaded") + "@example.com";
    UserRepositoryJpaImpl setupRepository = newRepository();
    UserRootEntity setupRoot = setupRepository.loadAggregateRoot("users");
    UserEntity setupUser = newUser(setupRoot, email);
    setupUser.createPlaylist(PLAYLIST);
    setupRepository.storeAggregateRoot(setupRoot);
    assertEquals(1, countPlaylists(email, PLAYLIST));

    UserRepositoryJpaImpl repository = newRepository();
    UserRootEntity root = repository.loadAggregateRoot("users");
    UserEntity user = root.getUserByEmailAddressNullIfNotExists(email);

    assertDeletingThePlaylistNeverBreaksLaterStores(repository, root, user, email);
  }

  @Test
  void deletingAPlaylistOfAUserRegisteredSinceStartup_neverBreaksLaterStores() throws Exception {

    // persist() swaps a new user's collections for Hibernate's own, in place.
    String email = unique("registered") + "@example.com";
    UserRepositoryJpaImpl repository = newRepository();
    UserRootEntity root = repository.loadAggregateRoot("users");
    UserEntity user = newUser(root, email);
    repository.storeAggregateRoot(root);
    user.createPlaylist(PLAYLIST);
    repository.storeAggregateRoot(root);
    assertEquals(1, countPlaylists(email, PLAYLIST));

    assertDeletingThePlaylistNeverBreaksLaterStores(repository, root, user, email);
  }

  private void assertDeletingThePlaylistNeverBreaksLaterStores(UserRepositoryJpaImpl repository,
      UserRootEntity root, UserEntity user, String email) throws Exception {

    user.deletePlaylist(PLAYLIST);
    repository.storeAggregateRoot(root);
    assertEquals(0, countPlaylists(email, PLAYLIST), "the deleted playlist's row must be gone");

    // The same user changes something else...
    user.setFirstName("Renamed");
    assertDoesNotThrow(() -> repository.storeAggregateRoot(root),
        "a store after a playlist deletion must not fail on the deleted row");
    assertEquals("Renamed", firstNameOf(email));

    // ...and someone else registers -- a store merges every user in the root.
    String otherEmail = unique("other") + "@example.com";
    newUser(root, otherEmail);
    assertDoesNotThrow(() -> repository.storeAggregateRoot(root));
    assertEquals("New", firstNameOf(otherEmail));

    // The user's remaining playlist (My Favorites) is untouched throughout.
    assertEquals(1, countPlaylists(email, "My Favorites"));
  }

  // ── fixtures ────────────────────────────────────────────────────────────

  private static String unique(String base) {
    return base + "-" + System.nanoTime();
  }

  private static UserEntity newUser(UserRootEntity root, String email) {
    UserEntity user = new UserEntity(root.nextUserPersistentIdentity(), "New", "User", email,
        "hash", Integer.valueOf(0), UserRole.ROLE_USER);
    root.addUser(user);
    return user;
  }

  private int countPlaylists(String email, String playlistName) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select count(*) from user_playlist p join user_account u "
                + "on u.persistent_identity = p.user_id where u.email_address = ? and p.name = ?")) {
      statement.setString(1, email);
      statement.setString(2, playlistName);
      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }

  private String firstNameOf(String email) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select first_name from user_account where email_address = ?")) {
      statement.setString(1, email);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "Expected a user_account row for " + email);
        return rs.getString(1);
      }
    }
  }
}
