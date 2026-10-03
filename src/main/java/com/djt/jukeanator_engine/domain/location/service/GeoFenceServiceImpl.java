package com.djt.jukeanator_engine.domain.location.service;

import static java.util.Objects.requireNonNull;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.location.config.GeoFenceProperties;
import com.djt.jukeanator_engine.domain.location.dto.GeoFenceStatusDto;
import com.djt.jukeanator_engine.domain.location.exception.GeoFenceViolationException;
import com.djt.jukeanator_engine.domain.location.exception.GeoFenceViolationReason;
import com.djt.jukeanator_engine.domain.location.model.GeoDistance;
import com.djt.jukeanator_engine.domain.location.model.GeoPosition;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;

/**
 * Checks Web/Mobile UI device positions against the location data master holds for each location
 * (kept current by the slave's {@code /location-info} push -- see {@code SlaveConnectionManager}).
 *
 * <p>The device's reported accuracy counts in the patron's favor: a reading passes if the nearest
 * point of its accuracy circle is within {@code app.geo-fence.radius-meters} of the location, so
 * an imprecise indoor reading of a patron who is actually there is not rejected. Readings whose
 * accuracy radius exceeds {@code app.geo-fence.max-accuracy-meters} are rejected outright, since
 * they would otherwise let almost anyone through.
 */
public class GeoFenceServiceImpl implements GeoFenceService {

  private static final Logger log = LoggerFactory.getLogger(GeoFenceServiceImpl.class);

  /** Device clocks drift; a reading this far in the future is still treated as current. */
  static final long MAX_CLOCK_SKEW_MILLIS = 30_000L;

  private final GeoFenceProperties geoFenceProperties;
  private final AppProperties appProperties;
  private final LocationService locationService;
  private final Clock clock;

  public GeoFenceServiceImpl(GeoFenceProperties geoFenceProperties, AppProperties appProperties,
      LocationService locationService) {
    this(geoFenceProperties, appProperties, locationService, Clock.systemUTC());
  }

  public GeoFenceServiceImpl(GeoFenceProperties geoFenceProperties, AppProperties appProperties,
      LocationService locationService, Clock clock) {

    requireNonNull(geoFenceProperties, "geoFenceProperties cannot be null");
    requireNonNull(appProperties, "appProperties cannot be null");
    requireNonNull(locationService, "locationService cannot be null");
    requireNonNull(clock, "clock cannot be null");
    this.geoFenceProperties = geoFenceProperties;
    this.appProperties = appProperties;
    this.locationService = locationService;
    this.clock = clock;
  }

  @Override
  public boolean isEnforced(Integer locationId) {

    return enforcedLocation(locationId) != null;
  }

  @Override
  public GeoFenceStatusDto getStatus(Integer locationId) {

    LocationEntity location =
        locationId != null ? locationService.getLocationByIdNullIfNotExists(locationId) : null;
    return new GeoFenceStatusDto(isEnforced(locationId), geoFenceProperties.getRadiusMeters(),
        geoFenceProperties.getMaxAccuracyMeters(), geoFenceProperties.isAllowSimulatedPosition(),
        location != null ? location.getLatitude() : null,
        location != null ? location.getLongitude() : null);
  }

  @Override
  public void verifyWithinFence(Integer locationId, GeoPosition position) {

    LocationEntity location = enforcedLocation(locationId);
    if (location == null) {
      return;
    }

    String locationName = location.getName() != null && !location.getName().isBlank()
        ? location.getName()
        : "this location";

    if (position == null) {
      throw reject(locationId, GeoFenceViolationReason.POSITION_REQUIRED,
          "Your device location is required to queue songs at " + locationName
              + ". Please allow location access and try again.");
    }

    if (position.simulated() && !geoFenceProperties.isAllowSimulatedPosition()) {
      throw reject(locationId, GeoFenceViolationReason.SIMULATED_POSITION_NOT_ALLOWED,
          "A simulated location cannot be used to queue songs at " + locationName + ".");
    }

    long ageMillis = clock.millis() - position.timestampMillis();
    if (ageMillis > geoFenceProperties.getMaxPositionAgeSeconds() * 1000L
        || ageMillis < -MAX_CLOCK_SKEW_MILLIS) {
      throw reject(locationId, GeoFenceViolationReason.POSITION_TOO_OLD,
          "Your device location is out of date. Please try again.");
    }

    if (Double.isNaN(position.accuracyMeters()) || position.accuracyMeters() < 0
        || position.accuracyMeters() > geoFenceProperties.getMaxAccuracyMeters()) {
      throw reject(locationId, GeoFenceViolationReason.POSITION_TOO_INACCURATE,
          "Your device location is not precise enough (within "
              + Math.round(position.accuracyMeters()) + " m) to confirm you are at "
              + locationName + ". Please try again, ideally near a window or with Wi-Fi on.");
    }

    double distanceMeters = GeoDistance.metersBetween(position.latitude(), position.longitude(),
        location.getLatitude(), location.getLongitude());
    if (distanceMeters - position.accuracyMeters() > geoFenceProperties.getRadiusMeters()) {
      log.info("Geo-fence rejected locationId [{}]: distance={} m, accuracy={} m, radius={} m, "
          + "simulated={}", locationId, Math.round(distanceMeters),
          Math.round(position.accuracyMeters()), geoFenceProperties.getRadiusMeters(),
          position.simulated());
      throw new GeoFenceViolationException(GeoFenceViolationReason.OUTSIDE_FENCE,
          "You must be at " + locationName + " to queue songs.");
    }
  }

  /** @return the location if the fence is enforced there, otherwise null */
  private LocationEntity enforcedLocation(Integer locationId) {

    if (!geoFenceProperties.isEnabled() || !appProperties.isMaster() || locationId == null) {
      return null;
    }
    LocationEntity location = locationService.getLocationByIdNullIfNotExists(locationId);
    if (location == null || !location.isGeoFenced()) {
      return null;
    }
    if (location.getLatitude() == null || location.getLongitude() == null) {
      // Without coordinates there is nothing to fence against; refusing every patron would make
      // the location unusable from the Web/Mobile UI.
      log.warn("Geo-fence not enforced for locationId [{}]: it is geo-fenced but has no "
          + "latitude/longitude", locationId);
      return null;
    }
    return location;
  }

  private static GeoFenceViolationException reject(Integer locationId,
      GeoFenceViolationReason reason, String message) {

    log.info("Geo-fence rejected locationId [{}]: {}", locationId, reason);
    return new GeoFenceViolationException(reason, message);
  }
}
