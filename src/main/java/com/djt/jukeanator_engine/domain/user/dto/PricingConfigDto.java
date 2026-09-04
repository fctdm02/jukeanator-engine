package com.djt.jukeanator_engine.domain.user.dto;

/**
 * The Web/Mobile UI's own copy of a location's effective pricing config, so the client can price
 * and afford-check actions (e.g. the song popup's priority-play cost) the same way the server
 * will actually charge for them, and honor {@code displayCurrencyForCost}.
 */
public record PricingConfigDto(int priorityCostMultiplier, int creditsPerDollar,
    int fiveDollarBonusCredits, int tenDollarBonusCredits, int webCostMultiplier,
    boolean displayCurrencyForCost) {
}
