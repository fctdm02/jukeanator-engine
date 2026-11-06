package com.djt.jukeanator_engine.ui.model;

public class CreditManager {
  private int numCredits;
  private int totalDollarsInserted;
  // Credit-card dollars track their own independent milestone progress -- a session never mixes
  // cash and card, but keeping the counters separate means the card's divided-down payout formula
  // never disturbs the bill acceptor's tier math (and vice versa).
  private int totalCreditCardDollarsInserted;
  private int rawCreditCardCredits;
  private int creditCardCreditsAwarded;
  private final int creditsPerDollar;
  private final int fiveDollarBonus;
  private final int tenDollarBonus;
  private final int webCostMultiplier;
  private final boolean displayCurrencyForCost;
  // The blended credits-per-dollar rate actually achieved by totalDollarsInserted so far this
  // session (base rate plus whatever bonus tiers have been crossed) -- recomputed only when new
  // money is inserted, so spending credits down doesn't change the rate display for what's left.
  // Resets to the base rate when the balance (and totalDollarsInserted) hits zero, ready for the
  // next customer.
  private double effectiveCreditsPerDollar;
  // The priority level of the current user's first priority play this session (credits 0 ->
  // positive -> 0). Every later priority play in the same session reuses this level, so it costs
  // the same and queues behind the earlier ones in the order added. Cleared when credits hit 0.
  private Integer sessionPriorityLevel;
  // The songs ("albumId/songId") the current user has queued this session. Every jukebox song is
  // queued as LOCAL, so this is how the current user's selections are told apart from earlier
  // users' -- a priority play is only offered to jump someone else's. Cleared when credits hit 0.
  private final java.util.Set<String> sessionQueuedSongs = new java.util.HashSet<>();
  private final java.util.List<Runnable> listeners = new java.util.ArrayList<>();

  public CreditManager(int numCredits, int creditsPerDollar, int fiveDollarBonus,
      int tenDollarBonus, int webCostMultiplier, boolean displayCurrencyForCost) {
    this.numCredits = numCredits;
    this.creditsPerDollar = creditsPerDollar;
    this.fiveDollarBonus = fiveDollarBonus;
    this.tenDollarBonus = tenDollarBonus;
    this.webCostMultiplier = webCostMultiplier;
    this.displayCurrencyForCost = displayCurrencyForCost;
    this.effectiveCreditsPerDollar = creditsPerDollar;
  }

  public synchronized int getCredits() {
    return numCredits;
  }

  public synchronized void addDollar() {
    totalDollarsInserted++;
    numCredits = addTierDollar(numCredits, totalDollarsInserted);

    effectiveCreditsPerDollar =
        (double) numCredits / (totalDollarsInserted + totalCreditCardDollarsInserted);

    notifyListeners();
  }

  /**
   * Awards credits for one credit-card dollar (e.g. one Nayax VPOS Touch pulse), at a reduced
   * rate that offsets the reader's subscription/wireless fees and per-transaction processing cost.
   * Applies the same tiered bonus schedule as {@link #addDollar()} to the card's own running
   * total, then divides the result by {@code webCostMultiplier} and rounds up to the nearest whole
   * credit.
   *
   * @return the number of credits this dollar added to the balance
   */
  public synchronized int addCreditCardDollar() {
    totalCreditCardDollarsInserted++;
    rawCreditCardCredits = addTierDollar(rawCreditCardCredits, totalCreditCardDollarsInserted);

    int actualCardCredits = ceilDiv(rawCreditCardCredits, webCostMultiplier);
    int creditsAdded = actualCardCredits - creditCardCreditsAwarded;
    numCredits += creditsAdded;
    creditCardCreditsAwarded = actualCardCredits;

    effectiveCreditsPerDollar =
        (double) numCredits / (totalDollarsInserted + totalCreditCardDollarsInserted);

    notifyListeners();
    return creditsAdded;
  }

