package com.djt.jukeanator_engine.domain.user.exception;

/**
 * Thrown when a Web/Mobile UI user's credit balance does not cover a queue operation (adding a
 * song, or moving/removing a queued one). Always thrown before the operation runs, so nothing has
 * been queued, forwarded to a slave, or charged. The message is written for the patron.
 */
public class InsufficientCreditsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final int cost;
  private final int balance;

  public InsufficientCreditsException(int cost, int balance) {
    super("This costs " + cost + " credits, but your balance is " + balance
        + " credits. Add funds to continue.");
    this.cost = cost;
    this.balance = balance;
  }

  public int getCost() {
    return cost;
  }

  public int getBalance() {
    return balance;
  }
}
