package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Shared shape for both {@code LocalCashTransactionEntity} and {@code
 * LocalCreditTransactionEntity} rows -- which revenue stream a given instance represents is
 * determined entirely by which {@code FinancialLedgerRootDto} list it appears in, not by any
 * field on the DTO itself.
 */
public record LocalTransactionDto(Integer persistentIdentity, int amountDollars, Instant timestamp,
    Integer locationId) implements Serializable {
}
