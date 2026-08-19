package com.djt.jukeanator_engine.domain.location.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Plain, human-readable JSON representation of a {@code LocationEntity}, nested under the
 * singleton {@link LocationRootDto}.
 */
public final class LocationDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private Integer persistentIdentity;
  private String name;
  private Double latitude;
  private Double longitude;
  private String apiKeyHash;
  private String status;
  private Instant lastSeenAt;
  private Instant libraryLastSyncedAt;

  public LocationDto() {}

  public LocationDto(Integer persistentIdentity, String name,
      Double latitude, Double longitude, String apiKeyHash, String status, Instant lastSeenAt,
      Instant libraryLastSyncedAt) {

    this.persistentIdentity = persistentIdentity;
    this.name = name;
    this.latitude = latitude;
    this.longitude = longitude;
    this.apiKeyHash = apiKeyHash;
    this.status = status;
    this.lastSeenAt = lastSeenAt;
    this.libraryLastSyncedAt = libraryLastSyncedAt;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }

  public void setPersistentIdentity(Integer persistentIdentity) {
    this.persistentIdentity = persistentIdentity;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public String getApiKeyHash() {
    return apiKeyHash;
  }

  public void setApiKeyHash(String apiKeyHash) {
    this.apiKeyHash = apiKeyHash;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(Instant lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public Instant getLibraryLastSyncedAt() {
    return libraryLastSyncedAt;
  }

  public void setLibraryLastSyncedAt(Instant libraryLastSyncedAt) {
    this.libraryLastSyncedAt = libraryLastSyncedAt;
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
    LocationDto other = (LocationDto) obj;
    return Objects.equals(persistentIdentity, other.persistentIdentity);
  }

  @Override
  public String toString() {
    return "LocationDto [persistentIdentity=" + persistentIdentity
        + ", name=" + name + ", status=" + status + "]";
  }
}
