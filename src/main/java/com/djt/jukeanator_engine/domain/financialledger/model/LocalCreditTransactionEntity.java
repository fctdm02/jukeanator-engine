package com.djt.jukeanator_engine.domain.financialledger.model;

import java.time.Instant;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * One append-only local credit-card-reader credit-award record, analogous to {@code
 * UserSongCreditUsageEntity} for mobile/web credits. Recorded only for genuine hardware pulses --
 * see the "➕ Credits" admin override in {@code AdminPanel.doIncrementCredits()}, which
 * deliberately does <em>not</em> go through this entity.
 */
@Entity
@DiscriminatorValue("CREDIT_CARD")
public class LocalCreditTransactionEntity extends AbstractLocationTransactionEntity {

  private static final long serialVersionUID = 1L;

  protected LocalCreditTransactionEntity() {} // for JPA

  public LocalCreditTransactionEntity(Integer persistentIdentity, int amountDollars,
      Instant timestamp, Integer locationId) {
    this(persistentIdentity, amountDollars, timestamp, locationId, null);
  }

  public LocalCreditTransactionEntity(Integer persistentIdentity, int amountDollars,
      Instant timestamp, Integer locationId, Integer sourceTransactionId) {
    super(persistentIdentity, amountDollars, timestamp, locationId, sourceTransactionId);
  }

  @Override
  public String getNaturalIdentity() {
    return "LocalCreditTransaction/" + getTimestamp() + "/" + getPersistentIdentity();
  }
}
