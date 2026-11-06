package com.djt.jukeanator_engine.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link CardPulseBurstTracker}. */
class CardPulseBurstTrackerTest {

  /** Long enough that the quiet timer never fires during a test; bursts end via completeBurst(). */
  private static final int NEVER_MS = 60_000;

  private final List<int[]> bursts = new ArrayList<>();

  private CardPulseBurstTracker newTracker() {
    return new CardPulseBurstTracker(NEVER_MS,
        (dollars, credits) -> bursts.add(new int[] {dollars, credits}));
  }

  @Test
  void completeBurst_reportsTheTotalsOfEveryPulseInTheBurst() {
    CardPulseBurstTracker tracker = newTracker();

    for (int pulse = 0; pulse < 10; pulse++) {
      tracker.recordPulse(2);
    }
    tracker.completeBurst();

    assertEquals(1, bursts.size());
    assertEquals(10, bursts.get(0)[0]);
    assertEquals(20, bursts.get(0)[1]);
  }

  @Test
  void completeBurst_reportsNothingWithoutPulses() {
    CardPulseBurstTracker tracker = newTracker();

    tracker.completeBurst();

    assertTrue(bursts.isEmpty());
  }

  @Test
  void eachBurstIsReportedSeparately() {
    CardPulseBurstTracker tracker = newTracker();

    for (int pulse = 0; pulse < 5; pulse++) {
      tracker.recordPulse(1);
    }
    tracker.completeBurst();
    tracker.completeBurst();
    for (int pulse = 0; pulse < 20; pulse++) {
      tracker.recordPulse(2);
    }
    tracker.completeBurst();

    assertEquals(2, bursts.size());
    assertEquals(5, bursts.get(0)[0]);
    assertEquals(20, bursts.get(1)[0]);
    assertEquals(40, bursts.get(1)[1]);
    assertEquals(0, tracker.getPendingDollars());
  }
}
