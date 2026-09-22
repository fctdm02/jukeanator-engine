package com.djt.jukeanator_engine.domain.financialledger.event;

import java.time.Instant;

/**
 * Published by {@code FinancialLedgerServiceImpl} right after a local (bill-acceptor /
 * credit-card-reader) transaction is successfully stored. Harmless to publish on any instance --
 * only a slave's {@code FinancialLedgerSyncService} listens for it (standalone/master have no such
 * listener, so this is simply a no-op event there). {@code sourceTransactionId} is the just-minted
 * local {@code persistentIdentity}, carried along as the slave-to-master mirror's idempotency key.
 */
public record LocalFinancialTransactionRecordedEvent(Kind kind, Integer locationId,
    Integer sourceTransactionId, int amountDollars, Instant timestamp) {

  public enum Kind {
    CASH, CREDIT_CARD
  }
}
