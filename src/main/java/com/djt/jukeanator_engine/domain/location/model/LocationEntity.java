package com.djt.jukeanator_engine.domain.location.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * A physical jukebox ("slave") location known to the master instance.
 *
 * @author tmyers
 */
@Entity
@Table(name = "locations")
public class LocationEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(nullable = false)
  private String name;

  private Double latitude;
  private Double longitude;

  // bcrypt hash of the location's API secret; plaintext is never stored
  @Column(name = "api_key_hash", nullable = false)
  private String apiKeyHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LocationStatus status = LocationStatus.PENDING;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Column(name = "library_last_synced_at")
  private Instant libraryLastSyncedAt;

  public LocationEntity() {}

  public LocationEntity(Integer persistentIdentity, String name, Double latitude,
      Double longitude, String apiKeyHash) {
    super(persistentIdentity);
    this.name = name;
    this.latitude = latitude;
    this.longitude = longitude;
    this.apiKeyHash = apiKeyHash;
  }

  @Override
  public String getNaturalIdentity() {
    return String.valueOf(getPersistentIdentity());
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

  public LocationStatus getStatus() {
    return status;
  }

  public void setStatus(LocationStatus status) {
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
}
