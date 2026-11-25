package com.djt.jukeanator_engine.domain.user.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.security.UserRole;

/**
 * Covers how new users, location accounts and their children are given placeholder ids. Under
 * JPA, persisted rows carry real ids from the shared {@code persistent_identity_seq}; each scenario
 * below loads such ids into memory (as {@code UserRepositoryJpaImpl} would) so that a size-derived
 * placeholder ({@code size() + 1}, or {@code size()} for playlists) would land exactly on an
 * existing id -- which would overwrite that row on store (users, accounts, playlists) or silently
 * drop the new record from its id-keyed {@code HashSet} (credit usages, Add-Funds transactions).
 *
 * @author tmyers
 */
class UserEntityPlaceholderIdentityTest {

  private static final Integer LOCATION_ID = Integer.valueOf(7);

  private static UserEntity user(int persistentIdentity, String email) {
    return new UserEntity(Integer.valueOf(persistentIdentity), "First", "Last", email, "hash",
        UserRole.ROLE_USER);
  }

  private static UserLocationAccountEntity account(UserEntity user) {
    return user.getOrCreateLocationAccount(LOCATION_ID);
  }

  private static UserSongCreditUsageEntity usage(Integer persistentIdentity) {
    return new UserSongCreditUsageEntity(persistentIdentity, LOCATION_ID, -2,
        UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 1, 2, 4, "sync-" + persistentIdentity);
  }

  private static UserAddFundsTransactionEntity addFunds(Integer persistentIdentity) {
    return new UserAddFundsTransactionEntity(persistentIdentity, LOCATION_ID, "pkg-7", 10, 2,
        new BigDecimal("5.00"), "TestGateway", "txn-" + persistentIdentity, Instant.now(), 12);
  }

  @Test
  void nextUserPersistentIdentity_neverEqualsAnExistingUsersId() {

    // Two users whose real ids are 1 and 3: a users.size() + 1 placeholder would be 3, which
    // UserRepositoryJpaImpl would merge() straight over the existing user 3.
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
  void newLocationAccount_neverReusesAnExistingAccountsId() {

    UserEntity user = user(1, "alice@example.com");
    user.restoreLocationAccount(
        new UserLocationAccountEntity(Integer.valueOf(2), Integer.valueOf(99), 0));

    UserLocationAccountEntity added = user.getOrCreateLocationAccount(LOCATION_ID);

    assertEquals(Integer.valueOf(3), added.getPersistentIdentity());
  }

  @Test
  void newCreditUsage_isNeverDropped_whenItsCountWouldMatchAnExistingId() {

    // One persisted usage whose real id is 2: a size() + 1 placeholder would also be 2, and the
    // HashSet (keyed by persistentIdentity) would silently discard the new usage.
    UserLocationAccountEntity account = account(user(1, "alice@example.com"));
    account.addSongCreditUsage(usage(Integer.valueOf(2)));

    UserSongCreditUsageEntity added =
        account.addSongCreditUsage(usage(account.nextSongCreditUsageIdentity()));

    assertEquals(Integer.valueOf(3), added.getPersistentIdentity());
    assertEquals(2, account.getSongCreditUsages().size());
  }

  @Test
  void newAddFundsTransaction_isNeverDropped_whenItsCountWouldMatchAnExistingId() {

    UserLocationAccountEntity account = account(user(1, "alice@example.com"));
    account.addAddFundsTransaction(addFunds(Integer.valueOf(2)));

    account.addAddFundsTransaction(addFunds(account.nextAddFundsTransactionIdentity()));

    assertEquals(2, account.getAddFundsTransactions().size());
  }

  @Test
  void addingAChildWithADuplicateId_failsLoudly_ratherThanBeingSilentlyDropped() {

    UserLocationAccountEntity account = account(user(1, "alice@example.com"));
    account.addSongCreditUsage(usage(Integer.valueOf(2)));

    assertThrows(IllegalStateException.class,
        () -> account.addSongCreditUsage(usage(Integer.valueOf(2))));
  }

  @Test
  void createPlaylist_neverReusesAnExistingPlaylistsId() throws Exception {

    // Favorites was persisted with real id 1: a playlists.size() placeholder (1) would collide
    // with it, and UserRepositoryJpaImpl would merge() the new playlist over Favorites.
    UserLocationAccountEntity account =
        new UserLocationAccountEntity(Integer.valueOf(1), LOCATION_ID, 0);
    account.restorePlaylist(
        new PlaylistEntity(Integer.valueOf(1), PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME));

    PlaylistEntity partyMix = account.createPlaylist("Party Mix");

    assertEquals(Integer.valueOf(2), partyMix.getPersistentIdentity());
  }

  @Test
  void createPlaylist_staysUnique_afterADeletion() throws Exception {

    UserLocationAccountEntity account = account(user(1, "alice@example.com")); // Favorites: 0
    account.createPlaylist("A"); // 1
    account.createPlaylist("B"); // 2
    account.deletePlaylist("A");

    // playlists.size() would now be 2 again -- B's id.
    PlaylistEntity c = account.createPlaylist("C");

    List<Integer> ids =
        account.getPlaylists().stream().map(PlaylistEntity::getPersistentIdentity).toList();
    assertEquals(Integer.valueOf(3), c.getPersistentIdentity());
    assertEquals(ids.size(), ids.stream().distinct().count());
  }

  @Test
  void rehashChildCollections_letsContainsFindChildrenWhoseIdChangedInPlace() {

    UserEntity user = user(1, "alice@example.com");
    UserLocationAccountEntity account = account(user);
    UserSongCreditUsageEntity usage =
        account.addSongCreditUsage(usage(account.nextSongCreditUsageIdentity()));

    // What UserRepositoryJpaImpl's persist() does: replace the placeholder id in place.
    usage.setPersistentIdentity(Integer.valueOf(5000));
    user.rehashChildCollections();

    assertTrue(account.getSongCreditUsages().contains(usage));
    assertEquals(Integer.valueOf(5001), account.nextSongCreditUsageIdentity());
  }
}
