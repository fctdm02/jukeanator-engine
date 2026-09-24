package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A jukebox-split period, both as displayed (see {@code FinancialLedgerService.getAllPeriods()})
 * and as persisted by {@code FinancialLedgerRepositoryFileSystemImpl}. {@code locationId}, {@code
 * sourcePeriodId}, and {@code syncedToMasterAt} carry the slave-to-master mirror's state -- see
 * {@code JukeboxSplitPeriodEntity}.
 */
public record JukeboxSplitPeriodDto(Integer persistentIdentity, Instant startDate,
    Instant endDate, Integer splitPercentageToOwner, BigDecimal cashTotal, BigDecimal cardTotal,
    BigDecimal mobileTotal, BigDecimal totalEarned, BigDecimal amountDueOwner,
    BigDecimal amountDueOperator, Integer locationId, Integer sourcePeriodId,
    Instant syncedToMasterAt) implements Serializable {
}
