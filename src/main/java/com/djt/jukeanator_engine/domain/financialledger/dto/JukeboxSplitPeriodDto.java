package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record JukeboxSplitPeriodDto(Integer persistentIdentity, Instant startDate,
    Instant endDate, Integer splitPercentageToOwner, BigDecimal cashTotal, BigDecimal cardTotal,
    BigDecimal mobileTotal, BigDecimal totalEarned, BigDecimal amountDueOwner,
    BigDecimal amountDueOperator) implements Serializable {
}
