package com.djt.jukeanator_engine.domain.financialledger.model;

import java.time.Instant;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * One append-only local bill-acceptor credit-award record -- the cash counterpart of {@link
 * LocalCreditTransactionEntity}. Recorded only for genuine hardware pulses -- see the "➕ Credits"
 * admin override in {@code AdminPanel.doIncrementCredits()}, which deliberately does
 * <em>not</em> go through this entity.
 */
@Entity
@DiscriminatorValue("CASH")
public class LocalCashTransactionEntity extends AbstractLocationTransactionEntity {

  private static final long serialVersionUID = 1L;

  protected LocalCashTransactionEntity() {} // for JPA

  public LocalCashTransactionEntity(Integer persistentIdentity, int amountDollars,
      Instant timestamp, Integer locationId) {
    this(persistentIdentity, amountDollars, timestamp, locationId, null);
  }

  public LocalCashTransactionEntity(Integer persistentIdentity, int amountDollars,
      Instant timestamp, Integer locationId, Integer sourceTransactionId) {
    super(persistentIdentity, amountDollars, timestamp, locationId, sourceTransactionId);
  }

  @Override
  public String getNaturalIdentity() {
    return "LocalCashTransaction/" + getTimestamp() + "/" + getPersistentIdentity();
  }
}
