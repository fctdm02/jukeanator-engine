package com.djt.jukeanator_engine.domain.financialledger.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * Common shape shared by {@link LocalCashTransactionEntity} and {@link
 * LocalCreditTransactionEntity} -- both are simple, append-only local (bill-acceptor /
 * credit-card-reader) credit-award records tied to a physical location, differing only in which
 * hardware produced them. Both persist to one shared table ({@code location_transaction}),
 * discriminated by {@code transaction_type}, so a central DBA querying the master database can see
 * both revenue streams together and filter by location.
 *
 * <p>{@code sourceTransactionId} is the idempotency key for slave-to-master mirroring (see
 * {@code FinancialLedgerSyncService}/{@code FinancialLedgerSyncController}): {@code null} for
 * every transaction recorded directly on the instance that owns it (the normal case on a
 * slave/standalone instance); only master-received mirrored rows ever populate it, carrying the
 * slave's own local {@code persistentIdentity} for that transaction so a retried mirror push is a
 * safe no-op rather than a duplicate row.
 *
 * <p>{@code syncedToMasterAt} is the slave-side outbox marker: {@code null} until master has
 * acknowledged the mirror push, so {@code FinancialLedgerSyncService} keeps retrying every
 * not-yet-acknowledged transaction until it lands. Always {@code null} on master's mirrored copy.
 */
@Entity
@Table(name = "location_transaction")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "transaction_type")
public abstract class AbstractLocationTransactionEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(name = "amount_dollars", nullable = false)
  private int amountDollars;

  @Column(nullable = false)
  private Instant timestamp;

  @Column(name = "location_id")
  private Integer locationId;

  @Column(name = "source_transaction_id")
  private Integer sourceTransactionId;

  @Column(name = "synced_to_master_at")
  private Instant syncedToMasterAt;

  protected AbstractLocationTransactionEntity() {} // for JPA

  protected AbstractLocationTransactionEntity(Integer persistentIdentity, int amountDollars,
      Instant timestamp, Integer locationId, Integer sourceTransactionId) {
    super(persistentIdentity);
    this.amountDollars = amountDollars;
    this.timestamp = timestamp;
    this.locationId = locationId;
    this.sourceTransactionId = sourceTransactionId;
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

  public Integer getSourceTransactionId() {
    return sourceTransactionId;
  }

  public Instant getSyncedToMasterAt() {
    return syncedToMasterAt;
  }

  /**
   * True for a transaction this instance recorded itself (not a master-received mirrored copy)
   * that master has not yet acknowledged.
   */
  public boolean isPendingMasterSync() {
    return sourceTransactionId == null && syncedToMasterAt == null;
  }

  public void markSyncedToMaster(Instant syncedToMasterAt) {
    this.syncedToMasterAt = syncedToMasterAt;
  }

  // Package-private: only FinancialLedgerRootEntity.changeLocationId re-keys a transaction, when
  // this instance's own location id is corrected post-handshake.
  void changeLocationId(Integer locationId) {
    this.locationId = locationId;
  }
}
