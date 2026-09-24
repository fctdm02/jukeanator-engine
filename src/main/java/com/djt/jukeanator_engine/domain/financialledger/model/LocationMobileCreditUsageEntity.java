package com.djt.jukeanator_engine.domain.financialledger.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;

/**
 * Slave-only, append-only copy of one mobile/web song-credit spend master recorded against this
 * slave's location (master's authoritative copy is {@code UserSongCreditUsageEntity}, which a
 * slave never records for mobile/web users -- user accounts and credits are master-owned). Keyed
 * by {@code userEmail} rather than a user reference, since the user account itself only exists on
 * master. {@code sourceSyncId} is master's {@code UserSongCreditUsageEntity.syncId} -- the
 * master-to-slave mirror's idempotency key. {@code FinancialLedgerServiceImpl} sums these into a
 * slave's jukebox-split mobile total.
 */
@Entity
@Table(name = "location_mobile_credit_usage")
public class LocationMobileCreditUsageEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(name = "location_id")
  private Integer locationId;

  @Column(name = "source_sync_id", nullable = false, length = 36)
  private String sourceSyncId;

  @Column(name = "user_email", nullable = false)
  private String userEmail;

  @Column(nullable = false)
  private int amount; // always negative -- a spend, same sign as UserSongCreditUsageEntity

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserSongCreditUsageType type;

  @Column(nullable = false)
  private Instant timestamp;

  @Column(name = "song_album_id")
  private Integer songAlbumId;

  @Column(name = "song_id")
  private Integer songId;

  protected LocationMobileCreditUsageEntity() {} // for JPA

  public LocationMobileCreditUsageEntity(Integer persistentIdentity, Integer locationId,
      String sourceSyncId, String userEmail, int amount, UserSongCreditUsageType type,
      Instant timestamp, Integer songAlbumId, Integer songId) {
    super(persistentIdentity);
    this.locationId = locationId;
    this.sourceSyncId = sourceSyncId;
    this.userEmail = userEmail;
    this.amount = amount;
    this.type = type;
    this.timestamp = timestamp;
    this.songAlbumId = songAlbumId;
    this.songId = songId;
  }

  @Override
  public String getNaturalIdentity() {
    return "LocationMobileCreditUsage/" + sourceSyncId;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public String getSourceSyncId() {
    return sourceSyncId;
  }

  public String getUserEmail() {
    return userEmail;
  }

  public int getAmount() {
    return amount;
  }

  public UserSongCreditUsageType getType() {
    return type;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public Integer getSongAlbumId() {
    return songAlbumId;
  }

  public Integer getSongId() {
    return songId;
  }

  // Package-private: only FinancialLedgerRootEntity.changeLocationId re-keys a usage, when this
  // instance's own location id is corrected post-handshake.
  void changeLocationId(Integer locationId) {
    this.locationId = locationId;
  }
}
