package com.djt.jukeanator_engine.domain.location.controller;

import static java.util.Objects.requireNonNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.location.dto.GeoFenceStatusDto;
import com.djt.jukeanator_engine.domain.location.service.GeoFenceService;

/**
 * Lets the Web/Mobile UI find out whether it must send its device position with queue operations
 * at a location. Available in every {@code app.mode}; outside master it always reports the fence
 * as not enforced.
 */
@RestController
public class GeoFenceController {

  private final GeoFenceService geoFenceService;

  public GeoFenceController(GeoFenceService geoFenceService) {

    requireNonNull(geoFenceService, "geoFenceService cannot be null");
    this.geoFenceService = geoFenceService;
  }

  @GetMapping("/api/locations/{locationId}/geo-fence")
  public GeoFenceStatusDto getGeoFenceStatus(@PathVariable Integer locationId) {

    return geoFenceService.getStatus(locationId);
  }
}
