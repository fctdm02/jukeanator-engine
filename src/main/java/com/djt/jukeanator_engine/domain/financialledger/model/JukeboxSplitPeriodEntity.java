package com.djt.jukeanator_engine.domain.financialledger.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;

/**
 * One jukebox-split reconciliation period between the operator and a location owner. The period
 * with a {@code null} {@link #endDate} is the current/open one -- its sub-totals are always
 * computed live by {@code FinancialLedgerService}, never read from this entity, since they are
 * {@code null} here until {@link #finalizePeriod} locks them in.
 *
 * <p>Once finalized, a slave's period is mirrored up to master (see {@code
 * FinancialLedgerSyncService}). {@code sourcePeriodId} is that mirror's idempotency key -- {@code
 * null} on the slave's own periods, the slave's own {@code persistentIdentity} on master's mirrored
 * copy -- and {@code syncedToMasterAt} is the slave-side outbox marker, {@code null} until master
 * has acknowledged the push.
 */
@Entity
@Table(name = "location_jukebox_split")
public class JukeboxSplitPeriodEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  // Not a mapped column and not written by the filesystem repository -- reconstructed uniformly by
  // FinancialLedgerServiceImpl right after any repository load, regardless of backend, exactly like
  // RootFolderEntity's parentLocation. Only ever set on the owning instance's own periods; master's
  // mirrored periods carry just parentLocationId below.
  private transient LocationEntity parentLocation;

  // Read-only view of parent_location_id: FinancialLedgerRepositoryJpaImpl writes that column
  // itself on insert (from getLocationId()), and LocationRepositoryJpaImpl re-keys it, so JPA
  // must never write it back from here.
  @Column(name = "parent_location_id", insertable = false, updatable = false)
  private Integer parentLocationId;

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

  @Column(name = "source_period_id")
  private Integer sourcePeriodId;

  @Column(name = "synced_to_master_at")
  private Instant syncedToMasterAt;

  protected JukeboxSplitPeriodEntity() {} // for JPA

  public JukeboxSplitPeriodEntity(Integer persistentIdentity, Instant startDate) {
    super(persistentIdentity);
    this.startDate = startDate;
  }

  /** Full reconstruction constructor, used only when loading a persisted period back. */
  public JukeboxSplitPeriodEntity(Integer persistentIdentity, Instant startDate, Instant endDate,
      Integer splitPercentageToOwner, BigDecimal cashTotal, BigDecimal cardTotal,
      BigDecimal mobileTotal, BigDecimal totalEarned, BigDecimal amountDueOwner,
      BigDecimal amountDueOperator, Integer parentLocationId, Integer sourcePeriodId,
      Instant syncedToMasterAt) {

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
    this.parentLocationId = parentLocationId;
    this.sourcePeriodId = sourcePeriodId;
    this.syncedToMasterAt = syncedToMasterAt;
  }

  /** Master-side copy of a slave's finalized period, received via the slave-to-master mirror. */
  public static JukeboxSplitPeriodEntity mirroredFrom(Integer persistentIdentity,
      Integer locationId, Integer sourcePeriodId, Instant startDate, Instant endDate,
      Integer splitPercentageToOwner, BigDecimal cashTotal, BigDecimal cardTotal,
      BigDecimal mobileTotal, BigDecimal totalEarned, BigDecimal amountDueOwner,
      BigDecimal amountDueOperator) {

    return new JukeboxSplitPeriodEntity(persistentIdentity, startDate, endDate,
        splitPercentageToOwner, cashTotal, cardTotal, mobileTotal, totalEarned, amountDueOwner,
        amountDueOperator, locationId, sourcePeriodId, null);
  }

  @Override
  public String getNaturalIdentity() {
    return "JukeboxSplitPeriod/" + startDate;
  }

  public LocationEntity getParentLocation() {
    return parentLocation;
  }

  public void setParentLocation(LocationEntity parentLocation) {
    this.parentLocation = parentLocation;
    if (parentLocation != null) {
      this.parentLocationId = parentLocation.getPersistentIdentity();
    }
  }

  /**
   * The owning location's id -- read live from {@link #parentLocation} when set (it is re-keyed in
   * place when this instance's own location id is corrected), otherwise the persisted value.
   */
  public Integer getLocationId() {
    return parentLocation != null ? parentLocation.getPersistentIdentity() : parentLocationId;
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

  public Integer getSourcePeriodId() {
    return sourcePeriodId;
  }

  public Instant getSyncedToMasterAt() {
    return syncedToMasterAt;
  }

  /**
   * True for a finalized period this instance recorded itself (not a master-received mirrored
   * copy) that master has not yet acknowledged. The open period is never mirrored -- its totals
   * don't exist until it is finalized.
   */
  public boolean isPendingMasterSync() {
    return !isOpen() && sourcePeriodId == null && syncedToMasterAt == null;
  }

  public void markSyncedToMaster(Instant syncedToMasterAt) {
    this.syncedToMasterAt = syncedToMasterAt;
  }
}
