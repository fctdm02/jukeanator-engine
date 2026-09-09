package com.djt.jukeanator_engine.domain.user.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AddFundsResponseDto(Integer numCredits, BigDecimal balanceUsd, int creditsAdded,
    int bonusCreditsAdded, String paymentSource, String transactionId, Instant timestamp) {
}
