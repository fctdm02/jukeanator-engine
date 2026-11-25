package com.djt.jukeanator_engine.domain.location.dto;

import java.io.Serializable;
import java.util.List;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;

/**
 * A slave's credit-config bundle (its {@code user-interface.*} YAML block), pushed to master over
 * the {@code /ws-slave} STOMP connection every time it (re)connects — see
 * {@code SlaveConnectionManager}. Master caches this on the corresponding {@code LocationEntity}
 * so it can price that location's Web/Mobile UI the same way it already charges Web/Mobile credits
 * for that location. {@code creditPackages} are the location's own Add Funds packages; null from a
 * slave older than per-location packages, in which case master keeps whatever it last stored.
 */
public record LocationPricingConfigDto(Integer priorityCostMultiplier, Integer creditsPerDollar,
    Integer fiveDollarBonusCredits, Integer tenDollarBonusCredits, Integer webCostMultiplier,
    Boolean displayCurrencyForCost, List<CreditPackageDto> creditPackages) implements Serializable {
}
