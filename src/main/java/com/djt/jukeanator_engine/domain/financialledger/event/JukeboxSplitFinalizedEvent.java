package com.djt.jukeanator_engine.domain.financialledger.event;

/**
 * Published by {@code FinancialLedgerServiceImpl.addSplit()} right after a jukebox-split period is
 * finalized and stored. Harmless to publish on any instance -- only a slave's {@code
 * FinancialLedgerSyncService} listens, pushing the finalized period up to master right away rather
 * than waiting for its next periodic outbox sweep.
 */
public record JukeboxSplitFinalizedEvent(Integer locationId, Integer periodId) {
}
