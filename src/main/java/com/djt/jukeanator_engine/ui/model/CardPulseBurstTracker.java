package com.djt.jukeanator_engine.ui.model;

import javax.swing.Timer;

/**
 * Groups the burst of $1 pulses a pulse-mode card reader (Nayax VPOS Touch) sends for one card
 * purchase, so the UI can confirm the purchase once, with its total.
 *
 * <p>
 * The reader sends one pulse per dollar with a short gap between pulses, and nothing marks the end
 * of a purchase. A purchase is therefore treated as finished once no pulse has arrived for
 * {@code quietPeriodMs}. Credits are still awarded per pulse by the caller, so this class only
 * drives the confirmation message and never decides what the patron receives.
 *
 * <p>
 * All methods are expected to be called on the Swing event dispatch thread.
 */
public class CardPulseBurstTracker {

  /** Receives the totals of a finished burst. */
  @FunctionalInterface
  public interface BurstListener {
    void onBurstComplete(int dollars, int creditsAdded);
  }

  private final BurstListener listener;
  private final Timer quietTimer;
  private int dollars;
  private int creditsAdded;

  public CardPulseBurstTracker(int quietPeriodMs, BurstListener listener) {
    this.listener = listener;
    this.quietTimer = new Timer(quietPeriodMs, e -> completeBurst());
    this.quietTimer.setRepeats(false);
  }

  /** Records one $1 pulse that added {@code credits} credits, and restarts the quiet period. */
  public void recordPulse(int credits) {
    dollars++;
    creditsAdded += credits;
    quietTimer.restart();
  }

  /**
   * Ends the current burst now and reports it, if any pulses were recorded. Called by the quiet
   * timer, and directly by tests.
   */
  public void completeBurst() {
    quietTimer.stop();
    if (dollars == 0) {
      return;
    }
    int burstDollars = dollars;
    int burstCredits = creditsAdded;
    dollars = 0;
    creditsAdded = 0;
    listener.onBurstComplete(burstDollars, burstCredits);
  }

  /** Dollars recorded in the burst still in progress. */
  public int getPendingDollars() {
    return dollars;
  }
}
