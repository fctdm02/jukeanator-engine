package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import com.djt.jukeanator_engine.domain.user.model.CreditTransactionType;

/**
 * Plain, human-readable JSON representation of a {@code CreditTransactionEntity}, nested under
 * its owning {@link UserDto} (unlike {@link CreditTransactionDto}, which is the API-facing shape
 * for the credit ledger endpoint and carries the owning user's email instead).
 */
public class CreditTransactionEntryDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private Integer persistentIdentity;
  private String locationId;
  private int amount;
  private CreditTransactionType type;
  private Instant timestamp;
  private Integer songAlbumId;
  private Integer songId;
  private int resultingBalance;

  public CreditTransactionEntryDto() {}

  public CreditTransactionEntryDto(Integer persistentIdentity, String locationId, int amount,
      CreditTransactionType type, Instant timestamp, Integer songAlbumId, Integer songId,
      int resultingBalance) {
    this.persistentIdentity = persistentIdentity;
    this.locationId = locationId;
    this.amount = amount;
    this.type = type;
    this.timestamp = timestamp;
    this.songAlbumId = songAlbumId;
    this.songId = songId;
    this.resultingBalance = resultingBalance;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }

  public void setPersistentIdentity(Integer persistentIdentity) {
    this.persistentIdentity = persistentIdentity;
  }

  public String getLocationId() {
    return locationId;
  }

  public void setLocationId(String locationId) {
    this.locationId = locationId;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public CreditTransactionType getType() {
    return type;
  }

  public void setType(CreditTransactionType type) {
    this.type = type;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public Integer getSongAlbumId() {
    return songAlbumId;
  }

  public void setSongAlbumId(Integer songAlbumId) {
    this.songAlbumId = songAlbumId;
  }

  public Integer getSongId() {
    return songId;
  }

  public void setSongId(Integer songId) {
    this.songId = songId;
  }

  public int getResultingBalance() {
    return resultingBalance;
  }

  public void setResultingBalance(int resultingBalance) {
    this.resultingBalance = resultingBalance;
  }

  @Override
  public int hashCode() {
    return Objects.hash(persistentIdentity);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    CreditTransactionEntryDto other = (CreditTransactionEntryDto) obj;
    return Objects.equals(persistentIdentity, other.persistentIdentity);
  }

  @Override
  public String toString() {
    return "CreditTransactionEntryDto [persistentIdentity=" + persistentIdentity
        + ", locationId=" + locationId + ", amount=" + amount + ", type=" + type + ", timestamp="
        + timestamp + ", songAlbumId=" + songAlbumId + ", songId=" + songId
        + ", resultingBalance=" + resultingBalance + "]";
  }
}
