package com.djt.jukeanator_engine.domain.location.controller;

import com.djt.jukeanator_engine.domain.location.model.GeoPosition;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The HTTP headers a Web/Mobile UI client uses to send its device position with a queue
 * operation. Headers (rather than request-body fields) keep every queue request DTO unchanged,
 * and the same contract carries over to a native mobile app.
 */
public final class GeoPositionHeaders {

  public static final String LATITUDE = "X-Geo-Latitude";
  public static final String LONGITUDE = "X-Geo-Longitude";
  public static final String ACCURACY = "X-Geo-Accuracy";
  public static final String TIMESTAMP = "X-Geo-Timestamp";
  public static final String SIMULATED = "X-Geo-Simulated";

  private GeoPositionHeaders() {
  }

  /**
   * @return the position carried by the request's headers, or null if any required header is
   *         missing or malformed
   */
  public static GeoPosition fromRequest(HttpServletRequest request) {

    if (request == null) {
      return null;
    }
    try {
      String latitude = request.getHeader(LATITUDE);
      String longitude = request.getHeader(LONGITUDE);
      String accuracy = request.getHeader(ACCURACY);
      String timestamp = request.getHeader(TIMESTAMP);
      if (latitude == null || longitude == null || accuracy == null || timestamp == null) {
        return null;
      }
      double lat = Double.parseDouble(latitude.trim());
      double lon = Double.parseDouble(longitude.trim());
      double acc = Double.parseDouble(accuracy.trim());
      if (!Double.isFinite(lat) || !Double.isFinite(lon) || !Double.isFinite(acc)
          || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
        return null;
      }
      return new GeoPosition(lat, lon, acc, Long.parseLong(timestamp.trim()),
          Boolean.parseBoolean(request.getHeader(SIMULATED)));
    } catch (NumberFormatException nfe) {
      return null;
    }
  }
}
