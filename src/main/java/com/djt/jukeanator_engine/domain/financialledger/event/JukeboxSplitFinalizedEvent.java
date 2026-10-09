package com.djt.jukeanator_engine.domain.financialledger.event;

/**
 * Published by {@code FinancialLedgerServiceImpl.addSplit()} right after a jukebox-split period is
 * finalized and stored. A slave's {@code FinancialLedgerSyncService} listens, pushing the finalized
 * period up to master right away rather than waiting for its next periodic outbox sweep, and on
 * every instance {@code FinancialLedgerSplitEmailListener} emails the split summary.
 */
public record JukeboxSplitFinalizedEvent(Integer locationId, Integer periodId) {
}