  /**
   * The total credits a fresh credit-card purchase of {@code dollars} awards -- the same result as
   * calling {@link #addCreditCardDollar()} {@code dollars} times at the start of a session. Used
   * for the on-screen card pricing line and the Nayax reader's price-button titles.
   */
  public int creditCardCreditsFor(int dollars) {
    int raw = 0;
    for (int dollar = 1; dollar <= dollars; dollar++) {
      raw = addTierDollar(raw, dollar);
    }
    return ceilDiv(raw, webCostMultiplier);
  }

  /**
   * Adds one dollar's worth of credits to {@code credits}, applying the tiered bonus for reaching
   * {@code dollarCount} dollars this session (exact milestones at $5, $10, $15 and $20, with the
   * $10 and $20 totals capped).
   */
  private int addTierDollar(int credits, int dollarCount) {
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

  private static int ceilDiv(int numerator, int denominator) {
    return -Math.floorDiv(-numerator, denominator);
  }

  public synchronized boolean deductCredits(int amount) {
    if (numCredits >= amount && amount >= 0) {
      numCredits -= amount;
      if (numCredits == 0) {
        totalDollarsInserted = 0;
        totalCreditCardDollarsInserted = 0;
        rawCreditCardCredits = 0;
        creditCardCreditsAwarded = 0;
        effectiveCreditsPerDollar = creditsPerDollar;
        sessionPriorityLevel = null;
        sessionQueuedSongs.clear();
      }
      notifyListeners();
      return true;
    }
    return false;
  }

  /**
   * The priority level locked in by the current user's first priority play, or {@code null} if
   * they have not made one this session.
   */
  public synchronized Integer getSessionPriorityLevel() {
    return sessionPriorityLevel;
  }

  /**
   * Records the level of a priority play just paid for. Only the first one in a session is kept,
   * and only while credits remain -- a priority play that spends the last credit ends the session.
   */
  public synchronized void recordPriorityPlayLevel(int level) {
    if (sessionPriorityLevel == null && numCredits > 0) {
      sessionPriorityLevel = level;
    }
  }

  /**
   * Records a song the current user has just paid to queue (normal or priority play). Ignored when
   * that play spent the last credit, since the session -- and the user -- has ended.
   */
  public synchronized void recordSongQueued(Integer albumId, Integer songId) {
    if (numCredits > 0) {
      sessionQueuedSongs.add(songKey(albumId, songId));
    }
  }

  /** Whether the current user queued this song during the current credit session. */
  public synchronized boolean isSongQueuedThisSession(Integer albumId, Integer songId) {
    return sessionQueuedSongs.contains(songKey(albumId, songId));
  }

  private static String songKey(Integer albumId, Integer songId) {
    return albumId + "/" + songId;
  }

  /**
   * Formats a credit amount for display -- {@code "Ncr"}, or the actual dollar cost (e.g.
   * {@code "$0.33"}, rounded to the nearest cent) when {@code display-currency-for-cost} is
   * enabled, converted at whichever tier's blended rate this session has actually achieved.
   */
  public synchronized String formatCredits(int credits) {
    if (!displayCurrencyForCost) {
      return credits + "cr";
    }
    return formatDollars(credits);
  }

  /** Formats a credit shortfall -- {@code "ADD N CREDIT(S)"} or {@code "ADD $X.XX"}. */
  public synchronized String formatShortfall(int neededCredits) {
    if (!displayCurrencyForCost) {
      return "ADD " + neededCredits + (neededCredits == 1 ? " CREDIT" : " CREDITS");
    }
    return "ADD " + formatDollars(neededCredits);
  }

  private String formatDollars(int credits) {
    java.math.BigDecimal amount = java.math.BigDecimal.valueOf(credits)
        .divide(java.math.BigDecimal.valueOf(effectiveCreditsPerDollar), 2,
            java.math.RoundingMode.HALF_UP);
    return "$" + amount.toPlainString();
  }

  public synchronized void addListener(Runnable listener) {
    listeners.add(listener);
  }

  public synchronized void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  private void notifyListeners() {
    for (Runnable listener : listeners) {
      javax.swing.SwingUtilities.invokeLater(listener);
    }
  }
}
