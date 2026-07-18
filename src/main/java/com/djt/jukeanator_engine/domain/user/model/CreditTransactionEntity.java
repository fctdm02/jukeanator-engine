package com.djt.jukeanator_engine.domain.user.model;

import java.time.Instant;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * One append-only credit spend/purchase record. {@code locationId} is {@code null} for
 * standalone-mode (non-location-attributed) spends, and for the pre-multi-tenant call sites that
 * don't yet have a location to tag — never retroactively backfilled.
 *
 * @author tmyers
 */
public class CreditTransactionEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  private String userEmail;
  private String locationId;
  private int amount; // negative for spend, positive for purchase
  private CreditTransactionType type;
  private Instant timestamp;
  private Integer songAlbumId;
  private Integer songId;
  private int resultingBalance;

  public CreditTransactionEntity() {}

  public CreditTransactionEntity(Integer persistentIdentity, String userEmail, String locationId,
      int amount, CreditTransactionType type, Instant timestamp, Integer songAlbumId,
      Integer songId, int resultingBalance) {
    super(persistentIdentity);
    this.userEmail = userEmail;
    this.locationId = locationId;
    this.amount = amount;
    this.type = type;
    this.timestamp = timestamp;
    this.songAlbumId = songAlbumId;
    this.songId = songId;
    this.resultingBalance = resultingBalance;
  }

  @Override
  public String getNaturalIdentity() {
    return userEmail + "/" + timestamp + "/" + getPersistentIdentity();
  }

  public String getUserEmail() {
    return userEmail;
  }

  public String getLocationId() {
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
