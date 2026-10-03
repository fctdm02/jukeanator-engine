package com.djt.jukeanator_engine.domain.location.model;

/**
 * A device position reported by a Web/Mobile UI client, as returned by the browser's Geolocation
 * API (or entered by hand when {@code app.geo-fence.allow-simulated-position} is set for QA).
 *
 * @param latitude in decimal degrees
 * @param longitude in decimal degrees
 * @param accuracyMeters the radius of uncertainty the device reported for this reading
 * @param timestampMillis when the reading was taken, in epoch millis (UTC)
 * @param simulated true if the position was entered by hand rather than read from the device
 */
public record GeoPosition(double latitude, double longitude, double accuracyMeters,
    long timestampMillis, boolean simulated) {
}
