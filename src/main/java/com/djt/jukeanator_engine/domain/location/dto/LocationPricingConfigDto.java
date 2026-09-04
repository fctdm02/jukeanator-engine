package com.djt.jukeanator_engine.domain.location.dto;

import java.io.Serializable;

/**
 * A slave's credit-config bundle (its {@code user-interface.*} YAML block), pushed to master over
 * the {@code /ws-slave} STOMP connection every time it (re)connects — see
 * {@code SlaveConnectionManager}. Master caches this on the corresponding {@code LocationEntity}
 * so it can price that location's Web/Mobile UI the same way it already charges Web/Mobile credits
 * for that location.
 */
public record LocationPricingConfigDto(Integer priorityCostMultiplier, Integer creditsPerDollar,
    Integer fiveDollarBonusCredits, Integer tenDollarBonusCredits, Integer webCostMultiplier,
    Boolean displayCurrencyForCost) implements Serializable {
}
