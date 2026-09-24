package com.djt.jukeanator_engine.domain.financialledger.dto;

/**
 * One slave-side ledger record master has not yet acknowledged, as handed to {@code
 * FinancialLedgerSyncService} by {@code FinancialLedgerService.getPendingMasterSync()}. {@code
 * payload} is the wire body for {@code kind}'s endpoint: a {@link LocalTransactionSyncDto} for
 * {@code CASH}/{@code CREDIT_CARD}, a {@link JukeboxSplitPeriodSyncDto} for {@code SPLIT_PERIOD}.
 * {@code persistentIdentity} is the slave's own id for the record, used to mark it acknowledged.
 */
public record MasterSyncOutboxEntry(Kind kind, Integer locationId, Integer persistentIdentity,
    Object payload) {

  public enum Kind {
    CASH, CREDIT_CARD, SPLIT_PERIOD
  }
}
