package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Shared shape for both {@code LocalCashTransactionEntity} and {@code
 * LocalCreditTransactionEntity} rows -- which revenue stream a given instance represents is
 * determined entirely by which {@code FinancialLedgerRootDto} list it appears in, not by any
 * field on the DTO itself. {@code syncedToMasterAt} is absent (so {@code null}) in files written
 * before the slave-to-master outbox existed, which simply makes those transactions eligible for
 * one (idempotent) re-push.
 */
public record LocalTransactionDto(Integer persistentIdentity, int amountDollars, Instant timestamp,
    Integer locationId, Integer sourceTransactionId, Instant syncedToMasterAt)
    implements Serializable {
}
