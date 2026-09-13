package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.io.Serializable;
import java.time.Instant;

public record LocalCreditTransactionDto(Integer persistentIdentity, String source,
    int amountDollars, Instant timestamp, Integer locationId) implements Serializable {
}
