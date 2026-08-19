package com.djt.jukeanator_engine.domain.songlibrary.dto;

public class LocationMetadataDto {

  private final Integer locationId;
  private final String locationName;
  private final String logoName;
  private final Double latitude;
  private final Double longitude;
  private final boolean isGeoFenced;

  public LocationMetadataDto(
      Integer locationId,
      String locationName,
      String logoName,
      Double latitude,
      Double longitude,
      boolean isGeoFenced) {
    super();
    this.locationId = locationId;
    this.locationName = locationName;
    this.logoName = logoName;
    this.latitude = latitude;
    this.longitude = longitude;
    this.isGeoFenced = isGeoFenced;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public String getLocationName() {
    return locationName;
  }

  public String getLogoName() {
    return logoName;
  }

  public Double getLatitude() {
    return latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public boolean isGeoFenced() {
    return isGeoFenced;
  }

  public boolean isEmpty() {

    if (this.locationId == null || this.locationName.isBlank()) {
      return true;
    }

    return false;
  }
}
