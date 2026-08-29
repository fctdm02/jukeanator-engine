package com.djt.jukeanator_engine.domain.location.dto;

public record UpdateLocationInfoRequest(String name, Double latitude, Double longitude,
    String logoName, boolean isGeoFenced) {
}
