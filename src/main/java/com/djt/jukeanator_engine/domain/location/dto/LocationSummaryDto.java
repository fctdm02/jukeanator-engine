package com.djt.jukeanator_engine.domain.location.dto;

/**
 * Public-facing location picker entry for the web/mobile app. {@code privateLocation} is true for
 * a location listed only for the accounts named in {@code location.private-locations}; the web UI
 * never picks such a location by default.
 */
public record LocationSummaryDto(Integer locationId, String name, String logoName,
    Double latitude, Double longitude, boolean online, boolean privateLocation) {

  /** A public location entry. */
  public LocationSummaryDto(Integer locationId, String name, String logoName, Double latitude,
      Double longitude, boolean online) {
    this(locationId, name, logoName, latitude, longitude, online, false);
  }

  /** This entry with {@code privateLocation} set as given. */
  public LocationSummaryDto withPrivateLocation(boolean privateLocation) {
    return new LocationSummaryDto(locationId, name, logoName, latitude, longitude, online,
        privateLocation);
  }
}
