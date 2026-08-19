package com.djt.jukeanator_engine.domain.user.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * One append-only credit spend/purchase record, owned by the {@link UserEntity} it belongs to.
 * {@code locationId} is {@code null} for standalone-mode (non-location-attributed) spends, and
 * for the pre-multi-tenant call sites that don't yet have a location to tag — never
 * retroactively backfilled.
 *
 * @author tmyers
 */
@Entity
@Table(name = "credit_transactions")
public class CreditTransactionEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  // Persistence-only back-reference -- the FK column JPA needs to own the UserEntity <->
  // transaction relationship. UserEntity.addTransaction() is the single place that keeps this
  // back-reference in sync (see setUser()).
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id")
  private UserEntity user;

  @Column(name = "location_id")
  private Integer locationId;

  @Column(nullable = false)
  private int amount; // negative for spend, positive for purchase

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CreditTransactionType type;

  @Column(nullable = false)
  private Instant timestamp;

  @Column(name = "song_album_id")
  private Integer songAlbumId;

  @Column(name = "song_id")
  private Integer songId;

  @Column(name = "resulting_balance", nullable = false)
  private int resultingBalance;

  protected CreditTransactionEntity() {} // for JPA

  public CreditTransactionEntity(Integer persistentIdentity, Integer locationId, int amount,
      CreditTransactionType type, Instant timestamp, Integer songAlbumId, Integer songId,
      int resultingBalance) {
    super(persistentIdentity);
    this.locationId = locationId;
    this.amount = amount;
    this.type = type;
    this.timestamp = timestamp;
    this.songAlbumId = songAlbumId;
    this.songId = songId;
    this.resultingBalance = resultingBalance;
  }

  void setUser(UserEntity user) {
    this.user = user;
  }

  @Override
  public String getNaturalIdentity() {
    return getUserEmail() + "/" + timestamp + "/" + getPersistentIdentity();
  }

  public String getUserEmail() {
    return user != null ? user.getEmailAddress() : null;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public int getAmount() {
    return amount;
  }

  public CreditTransactionType getType() {
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

  public int getResultingBalance() {
    return resultingBalance;
  }
}
