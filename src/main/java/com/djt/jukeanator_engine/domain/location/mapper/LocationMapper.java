package com.djt.jukeanator_engine.domain.location.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.location.dto.LocationDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationRootDto;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationStatus;

/**
 * @author tmyers
 */
public final class LocationMapper {

  private LocationMapper() {}

  public static LocationRootDto toDto(LocationRootEntity root) {

    List<LocationDto> dtos = new ArrayList<>();

    for (LocationEntity location : root.getLocations()) {
      dtos.add(toDto(location));
    }

    return new LocationRootDto(dtos);
  }

  public static LocationDto toDto(LocationEntity entity) {

    return new LocationDto(
        entity.getPersistentIdentity(),
        entity.getName(),
        entity.getLogoName(),
        entity.getLatitude(),
        entity.getLongitude(),
        entity.getApiKeyHash(),
        entity.getStatus().name(),
        entity.getLastSeenAt(),
        entity.getLibraryLastSyncedAt());
  }

  public static LocationRootEntity toEntity(LocationRootDto dto) {

    LocationRootEntity root = new LocationRootEntity();

    for (LocationDto locationDto : dto.locations()) {
      root.addLocation(toEntity(locationDto));
    }

    return root;
  }

  public static LocationEntity toEntity(LocationDto dto) {

    LocationEntity location = new LocationEntity(
        dto.persistentIdentity(),
        dto.name(),
        dto.latitude(),
        dto.longitude(),
        dto.apiKeyHash());

    location.setLogoName(dto.logoName());
    location.setStatus(LocationStatus.valueOf(dto.status()));
    location.setLastSeenAt(dto.lastSeenAt());
    location.setLibraryLastSyncedAt(dto.libraryLastSyncedAt());

    return location;
  }
}
