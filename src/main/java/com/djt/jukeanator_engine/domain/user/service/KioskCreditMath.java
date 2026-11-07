package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The JFC/Swing kiosk's credit math for bill-acceptor and card-reader dollars, kept free of Swing
 * so both {@code CreditManager} (the kiosk) and {@link PricingStartupCheck} (startup pricing
 * warnings) use the same formulas. See {@code docs/pricing.md}.
 */
public final class KioskCreditMath {

  /**
   * The price buttons configured on the Nayax card reader ({@code Pulse Increment} in Nayax Core).
   * Change this together with the reader's own settings.
   */
  public static final List<Integer> CARD_READER_PRICE_DOLLARS = List.of(5, 10, 15, 20);

  private KioskCreditMath() {}

  /**
   * Adds one dollar's worth of credits to {@code credits}, applying the tiered bonus for reaching
   * {@code dollarCount} dollars this session (exact milestones at $5, $10, $15 and $20, with the
   * $10 and $20 totals capped).
   */
  public static int addTierDollar(int credits, int dollarCount, int creditsPerDollar,
      int fiveDollarBonus, int tenDollarBonus) {
    credits += creditsPerDollar;

    if (dollarCount == 5) {
      credits += fiveDollarBonus;
    } else if (dollarCount == 10) {
      credits += tenDollarBonus;
      if (credits > creditsPerDollar * 10) {
        credits = (creditsPerDollar * 10) + tenDollarBonus;
      }
    } else if (dollarCount == 15) {
      credits += fiveDollarBonus;
    } else if (dollarCount == 20) {
      credits += tenDollarBonus;
      if (credits > (creditsPerDollar * 20) + (2 * tenDollarBonus)) {
        credits = (creditsPerDollar * 20) + (2 * tenDollarBonus);
      }
    }
    return credits;
  }

  /** The bill-acceptor (cash) credits for {@code dollars} inserted at the start of a session. */
  public static int cashCreditsFor(int dollars, int creditsPerDollar, int fiveDollarBonus,
      int tenDollarBonus) {
    int credits = 0;
    for (int dollar = 1; dollar <= dollars; dollar++) {
      credits = addTierDollar(credits, dollar, creditsPerDollar, fiveDollarBonus, tenDollarBonus);
    }
    return credits;
  }

  /**
   * Converts cash-rate credits to card-reader credits: divided by
   * {@code credit-card-cost-multiplier} and rounded up to a whole credit.
   */
  public static int toCreditCardCredits(int cashRateCredits, BigDecimal creditCardCostMultiplier) {
    return BigDecimal.valueOf(cashRateCredits)
        .divide(creditCardCostMultiplier, 0, RoundingMode.CEILING).intValueExact();
  }

  /** The card-reader credits for a {@code dollars} charge at the start of a session. */
  public static int creditCardCreditsFor(int dollars, int creditsPerDollar, int fiveDollarBonus,
      int tenDollarBonus, BigDecimal creditCardCostMultiplier) {
    return toCreditCardCredits(
        cashCreditsFor(dollars, creditsPerDollar, fiveDollarBonus, tenDollarBonus),
        creditCardCostMultiplier);
  }

  /**
   * Rejects a missing or non-positive {@code credit-card-cost-multiplier}.
   *
   * @return the multiplier
   */
  public static BigDecimal requireValidCreditCardCostMultiplier(BigDecimal multiplier) {
    if (multiplier == null || multiplier.signum() <= 0) {
      throw new IllegalArgumentException(
          "user-interface.credit-card-cost-multiplier must be greater than 0, but was "
              + multiplier);
    }
    return multiplier;
  }
}
