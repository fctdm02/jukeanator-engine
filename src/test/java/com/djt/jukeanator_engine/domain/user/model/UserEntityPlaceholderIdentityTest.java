package com.djt.jukeanator_engine.domain.user.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.security.UserRole;

/**
 * Covers how new users and their children are given placeholder ids. Under JPA, persisted rows
 * carry real ids from the shared {@code persistent_identity_seq}; each scenario below loads such
 * ids into memory (as {@code UserRepositoryJpaImpl} would) so that the old size-derived placeholder
 * ({@code size() + 1}, or {@code size()} for playlists) lands exactly on an existing id -- which
 * previously overwrote that row on store (users, playlists) or silently dropped the new record
 * from its id-keyed {@code HashSet} (credit usages, Add-Funds transactions).
 *
 * @author tmyers
 */
class UserEntityPlaceholderIdentityTest {

  private static UserEntity user(int persistentIdentity, String email) {
    return new UserEntity(Integer.valueOf(persistentIdentity), "First", "Last", email, "hash",
        Integer.valueOf(6), UserRole.ROLE_USER);
  }

  private static UserSongCreditUsageEntity usage(Integer persistentIdentity) {
    return new UserSongCreditUsageEntity(persistentIdentity, Integer.valueOf(7), -2,
        UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 1, 2, 4, "sync-" + persistentIdentity);
  }

  private static UserAddFundsTransactionEntity addFunds(Integer persistentIdentity) {
    return new UserAddFundsTransactionEntity(persistentIdentity, "pkg-7", 10, 2,
        new BigDecimal("5.00"), "TestGateway", "txn-" + persistentIdentity, Instant.now(), 12);
  }

  @Test
  void nextUserPersistentIdentity_neverEqualsAnExistingUsersId() {

    // Two users whose real ids are 1 and 3: the old users.size() + 1 placeholder is 3, which
    // UserRepositoryJpaImpl would have merge()d straight over the existing user 3.
    UserRootEntity root = new UserRootEntity();
    root.addUser(user(1, "admin@example.com"));
    root.addUser(user(3, "early@example.com"));

    assertEquals(Integer.valueOf(4), root.nextUserPersistentIdentity());
  }

  @Test
  void nextUserPersistentIdentity_startsAtOne_forAnEmptyRoot() {
    assertEquals(Integer.valueOf(1), new UserRootEntity().nextUserPersistentIdentity());
  }

  @Test
  void newCreditUsage_isNeverDropped_whenItsCountWouldMatchAnExistingId() {

    // One persisted usage whose real id is 2: the old size() + 1 placeholder is also 2, and the
    // HashSet (keyed by persistentIdentity) silently discarded the new usage.
    UserEntity user = user(1, "alice@example.com");
    user.addUserSongCreditUsage(usage(Integer.valueOf(2)));

    UserSongCreditUsageEntity added =
        user.addUserSongCreditUsage(usage(user.nextUserSongCreditUsageIdentity()));

    assertEquals(Integer.valueOf(3), added.getPersistentIdentity());
    assertEquals(2, user.getUserSongCreditUsages().size());
  }

  @Test
  void newAddFundsTransaction_isNeverDropped_whenItsCountWouldMatchAnExistingId() {

    UserEntity user = user(1, "alice@example.com");
    user.addUserAddFundsTransaction(addFunds(Integer.valueOf(2)));

    user.addUserAddFundsTransaction(addFunds(user.nextUserAddFundsTransactionIdentity()));

    assertEquals(2, user.getUserAddFundsTransactions().size());
  }

  @Test
  void addingAChildWithADuplicateId_failsLoudly_ratherThanBeingSilentlyDropped() {

    UserEntity user = user(1, "alice@example.com");
    user.addUserSongCreditUsage(usage(Integer.valueOf(2)));

    assertThrows(IllegalStateException.class,
        () -> user.addUserSongCreditUsage(usage(Integer.valueOf(2))));
  }

  @Test
  void createPlaylist_neverReusesAnExistingPlaylistsId() throws Exception {

    // Favorites was persisted with real id 1: the old playlists.size() placeholder (1) collided
    // with it, and UserRepositoryJpaImpl would have merge()d the new playlist over Favorites.
    UserEntity user = user(1, "alice@example.com");
    user.getPlaylists().clear();
    user.restorePlaylist(new PlaylistEntity(Integer.valueOf(1), "alice@example.com",
        PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, new ArrayList<>()));

    PlaylistEntity partyMix = user.createPlaylist("Party Mix");

    assertEquals(Integer.valueOf(2), partyMix.getPersistentIdentity());
  }

  @Test
  void createPlaylist_staysUnique_afterADeletion() throws Exception {

    UserEntity user = user(1, "alice@example.com"); // Favorites gets 0
    user.createPlaylist("A"); // 1
    user.createPlaylist("B"); // 2
    user.deletePlaylist("A");

    // playlists.size() would now be 2 again -- B's id.
    PlaylistEntity c = user.createPlaylist("C");

    List<Integer> ids =
        user.getPlaylists().stream().map(PlaylistEntity::getPersistentIdentity).toList();
    assertEquals(Integer.valueOf(3), c.getPersistentIdentity());
    assertEquals(ids.size(), ids.stream().distinct().count());
  }

  @Test
  void rehashChildCollections_letsContainsFindChildrenWhoseIdChangedInPlace() {

    UserEntity user = user(1, "alice@example.com");
    UserSongCreditUsageEntity usage =
        user.addUserSongCreditUsage(usage(user.nextUserSongCreditUsageIdentity()));

    // What UserRepositoryJpaImpl's persist() does: replace the placeholder id in place.
    usage.setPersistentIdentity(Integer.valueOf(5000));
    user.rehashChildCollections();

    assertTrue(user.getUserSongCreditUsages().contains(usage));
    assertEquals(Integer.valueOf(5001), user.nextUserSongCreditUsageIdentity());
  }
}
