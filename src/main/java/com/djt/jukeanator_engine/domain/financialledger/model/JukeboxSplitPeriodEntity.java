package com.djt.jukeanator_engine.domain.financialledger.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * One jukebox-split reconciliation period between the operator and a location owner. The period
 * with a {@code null} {@link #endDate} is the current/open one -- its sub-totals are always
 * computed live by {@code FinancialLedgerService}, never read from this entity, since they are
 * {@code null} here until {@link #finalizePeriod} locks them in.
 */
@Entity
@Table(name = "location_jukebox_split")
public class JukeboxSplitPeriodEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(name = "start_date", nullable = false)
  private Instant startDate;

  @Column(name = "end_date")
  private Instant endDate;

  @Column(name = "split_percentage_to_owner")
  private Integer splitPercentageToOwner;

  @Column(name = "cash_total", precision = 12, scale = 2)
  private BigDecimal cashTotal;

  @Column(name = "card_total", precision = 12, scale = 2)
  private BigDecimal cardTotal;

  @Column(name = "mobile_total", precision = 12, scale = 2)
  private BigDecimal mobileTotal;

  @Column(name = "total_earned", precision = 12, scale = 2)
  private BigDecimal totalEarned;

  @Column(name = "amount_due_owner", precision = 12, scale = 2)
  private BigDecimal amountDueOwner;

  @Column(name = "amount_due_operator", precision = 12, scale = 2)
  private BigDecimal amountDueOperator;

  protected JukeboxSplitPeriodEntity() {} // for JPA

  public JukeboxSplitPeriodEntity(Integer persistentIdentity, Instant startDate) {
    super(persistentIdentity);
    this.startDate = startDate;
  }

  /** Full reconstruction constructor, used only when loading a persisted period back. */
  public JukeboxSplitPeriodEntity(Integer persistentIdentity, Instant startDate, Instant endDate,
      Integer splitPercentageToOwner, BigDecimal cashTotal, BigDecimal cardTotal,
      BigDecimal mobileTotal, BigDecimal totalEarned, BigDecimal amountDueOwner,
      BigDecimal amountDueOperator) {

    super(persistentIdentity);
    this.startDate = startDate;
    this.endDate = endDate;
    this.splitPercentageToOwner = splitPercentageToOwner;
    this.cashTotal = cashTotal;
    this.cardTotal = cardTotal;
    this.mobileTotal = mobileTotal;
    this.totalEarned = totalEarned;
    this.amountDueOwner = amountDueOwner;
    this.amountDueOperator = amountDueOperator;
  }

  @Override
  public String getNaturalIdentity() {
    return "JukeboxSplitPeriod/" + startDate;
  }

  public boolean isOpen() {
    return endDate == null;
  }

  public void finalizePeriod(Instant endDate, int splitPercentageToOwner, BigDecimal cashTotal,
      BigDecimal cardTotal, BigDecimal mobileTotal, BigDecimal totalEarned,
      BigDecimal amountDueOwner, BigDecimal amountDueOperator) {

    this.endDate = endDate;
    this.splitPercentageToOwner = splitPercentageToOwner;
    this.cashTotal = cashTotal;
    this.cardTotal = cardTotal;
    this.mobileTotal = mobileTotal;
    this.totalEarned = totalEarned;
    this.amountDueOwner = amountDueOwner;
    this.amountDueOperator = amountDueOperator;
  }

  public Instant getStartDate() {
    return startDate;
  }

  public Instant getEndDate() {
    return endDate;
  }

  public Integer getSplitPercentageToOwner() {
    return splitPercentageToOwner;
  }

  public BigDecimal getCashTotal() {
    return cashTotal;
  }

  public BigDecimal getCardTotal() {
    return cardTotal;
  }

  public BigDecimal getMobileTotal() {
    return mobileTotal;
  }

  public BigDecimal getTotalEarned() {
    return totalEarned;
  }

  public BigDecimal getAmountDueOwner() {
    return amountDueOwner;
  }

  public BigDecimal getAmountDueOperator() {
    return amountDueOperator;
  }
}
