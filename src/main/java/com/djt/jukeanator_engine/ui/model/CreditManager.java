package com.djt.jukeanator_engine.ui.model;

public class CreditManager {
  private int numCredits;
  private int totalDollarsInserted;
  private final int creditsPerDollar;
  private final int fiveDollarBonus;
  private final int tenDollarBonus;
  private final boolean displayCurrencyForCost;
  // The blended credits-per-dollar rate actually achieved by totalDollarsInserted so far this
  // session (base rate plus whatever bonus tiers have been crossed) -- recomputed only when new
  // money is inserted, so spending credits down doesn't change the rate display for what's left.
  // Resets to the base rate when the balance (and totalDollarsInserted) hits zero, ready for the
  // next customer.
  private double effectiveCreditsPerDollar;
  private final java.util.List<Runnable> listeners = new java.util.ArrayList<>();

  public CreditManager(int numCredits, int creditsPerDollar, int fiveDollarBonus,
      int tenDollarBonus, boolean displayCurrencyForCost) {
    this.numCredits = numCredits;
    this.creditsPerDollar = creditsPerDollar;
    this.fiveDollarBonus = fiveDollarBonus;
    this.tenDollarBonus = tenDollarBonus;
    this.displayCurrencyForCost = displayCurrencyForCost;
    this.effectiveCreditsPerDollar = creditsPerDollar;
  }

  public synchronized int getCredits() {
    return numCredits;
  }

  public synchronized void addDollar() {
    totalDollarsInserted++;
    numCredits += creditsPerDollar;

    // Apply tiered bonuses based on exact milestone targets
    if (totalDollarsInserted == 5) {
      numCredits += fiveDollarBonus;
    } else if (totalDollarsInserted == 10) {
      numCredits += tenDollarBonus;
      if (numCredits > creditsPerDollar * 10) {
        numCredits = (creditsPerDollar * 10) + tenDollarBonus;
      }
    } else if (totalDollarsInserted == 15) {
      numCredits += fiveDollarBonus;
    } else if (totalDollarsInserted == 20) {
      numCredits += tenDollarBonus;
      if (numCredits > (creditsPerDollar * 20) + (2 * tenDollarBonus)) {
        numCredits = (creditsPerDollar * 20) + (2 * tenDollarBonus);
      }
    }

    effectiveCreditsPerDollar = (double) numCredits / totalDollarsInserted;

    notifyListeners();
  }

  public synchronized boolean deductCredits(int amount) {
    if (numCredits >= amount && amount >= 0) {
      numCredits -= amount;
      if (numCredits == 0) {
        totalDollarsInserted = 0;
        effectiveCreditsPerDollar = creditsPerDollar;
      }
      notifyListeners();
      return true;
    }
    return false;
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
