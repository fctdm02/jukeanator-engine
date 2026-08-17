package com.djt.jukeanator_engine.domain.location.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain, human-readable JSON representation of the singleton {@code LocationRootEntity}. This is
 * the top-level shape written to and read from {@code LocationRootEntity.LOCATION_LIST_FILENAME}.
 */
public class LocationRootDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private List<LocationDto> locations = new ArrayList<>();

  public LocationRootDto() {}

  public LocationRootDto(List<LocationDto> locations) {
    this.locations = locations;
  }

  public List<LocationDto> getLocations() {
    return locations;
  }

  public void setLocations(List<LocationDto> locations) {
    this.locations = locations;
  }
}
