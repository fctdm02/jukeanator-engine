package com.djt.jukeanator_engine.domain.user.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published after a successful Add Funds purchase, decoupling the receipt email from the charge.
 * {@code newBalance} is the balance at {@code locationId}, where the credits can be used;
 * {@code locationName} may be null if this instance does not know it.
 */
public record PurchaseCompletedEvent(String emailAddress, String firstName, int credits,
    int bonusCredits, BigDecimal amountUsd, String paymentSource, String transactionId,
    Instant timestamp, int newBalance, Integer locationId, String locationName) {
}
