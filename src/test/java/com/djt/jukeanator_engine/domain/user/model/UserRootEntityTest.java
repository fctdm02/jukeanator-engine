package com.djt.jukeanator_engine.domain.user.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Covers {@link UserRootEntity#changeLocationId}, the in-memory counterpart of {@code
 * LocationRepositoryJpaImpl.changeLocationId}'s user table updates, and {@link
 * UserRootEntity#closeUser}, which closes an account while keeping its financial records.
 *
 * @author tmyers
 */
class UserRootEntityTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);
  private static final Integer OTHER_LOCATION_ID = Integer.valueOf(99);

  @Test
  void changeLocationId_retagsPlayHistoryPlaylistSongsAndCreditUsages_underPreviousIdOnly() {

    UserEntity user = new UserEntity(Integer.valueOf(1), "Alice", "Smith", "alice@example.com",
        "hash", Integer.valueOf(6), UserRole.ROLE_USER);

    user.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    user.addSongToSongPlayHistory(new SongIdentifier(OTHER_LOCATION_ID, 3, 4));

    PlaylistEntity favorites = user.createMyFavoritesPlaylist();
    favorites.addSong(new SongIdentifier(PREVIOUS_LOCATION_ID, 5, 6));
    favorites.addSong(new SongIdentifier(OTHER_LOCATION_ID, 7, 8));

    UserSongCreditUsageEntity ownUsage = user.addUserSongCreditUsage(
        new UserSongCreditUsageEntity(Integer.valueOf(10), PREVIOUS_LOCATION_ID, -2,
            UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 5, 6, 4));
    UserSongCreditUsageEntity otherUsage = user.addUserSongCreditUsage(
        new UserSongCreditUsageEntity(Integer.valueOf(11), OTHER_LOCATION_ID, -2,
            UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 7, 8, 2));

    UserRootEntity root = new UserRootEntity();
    root.addUser(user);

    root.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2), user.getSongPlayHistory().get(0));
    assertEquals(new SongIdentifier(OTHER_LOCATION_ID, 3, 4), user.getSongPlayHistory().get(1));
    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 5, 6), favorites.getSongs().get(0));
    assertEquals(new SongIdentifier(OTHER_LOCATION_ID, 7, 8), favorites.getSongs().get(1));
    assertEquals(CONFIRMED_LOCATION_ID, ownUsage.getLocationId());
    assertEquals(OTHER_LOCATION_ID, otherUsage.getLocationId());
  }

  @Test
  void closeUser_anonymizesInPlace_rekeysUnderTheClosedAddress_andKeepsFinancialRecords() {

    UserEntity user = new UserEntity(Integer.valueOf(1), "Alice", "Smith", "alice@example.com",
        "hash", Integer.valueOf(6), UserRole.ROLE_USER);
    user.addSongToSongPlayHistory(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2));
    user.addToSearchHistory("Billy Idol", 10);
    UserSongCreditUsageEntity usage = user.addUserSongCreditUsage(
        new UserSongCreditUsageEntity(Integer.valueOf(10), CONFIRMED_LOCATION_ID, -2,
            UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 1, 2, 4));
    UserAddFundsTransactionEntity purchase = user.addUserAddFundsTransaction(
        new UserAddFundsTransactionEntity(Integer.valueOf(20), "pkg-7", 12, 1,
            new BigDecimal("7.00"), "PayPal", "txn-1", Instant.now(), 13));
    UserRootEntity root = new UserRootEntity();
    root.addUser(user);

    String closedEmail = "closed-1" + UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN;
    UserEntity closed = root.closeUser("alice@example.com", closedEmail, "unusable");

    assertSame(user, closed, "closed in place -- the same row, with the same id");
    assertNull(root.getUserByEmailAddressNullIfNotExists("alice@example.com"));
    assertSame(user, root.getUserByEmailAddressNullIfNotExists(closedEmail));
    assertTrue(user.isClosed());
    assertEquals("Closed", user.getFirstName());
    assertEquals("Account", user.getLastName());
    assertEquals("unusable", user.getPasswordHash());
    assertTrue(user.getSongPlayHistory().isEmpty());
    assertTrue(user.getSearchHistory().isEmpty());
    assertTrue(user.getPlaylists().isEmpty());
    assertEquals(Set.of(usage), user.getUserSongCreditUsages());
    assertEquals(Set.of(purchase), user.getUserAddFundsTransactions());
    assertEquals(closedEmail, usage.getUserEmail());
  }

  @Test
  void closeUser_unknownEmail_returnsNullAndChangesNothing() {

    UserRootEntity root = new UserRootEntity();
    root.addUser(new UserEntity(Integer.valueOf(1), "Alice", "Smith", "alice@example.com", "hash",
        Integer.valueOf(0), UserRole.ROLE_USER));

    assertNull(root.closeUser("bob@example.com",
        "closed-1" + UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN, "unusable"));
    assertFalse(root.getUserByEmailAddressNullIfNotExists("alice@example.com").isClosed());
  }

  @Test
  void anonymize_refusesAnAddressOutsideTheClosedAccountDomain() {

    UserEntity user = new UserEntity(Integer.valueOf(1), "Alice", "Smith", "alice@example.com",
        "hash", Integer.valueOf(0), UserRole.ROLE_USER);

    // Otherwise the closed account could collide with, or be logged into as, a real address.
    assertThrows(IllegalArgumentException.class,
        () -> user.anonymize("someone@example.com", "unusable"));
    assertFalse(user.isClosed());
    assertEquals("alice@example.com", user.getEmailAddress());
  }

  @Test
  void removeUnstoredUserAddFundsTransaction_findsItEvenAfterItsIdChangedInPlace() {

    UserEntity user = new UserEntity(Integer.valueOf(1), "Alice", "Smith", "alice@example.com",
        "hash", Integer.valueOf(0), UserRole.ROLE_USER);
    UserAddFundsTransactionEntity purchase = user.addUserAddFundsTransaction(
        new UserAddFundsTransactionEntity(Integer.valueOf(1), "pkg-7", 12, 1,
            new BigDecimal("7.00"), "PayPal", "txn-1", Instant.now(), 13));

    // A failed JPA store may already have replaced the placeholder id in place, leaving the
    // purchase in a stale hash bucket where remove() would miss it.
    purchase.setPersistentIdentity(Integer.valueOf(9001));

    assertTrue(user.removeUnstoredUserAddFundsTransaction(purchase));
    assertTrue(user.getUserAddFundsTransactions().isEmpty());
  }
}
