package com.djt.jukeanator_engine.domain.location.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Plain, human-readable JSON representation of the singleton {@code LocationRootEntity}. This is
 * the top-level shape written to and read from {@code LocationRootEntity.LOCATION_LIST_FILENAME}.
 */
public record LocationRootDto(List<LocationDto> locations) implements Serializable {
}
