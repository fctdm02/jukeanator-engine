package com.djt.jukeanator_engine.domain.financialledger.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * One append-only local bill-acceptor credit-award record -- the cash counterpart of {@link
 * LocalCreditTransactionEntity}, kept in its own table rather than sharing one behind a
 * {@code source} column, so cash and credit-card-reader revenue are never ambiguous by table name
 * alone. Recorded only for genuine hardware pulses -- see the "➕ Credits" admin override in
 * {@code AdminPanel.doIncrementCredits()}, which deliberately does <em>not</em> go through this
 * entity.
 */
@Entity
@Table(name = "local_cash_transactions")
public class LocalCashTransactionEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(name = "amount_dollars", nullable = false)
  private int amountDollars;

  @Column(nullable = false)
  private Instant timestamp;

  @Column(name = "location_id")
  private Integer locationId;

  protected LocalCashTransactionEntity() {} // for JPA

  public LocalCashTransactionEntity(Integer persistentIdentity, int amountDollars,
      Instant timestamp, Integer locationId) {
    super(persistentIdentity);
    this.amountDollars = amountDollars;
    this.timestamp = timestamp;
    this.locationId = locationId;
  }

  @Override
  public String getNaturalIdentity() {
    return "LocalCashTransaction/" + timestamp + "/" + getPersistentIdentity();
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
