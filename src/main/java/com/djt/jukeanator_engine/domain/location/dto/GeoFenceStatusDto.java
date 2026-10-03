package com.djt.jukeanator_engine.domain.location.dto;

/**
 * Tells the Web/Mobile UI whether it must send its device position with queue operations at a
 * location, and the limits that position will be checked against.
 *
 * @param enforced true if queue operations at this location require a position inside the fence
 * @param radiusMeters how far from the location's coordinates a patron may be
 * @param maxAccuracyMeters the largest device accuracy radius that is accepted
 * @param allowSimulatedPosition true if a hand-entered position is accepted (QA only)
 * @param latitude the location's latitude, or null if the location is unknown
 * @param longitude the location's longitude, or null if the location is unknown
 */
public record GeoFenceStatusDto(boolean enforced, double radiusMeters, double maxAccuracyMeters,
    boolean allowSimulatedPosition, Double latitude, Double longitude) {
}
