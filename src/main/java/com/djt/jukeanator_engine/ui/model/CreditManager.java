package com.djt.jukeanator_engine.ui.model;

public class CreditManager {
  private int numCredits;
  private int totalDollarsInserted;
  private final int creditsPerDollar;
  private final int fiveDollarBonus;
  private final int tenDollarBonus;
  private final java.util.List<Runnable> listeners = new java.util.ArrayList<>();

  public CreditManager(int numCredits, int creditsPerDollar, int fiveDollarBonus, int tenDollarBonus) {
    this.numCredits = numCredits;
    this.creditsPerDollar = creditsPerDollar;
    this.fiveDollarBonus = fiveDollarBonus;
    this.tenDollarBonus = tenDollarBonus;
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
    }
    notifyListeners();
  }

  public synchronized boolean deductCredits(int amount) {
    if (numCredits >= amount && amount >= 0) {
      numCredits -= amount;
      notifyListeners();
      return true;
    }
    return false;
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
