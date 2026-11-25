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

  private static UserEntity alice() {
    return new UserEntity(Integer.valueOf(1), "Alice", "Smith", "alice@example.com", "hash",
        UserRole.ROLE_USER);
  }

  @Test
  void changeLocationId_retagsTheAccountAtThePreviousIdOnly() {

    UserEntity user = alice();

    UserLocationAccountEntity own = user.getOrCreateLocationAccount(PREVIOUS_LOCATION_ID);
    own.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    PlaylistEntity ownFavorites =
        own.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    ownFavorites.addSong(new SongIdentifier(PREVIOUS_LOCATION_ID, 5, 6));
    UserSongCreditUsageEntity ownUsage = own.addSongCreditUsage(
        new UserSongCreditUsageEntity(Integer.valueOf(10), PREVIOUS_LOCATION_ID, -2,
            UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 5, 6, 4));

    UserLocationAccountEntity other = user.getOrCreateLocationAccount(OTHER_LOCATION_ID);
    other.addSongToSongPlayHistory(new SongIdentifier(OTHER_LOCATION_ID, 3, 4));
    UserSongCreditUsageEntity otherUsage = other.addSongCreditUsage(
        new UserSongCreditUsageEntity(Integer.valueOf(11), OTHER_LOCATION_ID, -2,
            UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 7, 8, 2));

    UserRootEntity root = new UserRootEntity();
    root.addUser(user);

    root.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(CONFIRMED_LOCATION_ID, own.getLocationId());
    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2), own.getSongPlayHistory().get(0));
    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 5, 6), ownFavorites.getSongs().get(0));
    assertEquals(CONFIRMED_LOCATION_ID, ownUsage.getLocationId());
    assertEquals(OTHER_LOCATION_ID, other.getLocationId());
    assertEquals(new SongIdentifier(OTHER_LOCATION_ID, 3, 4), other.getSongPlayHistory().get(0));
    assertEquals(OTHER_LOCATION_ID, otherUsage.getLocationId());
  }

  @Test
  void closeUser_anonymizesInPlace_rekeysUnderTheClosedAddress_andKeepsFinancialRecords() {

    UserEntity user = alice();
    UserLocationAccountEntity account = user.getOrCreateLocationAccount(CONFIRMED_LOCATION_ID);
    account.setNumCredits(13);
    account.addSongToSongPlayHistory(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2));
    user.addToSearchHistory("Billy Idol", 10);
    UserSongCreditUsageEntity usage = account.addSongCreditUsage(
        new UserSongCreditUsageEntity(Integer.valueOf(10), CONFIRMED_LOCATION_ID, -2,
            UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 1, 2, 4));
    UserAddFundsTransactionEntity purchase = account.addAddFundsTransaction(
        new UserAddFundsTransactionEntity(Integer.valueOf(20), CONFIRMED_LOCATION_ID, "pkg-7", 12,
            1, new BigDecimal("7.00"), "PayPal", "txn-1", Instant.now(), 13));
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
    assertTrue(account.getSongPlayHistory().isEmpty());
    assertTrue(user.getSearchHistory().isEmpty());
    assertTrue(account.getPlaylists().isEmpty());
    assertEquals(13, account.getNumCredits());
    assertEquals(Set.of(usage), account.getSongCreditUsages());
    assertEquals(Set.of(purchase), account.getAddFundsTransactions());
    assertEquals(closedEmail, usage.getUserEmail());
  }

  @Test
  void closeUser_unknownEmail_returnsNullAndChangesNothing() {

    UserRootEntity root = new UserRootEntity();
    root.addUser(alice());

    assertNull(root.closeUser("bob@example.com",
        "closed-1" + UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN, "unusable"));
    assertFalse(root.getUserByEmailAddressNullIfNotExists("alice@example.com").isClosed());
  }

  @Test
  void anonymize_refusesAnAddressOutsideTheClosedAccountDomain() {

    UserEntity user = alice();

    // Otherwise the closed account could collide with, or be logged into as, a real address.
    assertThrows(IllegalArgumentException.class,
        () -> user.anonymize("someone@example.com", "unusable"));
    assertFalse(user.isClosed());
    assertEquals("alice@example.com", user.getEmailAddress());
  }

  @Test
  void removeUnstoredAddFundsTransaction_findsItEvenAfterItsIdChangedInPlace() {

    UserLocationAccountEntity account = alice().getOrCreateLocationAccount(CONFIRMED_LOCATION_ID);
    UserAddFundsTransactionEntity purchase = account.addAddFundsTransaction(
        new UserAddFundsTransactionEntity(Integer.valueOf(1), CONFIRMED_LOCATION_ID, "pkg-7", 12,
            1, new BigDecimal("7.00"), "PayPal", "txn-1", Instant.now(), 13));

    // A failed JPA store may already have replaced the placeholder id in place, leaving the
    // purchase in a stale hash bucket where remove() would miss it.
    purchase.setPersistentIdentity(Integer.valueOf(9001));

    assertTrue(account.removeUnstoredAddFundsTransaction(purchase));
    assertTrue(account.getAddFundsTransactions().isEmpty());
  }
}
