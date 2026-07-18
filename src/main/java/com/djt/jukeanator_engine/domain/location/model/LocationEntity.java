package com.djt.jukeanator_engine.domain.location.model;

import java.time.Instant;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * A physical jukebox ("slave") location known to the master instance.
 *
 * @author tmyers
 */
public class LocationEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  private String locationId; // natural identity, UUID generated at provisioning
  private String name;
  private Double latitude;
  private Double longitude;
  private String apiKeyHash; // bcrypt hash of the location's API secret; plaintext is never stored
  private LocationStatus status = LocationStatus.PENDING;
  private Instant lastSeenAt;
  private Instant libraryLastSyncedAt;

  public LocationEntity() {}

  public LocationEntity(Integer persistentIdentity, String locationId, String name,
      Double latitude, Double longitude, String apiKeyHash) {
    super(persistentIdentity);
    this.locationId = locationId;
    this.name = name;
    this.latitude = latitude;
    this.longitude = longitude;
    this.apiKeyHash = apiKeyHash;
  }

  @Override
  public String getNaturalIdentity() {
    return locationId;
  }

  public String getLocationId() {
    return locationId;
  }

  public void setLocationId(String locationId) {
    this.locationId = locationId;
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
