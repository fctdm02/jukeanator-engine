package com.djt.jukeanator_engine.domain.financialledger.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.financialledger.exception.FinancialLedgerException;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCashTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;

/** Unit tests for {@link FinancialLedgerRepositoryFileSystemImpl}. */
class FinancialLedgerRepositoryFileSystemImplTest {

  @Test
  void loadAggregateRoot_throws_whenNoFileExistsYet(@TempDir Path basePath) {

    FinancialLedgerRepositoryFileSystemImpl repository =
        new FinancialLedgerRepositoryFileSystemImpl(basePath.toString());

    assertThrows(EntityDoesNotExistException.class,
        () -> repository.loadAggregateRoot("FinancialLedgerRootEntity"));
  }

  @Test
  void loadAggregateRoot_byOtherPersistentIdentity_isUnsupported(@TempDir Path basePath) {

    FinancialLedgerRepositoryFileSystemImpl repository =
        new FinancialLedgerRepositoryFileSystemImpl(basePath.toString());

    assertThrows(FinancialLedgerException.class, () -> repository.loadAggregateRoot(0));
  }

  @Test
  void storeAggregateRoot_thenLoadAggregateRoot_roundTripsPeriodsAndLocalTransactions(
      @TempDir Path basePath) throws Exception {

    FinancialLedgerRepositoryFileSystemImpl repository =
        new FinancialLedgerRepositoryFileSystemImpl(basePath.toString());

    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();

    Instant periodStart = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant periodEnd = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    JukeboxSplitPeriodEntity finalizedPeriod =
        new JukeboxSplitPeriodEntity(Integer.valueOf(1), periodStart);
    finalizedPeriod.finalizePeriod(periodEnd, 50, new BigDecimal("12.00"), new BigDecimal("8.00"),
        new BigDecimal("5.00"), new BigDecimal("25.00"), new BigDecimal("12.50"),
        new BigDecimal("12.50"));
    root.addSplitPeriod(finalizedPeriod);

    JukeboxSplitPeriodEntity openPeriod =
        new JukeboxSplitPeriodEntity(Integer.valueOf(2), periodEnd);
    root.addSplitPeriod(openPeriod);

    root.addLocalCashTransaction(
        new LocalCashTransactionEntity(Integer.valueOf(3), 1, periodEnd, Integer.valueOf(9)));
    root.addLocalCreditCardTransaction(
        new LocalCreditTransactionEntity(Integer.valueOf(4), 1, periodEnd, null));

    repository.storeAggregateRoot(root);

    assertTrue(Files.exists(basePath.resolve(FinancialLedgerRootEntity.FINANCIAL_LEDGER_FILENAME)));

    FinancialLedgerRootEntity reloaded = repository.loadAggregateRoot("FinancialLedgerRootEntity");

    assertEquals(2, reloaded.getSplitPeriods().size());
    JukeboxSplitPeriodEntity reloadedFinalized = reloaded.getSplitPeriods().get(0);
    assertEquals(periodStart, reloadedFinalized.getStartDate());
    assertEquals(periodEnd, reloadedFinalized.getEndDate());
    assertEquals(Integer.valueOf(50), reloadedFinalized.getSplitPercentageToOwner());
    assertEquals(new BigDecimal("12.00"), reloadedFinalized.getCashTotal());
    assertEquals(new BigDecimal("8.00"), reloadedFinalized.getCardTotal());
    assertEquals(new BigDecimal("5.00"), reloadedFinalized.getMobileTotal());
    assertEquals(new BigDecimal("25.00"), reloadedFinalized.getTotalEarned());
    assertEquals(new BigDecimal("12.50"), reloadedFinalized.getAmountDueOwner());
    assertEquals(new BigDecimal("12.50"), reloadedFinalized.getAmountDueOperator());

    JukeboxSplitPeriodEntity reloadedOpen = reloaded.getSplitPeriods().get(1);
    assertTrue(reloadedOpen.isOpen());
    assertEquals(reloaded.getCurrentPeriod(), reloadedOpen);

    assertEquals(1, reloaded.getLocalCashTransactions().size());
    LocalCashTransactionEntity cashTx = reloaded.getLocalCashTransactions().get(0);
    assertEquals(1, cashTx.getAmountDollars());
    assertEquals(Integer.valueOf(9), cashTx.getLocationId());

    assertEquals(1, reloaded.getLocalCreditCardTransactions().size());
    LocalCreditTransactionEntity cardTx = reloaded.getLocalCreditCardTransactions().get(0);
    assertEquals(1, cardTx.getAmountDollars());
    assertNull(cardTx.getLocationId());
  }

  @Test
  void nextPersistentIdentity_isSeededAboveTheHighestIdOnDisk_afterReload(@TempDir Path basePath)
      throws Exception {

    FinancialLedgerRepositoryFileSystemImpl writer =
        new FinancialLedgerRepositoryFileSystemImpl(basePath.toString());

    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();
    root.addSplitPeriod(new JukeboxSplitPeriodEntity(Integer.valueOf(1), Instant.now()));
    root.addLocalCashTransaction(
        new LocalCashTransactionEntity(Integer.valueOf(50), 1, Instant.now(), null));
    root.addLocalCreditCardTransaction(
        new LocalCreditTransactionEntity(Integer.valueOf(30), 1, Instant.now(), null));
    writer.storeAggregateRoot(root);

    // A fresh repository instance (as happens on app restart) must not restart id minting at 1 --
    // it has to seed its counter from what's already on disk across ALL THREE child collections.
    FinancialLedgerRepositoryFileSystemImpl reopened =
        new FinancialLedgerRepositoryFileSystemImpl(basePath.toString());
    reopened.loadAggregateRoot("FinancialLedgerRootEntity");

    Integer nextId = reopened.nextPersistentIdentity();
    assertTrue(nextId.intValue() > 50, "Expected an id greater than the existing max (50), got: " + nextId);
  }

  @Test
  void nextPersistentIdentity_returnsIncreasingUniqueIds_withoutAnyPriorLoad(@TempDir Path basePath) {

    FinancialLedgerRepositoryFileSystemImpl repository =
        new FinancialLedgerRepositoryFileSystemImpl(basePath.toString());

    Integer first = repository.nextPersistentIdentity();
    Integer second = repository.nextPersistentIdentity();

    assertNotEquals(first, second);
    assertTrue(second.intValue() > first.intValue());
  }
}
