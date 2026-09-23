package com.djt.jukeanator_engine.domain.financialledger.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link AbstractLocationTransactionEntity#changeLocationId}, exercised through both
 * concrete subclasses. Lives in the model package since the method is package-private (only
 * {@link FinancialLedgerRootEntity#changeLocationId} calls it).
 *
 * @author tmyers
 */
class AbstractLocationTransactionEntityTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);

  @Test
  void changeLocationId_onCashTransaction_replacesLocationIdAndPreservesEveryOtherField() {

    Instant timestamp = Instant.now();
    LocalCashTransactionEntity transaction = new LocalCashTransactionEntity(Integer.valueOf(1), 3,
        timestamp, PREVIOUS_LOCATION_ID, Integer.valueOf(500));

    transaction.changeLocationId(CONFIRMED_LOCATION_ID);

    assertEquals(CONFIRMED_LOCATION_ID, transaction.getLocationId());
    assertEquals(Integer.valueOf(1), transaction.getPersistentIdentity());
    assertEquals(3, transaction.getAmountDollars());
    assertEquals(timestamp, transaction.getTimestamp());
    assertEquals(Integer.valueOf(500), transaction.getSourceTransactionId());
  }

  @Test
  void changeLocationId_onCreditCardTransaction_replacesLocationIdAndPreservesEveryOtherField() {

    Instant timestamp = Instant.now();
    LocalCreditTransactionEntity transaction = new LocalCreditTransactionEntity(
        Integer.valueOf(2), 5, timestamp, PREVIOUS_LOCATION_ID, Integer.valueOf(600));

    transaction.changeLocationId(CONFIRMED_LOCATION_ID);

    assertEquals(CONFIRMED_LOCATION_ID, transaction.getLocationId());
    assertEquals(Integer.valueOf(2), transaction.getPersistentIdentity());
    assertEquals(5, transaction.getAmountDollars());
    assertEquals(timestamp, transaction.getTimestamp());
    assertEquals(Integer.valueOf(600), transaction.getSourceTransactionId());
  }

  @Test
  void changeLocationId_tagsAPreviouslyUntaggedTransaction() {

    LocalCashTransactionEntity transaction =
        new LocalCashTransactionEntity(Integer.valueOf(3), 1, Instant.now(), null);

    transaction.changeLocationId(CONFIRMED_LOCATION_ID);

    assertEquals(CONFIRMED_LOCATION_ID, transaction.getLocationId());
  }
}
