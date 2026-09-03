package com.djt.jukeanator_engine.domain.location.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Plain, human-readable JSON representation of a {@code LocationEntity}, nested under the
 * singleton {@link LocationRootDto}.
 */
public record LocationDto(Integer persistentIdentity, String name, String logoName,
    Double latitude, Double longitude, String apiKeyHash, String status, Instant lastSeenAt,
    Instant libraryLastSyncedAt) implements Serializable {

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
  public int hashCode() {
    return Objects.hash(persistentIdentity);
  }

  @Override
  public String toString() {
    return "LocationDto [persistentIdentity=" + persistentIdentity
        + ", name=" + name + ", status=" + status + "]";
  }
}
