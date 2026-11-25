package com.djt.jukeanator_engine.domain.financialledger.event;

import com.djt.jukeanator_engine.domain.financialledger.dto.LocalMobileTransactionDto;

/**
 * Published on master by {@code FinancialLedgerServiceImpl.recordMobilePurchase} right after a
 * Web/Mobile Add Funds purchase is stored in its location's ledger. {@code
 * MobileTransactionSlaveNotifier} listens and mirrors it down to that location's slave. Kept apart
 * from {@link LocalFinancialTransactionRecordedEvent}, which drives the opposite (slave-to-master)
 * mirror for cash and card.
 */
public record MobilePurchaseRecordedEvent(LocalMobileTransactionDto transaction) {
}
