package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Plain, human-readable JSON representation of the singleton {@code FinancialLedgerRootEntity}.
 * This is the top-level shape written to and read from
 * {@code FinancialLedgerRootEntity.FINANCIAL_LEDGER_FILENAME}.
 */
public record FinancialLedgerRootDto(List<JukeboxSplitPeriodDto> splitPeriods,
    List<LocalCreditTransactionDto> localCreditTransactions) implements Serializable {
}
