package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wire shape a slave POSTs to master to mirror one finalized jukebox-split period -- the split
 * counterpart of {@link LocalTransactionSyncDto}. {@code sourcePeriodId} is the slave's own local
 * {@code persistentIdentity} for the period, master's idempotency key for a retried push; the URL
 * path carries {@code locationId}.
 */
public record JukeboxSplitPeriodSyncDto(Integer sourcePeriodId, Instant startDate,
    Instant endDate, Integer splitPercentageToOwner, BigDecimal cashTotal, BigDecimal cardTotal,
    BigDecimal mobileTotal, BigDecimal totalEarned, BigDecimal amountDueOwner,
    BigDecimal amountDueOperator) {
}
