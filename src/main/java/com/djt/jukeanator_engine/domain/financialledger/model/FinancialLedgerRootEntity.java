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
 * FinancialLedgerRepositoryJpaImpl} loads {@link JukeboxSplitPeriodEntity} and
 * {@link LocalCreditTransactionEntity} rows directly and assembles this aggregate around them,
 * exactly like {@code LocationRootEntity}.
 */
public class FinancialLedgerRootEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  public static final String FINANCIAL_LEDGER_FILENAME = "JukeANator_FinancialLedger.json";

  private final List<JukeboxSplitPeriodEntity> splitPeriods = new ArrayList<>();
  private final List<LocalCreditTransactionEntity> localCreditTransactions = new ArrayList<>();

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

  public List<LocalCreditTransactionEntity> getLocalCreditTransactions() {
    return Collections.unmodifiableList(localCreditTransactions);
  }

  public void addLocalCreditTransaction(LocalCreditTransactionEntity transaction) {
    localCreditTransactions.add(transaction);
  }

  /**
   * Local credit transactions recorded strictly after {@code from}. Exclusive (rather than {@code
   * from}-inclusive) specifically so that a period boundary -- {@code from} is always either a
   * period's {@code startDate} or, at the moment of a split, the instant shared by the just-closed
   * period's {@code endDate} and the new period's {@code startDate} -- never double-counts a
   * transaction whose timestamp happens to tie exactly with it: {@code
   * FinancialLedgerServiceImpl.computeTotals} already counts a boundary-tied transaction inclusively
   * into the closing period ({@code timestamp <= to}), so it must be excluded here from also being
   * counted into the new period that opens at that same instant.
   */
  public List<LocalCreditTransactionEntity> getLocalCreditTransactionsSince(Instant from) {
    return localCreditTransactions.stream()
        .filter(t -> t.getTimestamp().isAfter(from))
        .toList();
  }
}
