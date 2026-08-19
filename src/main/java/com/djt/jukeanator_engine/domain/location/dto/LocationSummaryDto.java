package com.djt.jukeanator_engine.domain.location.dto;

/** Public-facing location picker entry for the web/mobile app. */
public record LocationSummaryDto(Integer locationId, String name, Double latitude,
    Double longitude, boolean online) {
}
