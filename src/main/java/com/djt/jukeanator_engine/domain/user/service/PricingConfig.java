package com.djt.jukeanator_engine.domain.user.service;

/**
 * A location's effective credit-pricing config — either read directly from
 * {@code JukeANatorUserInterfaceProperties} (standalone/slave, same JVM as the JFC/Swing UI) or
 * resolved from that location's synced {@code LocationEntity} columns (master). See
 * {@link PricingService}.
 */
public record PricingConfig(int priorityCostMultiplier, int creditsPerDollar,
    int fiveDollarBonusCredits, int tenDollarBonusCredits, int webCostMultiplier,
    boolean displayCurrencyForCost) {
}
