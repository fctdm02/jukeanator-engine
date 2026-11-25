package com.djt.jukeanator_engine.domain.user.dto;

import java.time.Instant;

/**
 * The result of an Add Funds purchase: {@code numCredits} is the new balance at
 * {@code locationId}, the only location where the purchased credits can be used. Carries no dollar
 * balance: the Web/Mobile UI shows credits instead -- see docs/pricing.md.
 */
public record AddFundsResponseDto(Integer numCredits, int creditsAdded, int bonusCreditsAdded,
    String paymentSource, String transactionId, Instant timestamp, Integer locationId,
    String locationName) {
}
