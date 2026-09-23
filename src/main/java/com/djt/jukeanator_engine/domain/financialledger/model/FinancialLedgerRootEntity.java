package com.djt.jukeanator_engine.domain.financialledger.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * Singleton in-memory aggregate root holding every jukebox-split period and every local (cash /
 * credit-card-reader) credit transaction. Not itself JPA-mapped -- {@code
 * FinancialLedgerRepositoryJpaImpl} loads {@link JukeboxSplitPeriodEntity}, {@link
 * LocalCashTransactionEntity}, and {@link LocalCreditTransactionEntity} rows directly and
 * assembles this aggregate around them, exactly like {@code LocationRootEntity}.
 */
public class FinancialLedgerRootEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  public static final String FINANCIAL_LEDGER_FILENAME = "JukeANator_FinancialLedger.json";

  private final List<JukeboxSplitPeriodEntity> splitPeriods = new ArrayList<>();
  private final List<LocalCashTransactionEntity> localCashTransactions = new ArrayList<>();
  private final List<LocalCreditTransactionEntity> localCreditCardTransactions = new ArrayList<>();

  public FinancialLedgerRootEntity() {
    super(Integer.valueOf(0));
  }

  @Override
  public String getNaturalIdentity() {
    return "FinancialLedgerRootEntity";
  }

  /** Every split period, ordered oldest-first (the last entry, if any, is the open period). */
  public List<JukeboxSplitPeriodEntity> getSplitPeriods() {
    return Collections.unmodifiableList(splitPeriods);
  }

  public void addSplitPeriod(JukeboxSplitPeriodEntity period) {
    splitPeriods.add(period);
    splitPeriods.sort(Comparator.comparing(JukeboxSplitPeriodEntity::getStartDate));
  }

  /** The single open (not-yet-finalized) period, or {@code null} if none has been created yet. */
  public JukeboxSplitPeriodEntity getCurrentPeriod() {
    return splitPeriods.stream()
        .filter(JukeboxSplitPeriodEntity::isOpen)
        .findFirst()
        .orElse(null);
  }

  public List<LocalCashTransactionEntity> getLocalCashTransactions() {
    return Collections.unmodifiableList(localCashTransactions);
  }

  public void addLocalCashTransaction(LocalCashTransactionEntity transaction) {
    localCashTransactions.add(transaction);
  }

  /** Local cash transactions recorded at or after {@code from} (inclusive). */
  public List<LocalCashTransactionEntity> getLocalCashTransactionsSince(Instant from) {
    return localCashTransactions.stream()
        .filter(t -> !t.getTimestamp().isBefore(from))
        .toList();
  }

  public List<LocalCreditTransactionEntity> getLocalCreditCardTransactions() {
    return Collections.unmodifiableList(localCreditCardTransactions);
  }

  public void addLocalCreditCardTransaction(LocalCreditTransactionEntity transaction) {
    localCreditCardTransactions.add(transaction);
  }

  /** Local credit-card transactions recorded at or after {@code from} (inclusive). */
  public List<LocalCreditTransactionEntity> getLocalCreditCardTransactionsSince(Instant from) {
    return localCreditCardTransactions.stream()
        .filter(t -> !t.getTimestamp().isBefore(from))
        .toList();
  }

  /**
   * True if a mirrored cash transaction from {@code (locationId, sourceTransactionId)} has
   * already been received -- makes a retried slave-to-master mirror push a safe no-op. Pure
   * in-memory check against the already-loaded aggregate; no repository query needed.
   */
  public boolean hasLocalCashTransactionFromSource(Integer locationId, Integer sourceTransactionId) {
    return hasTransactionFromSource(localCashTransactions, locationId, sourceTransactionId);
  }

  /** Same as {@link #hasLocalCashTransactionFromSource}, for the credit-card stream. */
  public boolean hasLocalCreditCardTransactionFromSource(Integer locationId,
      Integer sourceTransactionId) {
    return hasTransactionFromSource(localCreditCardTransactions, locationId, sourceTransactionId);
  }

  /**
   * Re-tags every local transaction recorded under {@code oldLocationId} with {@code
   * newLocationId} -- the in-memory counterpart of {@code
   * LocationRepositoryJpaImpl.changeLocationId}'s location_transaction update.
   */
  public void changeLocationId(Integer oldLocationId, Integer newLocationId) {
    changeLocationId(localCashTransactions, oldLocationId, newLocationId);
    changeLocationId(localCreditCardTransactions, oldLocationId, newLocationId);
  }

  private static void changeLocationId(
      List<? extends AbstractLocationTransactionEntity> transactions, Integer oldLocationId,
      Integer newLocationId) {
    transactions.stream()
        .filter(t -> oldLocationId.equals(t.getLocationId()))
        .forEach(t -> t.changeLocationId(newLocationId));
  }

  private static boolean hasTransactionFromSource(
      List<? extends AbstractLocationTransactionEntity> transactions, Integer locationId,
      Integer sourceTransactionId) {
    return transactions.stream().anyMatch(t -> locationId.equals(t.getLocationId())
        && sourceTransactionId.equals(t.getSourceTransactionId()));
  }
}
