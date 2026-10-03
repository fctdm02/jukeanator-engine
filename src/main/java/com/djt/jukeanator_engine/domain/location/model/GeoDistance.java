package com.djt.jukeanator_engine.domain.location.model;

/** Great-circle distance between two latitude/longitude points. */
public final class GeoDistance {

  /** Mean Earth radius, in meters (IUGG). */
  public static final double EARTH_RADIUS_METERS = 6_371_008.8;

  private GeoDistance() {
  }

  /**
   * Haversine distance, which is accurate to well under a meter at the distances geo-fencing
   * deals with.
   *
   * @return the distance between the two points, in meters
   */
  public static double metersBetween(double latitude1, double longitude1, double latitude2,
      double longitude2) {

    double phi1 = Math.toRadians(latitude1);
    double phi2 = Math.toRadians(latitude2);
    double deltaPhi = Math.toRadians(latitude2 - latitude1);
    double deltaLambda = Math.toRadians(longitude2 - longitude1);

    double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
        + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
    return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
