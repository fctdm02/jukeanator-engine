package com.djt.jukeanator_engine.domain.location.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class GeoDistanceTest {

  private static final double LATITUDE = 42.3314;
  private static final double LONGITUDE = -83.0458;

  /** One degree of latitude, in meters, on a sphere of GeoDistance.EARTH_RADIUS_METERS. */
  private static final double METERS_PER_DEGREE_LATITUDE =
      Math.PI * GeoDistance.EARTH_RADIUS_METERS / 180.0;

  @Test
  void samePoint_isZero() {
    assertEquals(0.0, GeoDistance.metersBetween(LATITUDE, LONGITUDE, LATITUDE, LONGITUDE), 1e-9);
  }

  @Test
  void tenThousandthOfADegreeOfLatitude_isAboutElevenMeters() {
    assertEquals(11.12,
        GeoDistance.metersBetween(LATITUDE, LONGITUDE, LATITUDE + 0.0001, LONGITUDE), 0.01);
  }

  @Test
  void pointsEighteenAndNineteenMetersNorth_measureAsSuch() {
    double eighteenMeters = 18.0 / METERS_PER_DEGREE_LATITUDE;
    double nineteenMeters = 19.0 / METERS_PER_DEGREE_LATITUDE;

    assertEquals(18.0,
        GeoDistance.metersBetween(LATITUDE, LONGITUDE, LATITUDE + eighteenMeters, LONGITUDE), 0.01);
    assertEquals(19.0,
        GeoDistance.metersBetween(LATITUDE, LONGITUDE, LATITUDE + nineteenMeters, LONGITUDE), 0.01);
  }

  @Test
  void eastWestDistance_shrinksWithLatitude() {
    // At 42.33 degrees north, a degree of longitude is cos(42.33) of a degree of latitude.
    double expected = 0.001 * METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(LATITUDE));

    assertEquals(expected,
        GeoDistance.metersBetween(LATITUDE, LONGITUDE, LATITUDE, LONGITUDE + 0.001), 0.01);
  }

  @Test
  void isSymmetric() {
    assertEquals(GeoDistance.metersBetween(LATITUDE, LONGITUDE, 42.3400, -83.0500),
        GeoDistance.metersBetween(42.3400, -83.0500, LATITUDE, LONGITUDE), 1e-9);
  }
}
