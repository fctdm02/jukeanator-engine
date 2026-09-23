package com.djt.jukeanator_engine.domain.financialledger.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link FinancialLedgerRootEntity#changeLocationId}, the in-memory counterpart of {@code
 * LocationRepositoryJpaImpl.changeLocationId}'s location_transaction update.
 *
 * @author tmyers
 */
class FinancialLedgerRootEntityTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);
  private static final Integer OTHER_LOCATION_ID = Integer.valueOf(99);

  @Test
  void changeLocationId_retagsCashAndCreditCardTransactionsUnderThePreviousIdOnly() {

    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();
    Instant now = Instant.now();

    LocalCashTransactionEntity ownCash =
        new LocalCashTransactionEntity(Integer.valueOf(1), 1, now, PREVIOUS_LOCATION_ID);
    LocalCashTransactionEntity otherCash =
        new LocalCashTransactionEntity(Integer.valueOf(2), 1, now, OTHER_LOCATION_ID);
    LocalCashTransactionEntity untaggedCash =
        new LocalCashTransactionEntity(Integer.valueOf(3), 1, now, null);
    LocalCreditTransactionEntity ownCard =
        new LocalCreditTransactionEntity(Integer.valueOf(4), 1, now, PREVIOUS_LOCATION_ID);
    LocalCreditTransactionEntity otherCard =
        new LocalCreditTransactionEntity(Integer.valueOf(5), 1, now, OTHER_LOCATION_ID);

    root.addLocalCashTransaction(ownCash);
    root.addLocalCashTransaction(otherCash);
    root.addLocalCashTransaction(untaggedCash);
    root.addLocalCreditCardTransaction(ownCard);
    root.addLocalCreditCardTransaction(otherCard);

    root.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(CONFIRMED_LOCATION_ID, ownCash.getLocationId());
    assertEquals(CONFIRMED_LOCATION_ID, ownCard.getLocationId());
    assertEquals(OTHER_LOCATION_ID, otherCash.getLocationId());
    assertEquals(OTHER_LOCATION_ID, otherCard.getLocationId());
    assertNull(untaggedCash.getLocationId(),
        "A transaction with no location tag should stay untagged");
  }

  @Test
  void changeLocationId_carriesMirroredTransactionsSourceIdentityToTheNewId() {

    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();
    Instant now = Instant.now();
    Integer sourceTransactionId = Integer.valueOf(500);

    LocalCashTransactionEntity mirroredCash = new LocalCashTransactionEntity(Integer.valueOf(1),
        1, now, PREVIOUS_LOCATION_ID, sourceTransactionId);
    LocalCreditTransactionEntity mirroredCard = new LocalCreditTransactionEntity(
        Integer.valueOf(2), 1, now, PREVIOUS_LOCATION_ID, sourceTransactionId);
    root.addLocalCashTransaction(mirroredCash);
    root.addLocalCreditCardTransaction(mirroredCard);

    root.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    // sourceTransactionId is untouched, so the idempotency check now matches under the new id
    // only -- a retried mirror push after the correction is still recognised as a duplicate.
    assertEquals(sourceTransactionId, mirroredCash.getSourceTransactionId());
    assertEquals(sourceTransactionId, mirroredCard.getSourceTransactionId());
    assertTrue(root.hasLocalCashTransactionFromSource(CONFIRMED_LOCATION_ID, sourceTransactionId));
    assertTrue(
        root.hasLocalCreditCardTransactionFromSource(CONFIRMED_LOCATION_ID, sourceTransactionId));
    assertFalse(root.hasLocalCashTransactionFromSource(PREVIOUS_LOCATION_ID, sourceTransactionId));
    assertFalse(
        root.hasLocalCreditCardTransactionFromSource(PREVIOUS_LOCATION_ID, sourceTransactionId));
  }

  @Test
  void changeLocationId_leavesSplitPeriodsUntouched() {

    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();
    JukeboxSplitPeriodEntity period =
        new JukeboxSplitPeriodEntity(Integer.valueOf(1), Instant.now());
    root.addSplitPeriod(period);

    root.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    // Periods carry no location id of their own -- their transient parentLocation is the same
    // LocationEntity instance LocationService re-keys in place.
    assertEquals(1, root.getSplitPeriods().size());
    assertNull(period.getParentLocation());
  }
}
