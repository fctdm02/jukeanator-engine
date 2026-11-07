package com.djt.jukeanator_engine.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Tests for {@link CreditManager}'s per-session priority-play level. */
class CreditManagerTest {

  private static CreditManager newCreditManager(int credits) {
    return new CreditManager(credits, 3, 0, 0, BigDecimal.ONE, false);
  }

  @Test
  void sessionPriorityLevel_isNullUntilAPriorityPlayIsRecorded() {
    assertNull(newCreditManager(10).getSessionPriorityLevel());
  }

  @Test
  void recordPriorityPlayLevel_keepsTheFirstLevelForTheRestOfTheSession() {
    CreditManager creditManager = newCreditManager(18);

    creditManager.deductCredits(2);
    creditManager.recordPriorityPlayLevel(1);
    creditManager.deductCredits(2);
    creditManager.recordPriorityPlayLevel(2);

    assertEquals(Integer.valueOf(1), creditManager.getSessionPriorityLevel());
  }

  @Test
  void sessionPriorityLevel_isClearedWhenCreditsReachZero() {
    CreditManager creditManager = newCreditManager(4);

    creditManager.deductCredits(2);
    creditManager.recordPriorityPlayLevel(1);
    creditManager.deductCredits(2);

    assertNull(creditManager.getSessionPriorityLevel());
  }

  @Test
  void recordPriorityPlayLevel_isIgnoredWhenThePlaySpentTheLastCredit() {
    CreditManager creditManager = newCreditManager(2);

    creditManager.deductCredits(2);
    creditManager.recordPriorityPlayLevel(1);

    assertNull(creditManager.getSessionPriorityLevel());
  }

  @Test
  void songsQueuedThisSession_areTrackedUntilCreditsReachZero() {
    CreditManager creditManager = newCreditManager(3);

    creditManager.deductCredits(1);
    creditManager.recordSongQueued(10, 1);
    assertTrue(creditManager.isSongQueuedThisSession(10, 1));
    assertFalse(creditManager.isSongQueuedThisSession(10, 2));

    creditManager.deductCredits(2);
    assertFalse(creditManager.isSongQueuedThisSession(10, 1),
        "the next user does not inherit the previous user's songs");
  }

  @Test
  void recordSongQueued_isIgnoredWhenThePlaySpentTheLastCredit() {
    CreditManager creditManager = newCreditManager(1);

    creditManager.deductCredits(1);
    creditManager.recordSongQueued(10, 1);

    assertFalse(creditManager.isSongQueuedThisSession(10, 1));
  }

  @Test
  void newMoneyAfterTheSessionEnds_startsAFreshSession() {
    CreditManager creditManager = newCreditManager(4);
    creditManager.deductCredits(2);
    creditManager.recordPriorityPlayLevel(1);
    creditManager.deductCredits(2);

    creditManager.addDollar();
    creditManager.deductCredits(1);
    creditManager.recordPriorityPlayLevel(3);

    assertEquals(Integer.valueOf(3), creditManager.getSessionPriorityLevel());
  }

  /** The kiosk's default pricing: 3 credits per dollar, $5/$10 bonuses of 3/10, card rate / 2. */
  private static CreditManager newDefaultPricingCreditManager() {
    return newCreditManagerWithCardMultiplier("2");
  }

  private static CreditManager newCreditManagerWithCardMultiplier(String creditCardCostMultiplier) {
    return new CreditManager(0, 3, 3, 10, new BigDecimal(creditCardCostMultiplier), false);
  }

  @Test
  void creditCardCreditsFor_supportsADecimalMultiplier() {
    CreditManager creditManager = newCreditManagerWithCardMultiplier("1.5");

    // Cash credits 18 / 40 / 58 / 80, divided by 1.5 and rounded up.
    assertEquals(12, creditManager.creditCardCreditsFor(5));
    assertEquals(27, creditManager.creditCardCreditsFor(10));
    assertEquals(39, creditManager.creditCardCreditsFor(15));
    assertEquals(54, creditManager.creditCardCreditsFor(20));
  }

  @Test
  void addCreditCardDollar_withADecimalMultiplier_matchesCreditCardCreditsFor() {
    for (int dollars : new int[] {1, 5, 10, 15, 20}) {
      CreditManager creditManager = newCreditManagerWithCardMultiplier("1.5");

      int creditsAdded = 0;
      for (int pulse = 0; pulse < dollars; pulse++) {
        creditsAdded += creditManager.addCreditCardDollar();
      }

      assertEquals(creditManager.creditCardCreditsFor(dollars), creditsAdded, "$" + dollars);
    }
  }

  @Test
  void constructor_rejectsANonPositiveCardMultiplier() {
    assertThrows(IllegalArgumentException.class, () -> newCreditManagerWithCardMultiplier("0"));
  }

  @Test
  void creditCardCreditsFor_matchesTheCardReaderPriceButtons() {
    CreditManager creditManager = newDefaultPricingCreditManager();

    assertEquals(9, creditManager.creditCardCreditsFor(5));
    assertEquals(20, creditManager.creditCardCreditsFor(10));
    assertEquals(29, creditManager.creditCardCreditsFor(15));
    assertEquals(40, creditManager.creditCardCreditsFor(20));
  }

  @Test
  void creditCardCreditsFor_equalsTheCreditsAwardedPulseByPulse() {
    for (int dollars : new int[] {1, 5, 10, 15, 20}) {
      CreditManager creditManager = newDefaultPricingCreditManager();

      int creditsAdded = 0;
      for (int pulse = 0; pulse < dollars; pulse++) {
        creditsAdded += creditManager.addCreditCardDollar();
      }

      assertEquals(creditManager.creditCardCreditsFor(dollars), creditsAdded, "$" + dollars);
      assertEquals(creditsAdded, creditManager.getCredits(), "$" + dollars);
    }
  }

  @Test
  void addDollar_keepsTheBillAcceptorTierCredits() {
    CreditManager creditManager = newDefaultPricingCreditManager();

    for (int dollar = 0; dollar < 5; dollar++) {
      creditManager.addDollar();
    }
    assertEquals(18, creditManager.getCredits());

    for (int dollar = 0; dollar < 5; dollar++) {
      creditManager.addDollar();
    }
    assertEquals(40, creditManager.getCredits());
  }
}
