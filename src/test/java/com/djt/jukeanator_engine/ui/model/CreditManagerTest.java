package com.djt.jukeanator_engine.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests for {@link CreditManager}'s per-session priority-play level. */
class CreditManagerTest {

  private static CreditManager newCreditManager(int credits) {
    return new CreditManager(credits, 3, 0, 0, 1, false);
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
}
