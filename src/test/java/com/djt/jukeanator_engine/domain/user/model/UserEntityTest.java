package com.djt.jukeanator_engine.domain.user.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Covers {@link UserEntity}'s per-location accounts ({@link UserLocationAccountEntity}) and
 * {@link UserEntity#changeLocationId}. Lives in the model package since several of the methods are
 * package-private -- see {@link UserRootEntityTest} for the same behavior exercised through the
 * root.
 *
 * @author tmyers
 */
class UserEntityTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);
  private static final Integer OTHER_LOCATION_ID = Integer.valueOf(99);

  private static UserEntity newUser() {
    return new UserEntity(Integer.valueOf(1), "Alice", "Smith", "alice@example.com", "hash",
        UserRole.ROLE_USER);
  }

  private static UserSongCreditUsageEntity usage(int persistentIdentity, Integer locationId) {
    return new UserSongCreditUsageEntity(Integer.valueOf(persistentIdentity), locationId, -2,
        UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 1, 2, 4);
  }

  private static UserAddFundsTransactionEntity purchase(int persistentIdentity,
      Integer locationId) {
    return new UserAddFundsTransactionEntity(Integer.valueOf(persistentIdentity), locationId,
        "pkg-7", 12, 2, new BigDecimal("7.00"), "Visa", "tx-" + persistentIdentity, Instant.now(),
        14);
  }

  // ─── Per-location accounts ─────────────────────────────────────────────────────────────────

  @Test
  void newUser_hasNoLocationAccountsAndNoCreditsAnywhere() {

    UserEntity user = newUser();

    assertTrue(user.getLocationAccounts().isEmpty());
    assertNull(user.getLocationAccountNullIfNotExists(PREVIOUS_LOCATION_ID));
    assertEquals(0, user.getNumCredits(PREVIOUS_LOCATION_ID));
  }

  @Test
  void getOrCreateLocationAccount_createsOncePerLocation_withMyFavorites() {

    UserEntity user = newUser();

    UserLocationAccountEntity first = user.getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    UserLocationAccountEntity again = user.getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    UserLocationAccountEntity other = user.getOrCreateLocationAccount(OTHER_LOCATION_ID);

    assertSame(first, again);
    assertEquals(2, user.getLocationAccounts().size());
    assertSame(user, first.getUser());
    assertEquals(PREVIOUS_LOCATION_ID, first.getLocationId());
    assertNotNull(first.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME));
    assertNotNull(other.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME));
    assertEquals(0, first.getNumCredits());
  }

  @Test
  void balances_playsAndPlaylists_areSeparatePerLocation() throws Exception {

    UserEntity user = newUser();
    UserLocationAccountEntity here = user.getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    UserLocationAccountEntity there = user.getOrCreateLocationAccount(OTHER_LOCATION_ID);

    here.setNumCredits(14);
    here.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    here.createPlaylist("Party Mix");

    assertEquals(14, user.getNumCredits(PREVIOUS_LOCATION_ID));
    assertEquals(0, user.getNumCredits(OTHER_LOCATION_ID));
    assertTrue(there.getSongPlayHistory().isEmpty());
    assertNull(there.getPlaylistByNameNullIfNotExists("Party Mix"));
  }

  @Test
  void account_refusesASongOrLedgerEntryFromAnotherLocation() {

    UserLocationAccountEntity account =
        newUser().getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);

    assertThrows(IllegalArgumentException.class,
        () -> account.addSongToSongPlayHistory(new SongIdentifier(OTHER_LOCATION_ID, 1, 2)));
    assertThrows(IllegalArgumentException.class,
        () -> account.addSongCreditUsage(usage(10, OTHER_LOCATION_ID)));
    assertThrows(IllegalArgumentException.class,
        () -> account.addAddFundsTransaction(purchase(11, OTHER_LOCATION_ID)));
  }

  @Test
  void addSongToSongPlayHistory_movesARepeatToTheNewestEnd() {

    UserLocationAccountEntity account =
        newUser().getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    account.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    account.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 3, 4));
    account.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));

    assertEquals(List.of(new SongIdentifier(PREVIOUS_LOCATION_ID, 3, 4),
        new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2)), account.getSongPlayHistory());
  }

  @Test
  void anonymize_clearsPlaysAndPlaylists_butKeepsBalanceAndLedgers() {

    UserEntity user = newUser();
    UserLocationAccountEntity account = user.getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    account.setNumCredits(5);
    account.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    account.addSongCreditUsage(usage(10, PREVIOUS_LOCATION_ID));
    account.addAddFundsTransaction(purchase(11, PREVIOUS_LOCATION_ID));

    user.anonymize("closed-x" + UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN, "unusable");

    assertTrue(account.getSongPlayHistory().isEmpty());
    assertTrue(account.getPlaylists().isEmpty());
    assertEquals(5, account.getNumCredits());
    assertEquals(1, account.getSongCreditUsages().size());
    assertEquals(1, account.getAddFundsTransactions().size());
  }

  // ─── changeLocationId ─────────────────────────────────────────────────────────────────────

  @Test
  void changeLocationId_retagsTheAccountAndEverythingInIt() {

    UserEntity user = newUser();
    UserLocationAccountEntity account = user.getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    account.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    PlaylistEntity favorites =
        account.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    favorites.addSong(new SongIdentifier(PREVIOUS_LOCATION_ID, 3, 4));
    UserSongCreditUsageEntity spend = account.addSongCreditUsage(usage(10, PREVIOUS_LOCATION_ID));
    UserAddFundsTransactionEntity bought =
        account.addAddFundsTransaction(purchase(11, PREVIOUS_LOCATION_ID));

    user.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(CONFIRMED_LOCATION_ID, account.getLocationId());
    assertSame(account, user.getLocationAccountNullIfNotExists(CONFIRMED_LOCATION_ID));
    assertEquals(List.of(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2)),
        account.getSongPlayHistory());
    assertEquals(List.of(new SongIdentifier(CONFIRMED_LOCATION_ID, 3, 4)), favorites.getSongs());
    assertEquals(CONFIRMED_LOCATION_ID, spend.getLocationId());
    assertEquals(CONFIRMED_LOCATION_ID, bought.getLocationId());
  }

  @Test
  void changeLocationId_leavesOtherLocationsAccountsAlone() {

    UserEntity user = newUser();
    UserLocationAccountEntity other = user.getOrCreateLocationAccount(OTHER_LOCATION_ID);
    other.addSongToSongPlayHistory(new SongIdentifier(OTHER_LOCATION_ID, 1, 2));

    user.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(OTHER_LOCATION_ID, other.getLocationId());
    assertEquals(List.of(new SongIdentifier(OTHER_LOCATION_ID, 1, 2)),
        other.getSongPlayHistory());
  }

  @Test
  void changeLocationId_doesNothingWhenTheUserAlreadyHasAnAccountAtTheNewId() {

    UserEntity user = newUser();
    UserLocationAccountEntity previous = user.getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    user.getOrCreateLocationAccount(CONFIRMED_LOCATION_ID);

    user.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(PREVIOUS_LOCATION_ID, previous.getLocationId());
  }

  @Test
  void changeLocationId_toleratesAPlaylistWithNullSongs() {

    UserEntity user = newUser();
    UserLocationAccountEntity account = user.getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    PlaylistEntity empty = account.restorePlaylist(new PlaylistEntity(Integer.valueOf(2), "Empty"));
    empty.setSongs(null);

    assertDoesNotThrow(() -> user.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));
    assertNull(empty.getSongs());
  }

  // ─── Playlists (now on the location account) ──────────────────────────────────────────────

  @Test
  void renamePlaylist_renamesInPlace_keepingIdentityAndSongs() throws Exception {

    UserLocationAccountEntity account =
        newUser().getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    PlaylistEntity partyMix = account.createPlaylist("Party Mix");
    partyMix.addSong(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    Integer persistentIdentity = partyMix.getPersistentIdentity();

    assertTrue(account.renamePlaylist("Party Mix", "Dance Mix"));

    assertNull(account.getPlaylistByNameNullIfNotExists("Party Mix"));
    PlaylistEntity renamed = account.getPlaylistByName("Dance Mix");
    assertSame(partyMix, renamed);
    assertEquals(persistentIdentity, renamed.getPersistentIdentity());
    assertEquals(List.of(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2)), renamed.getSongs());
  }

  @Test
  void renamePlaylist_toSameName_isNoOp() throws Exception {

    UserLocationAccountEntity account =
        newUser().getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    account.createPlaylist("Party Mix");

    assertFalse(account.renamePlaylist("Party Mix", "Party Mix"));
    assertNotNull(account.getPlaylistByNameNullIfNotExists("Party Mix"));
  }

  @Test
  void renamePlaylist_toExistingName_throws() throws Exception {

    UserLocationAccountEntity account =
        newUser().getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    account.createPlaylist("Party Mix");
    account.createPlaylist("Dance Mix");

    assertThrows(EntityAlreadyExistsException.class,
        () -> account.renamePlaylist("Party Mix", "Dance Mix"));
    assertNotNull(account.getPlaylistByNameNullIfNotExists("Party Mix"));
  }

  @Test
  void renamePlaylist_toOrFromMyFavorites_throws() throws Exception {

    UserLocationAccountEntity account =
        newUser().getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    account.createPlaylist("Party Mix");

    assertThrows(IllegalArgumentException.class, () -> account
        .renamePlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, "Not Favorites"));
    assertThrows(IllegalArgumentException.class,
        () -> account.renamePlaylist("Party Mix", PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME));
  }

  @Test
  void renamePlaylist_missingPlaylist_throws() {

    UserLocationAccountEntity account =
        newUser().getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);

    assertThrows(EntityDoesNotExistException.class,
        () -> account.renamePlaylist("No Such Playlist", "Anything"));
  }

  @Test
  void samePlaylistName_mayExistAtTwoLocations() throws Exception {

    UserEntity user = newUser();
    user.getOrCreateLocationAccount(PREVIOUS_LOCATION_ID).createPlaylist("Party Mix");

    assertDoesNotThrow(
        () -> user.getOrCreateLocationAccount(OTHER_LOCATION_ID).createPlaylist("Party Mix"));
  }

  @Test
  void ensureMyFavoritesPlaylist_recreatesItAndDropsDuplicates() {

    UserLocationAccountEntity account =
        new UserLocationAccountEntity(Integer.valueOf(1), PREVIOUS_LOCATION_ID, 0);
    assertTrue(account.ensureMyFavoritesPlaylist(), "missing My Favorites is created");

    PlaylistEntity favorites =
        account.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    favorites.setSongs(new ArrayList<>(List.of(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2),
        new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2))));

    assertTrue(account.ensureMyFavoritesPlaylist());
    assertEquals(1, favorites.getSongs().size());
    assertFalse(account.ensureMyFavoritesPlaylist());
  }
}
