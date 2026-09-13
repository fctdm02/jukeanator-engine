package com.djt.jukeanator_engine.domain.financialledger.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * One append-only local (bill-acceptor or credit-card-reader) credit-award record, analogous to
 * {@code CreditTransactionEntity} for mobile/web credits. Recorded only for genuine hardware
 * pulses -- see the "➕ Credits" admin override in {@code AdminPanel.doIncrementCredits()}, which
 * deliberately does <em>not</em> go through this entity.
 */
@Entity
@Table(name = "local_credit_transactions")
public class LocalCreditTransactionEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LocalCreditSource source;

  @Column(name = "amount_dollars", nullable = false)
  private int amountDollars;

  @Column(nullable = false)
  private Instant timestamp;

  @Column(name = "location_id")
  private Integer locationId;

  protected LocalCreditTransactionEntity() {} // for JPA

  public LocalCreditTransactionEntity(Integer persistentIdentity, LocalCreditSource source,
      int amountDollars, Instant timestamp, Integer locationId) {
    super(persistentIdentity);
    this.source = source;
    this.amountDollars = amountDollars;
    this.timestamp = timestamp;
    this.locationId = locationId;
  }

  @Override
  public String getNaturalIdentity() {
    return source + "/" + timestamp + "/" + getPersistentIdentity();
  }

  public LocalCreditSource getSource() {
    return source;
  }

  public int getAmountDollars() {
    return amountDollars;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public Integer getLocationId() {
    return locationId;
  }
}
