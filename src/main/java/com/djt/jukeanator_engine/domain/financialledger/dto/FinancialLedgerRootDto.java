package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Plain, human-readable JSON representation of the singleton {@code FinancialLedgerRootEntity}.
 * This is the top-level shape written to and read from
 * {@code FinancialLedgerRootEntity.FINANCIAL_LEDGER_FILENAME}. {@code mobileCreditUsages} is
 * absent (so {@code null}) in files written before the master-to-slave mobile mirror existed.
 */
public record FinancialLedgerRootDto(List<JukeboxSplitPeriodDto> splitPeriods,
    List<LocalTransactionDto> localCashTransactions,
    List<LocalTransactionDto> localCreditCardTransactions,
    List<LocationMobileCreditUsageDto> mobileCreditUsages) implements Serializable {
}
