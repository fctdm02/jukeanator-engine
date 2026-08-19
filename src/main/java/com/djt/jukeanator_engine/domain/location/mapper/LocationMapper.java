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
        entity.getLatitude(),
        entity.getLongitude(),
        entity.getApiKeyHash(),
        entity.getStatus().name(),
        entity.getLastSeenAt(),
        entity.getLibraryLastSyncedAt());
  }

  public static LocationRootEntity toEntity(LocationRootDto dto) {

    LocationRootEntity root = new LocationRootEntity();

    for (LocationDto locationDto : dto.getLocations()) {
      root.addLocation(toEntity(locationDto));
    }

    return root;
  }

  public static LocationEntity toEntity(LocationDto dto) {

    LocationEntity location = new LocationEntity(
        dto.getPersistentIdentity(),
        dto.getName(),
        dto.getLatitude(),
        dto.getLongitude(),
        dto.getApiKeyHash());

    location.setStatus(LocationStatus.valueOf(dto.getStatus()));
    location.setLastSeenAt(dto.getLastSeenAt());
    location.setLibraryLastSyncedAt(dto.getLibraryLastSyncedAt());

    return location;
  }
}
