package com.djt.jukeanator_engine.domain.location.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.location.config.GeoFenceProperties;
import com.djt.jukeanator_engine.domain.location.dto.GeoFenceStatusDto;
import com.djt.jukeanator_engine.domain.location.exception.GeoFenceViolationException;
import com.djt.jukeanator_engine.domain.location.exception.GeoFenceViolationReason;
import com.djt.jukeanator_engine.domain.location.model.GeoDistance;
import com.djt.jukeanator_engine.domain.location.model.GeoPosition;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;

/**
 * Exercises the geo-fence rules directly, with a fixed clock and hand-built device positions, so
 * no browser, device location or https connection is involved.
 */
@ExtendWith(MockitoExtension.class)
class GeoFenceServiceTest {

  private static final Integer LOCATION_ID = 7;
  private static final Integer UNFENCED_LOCATION_ID = 8;
  private static final Integer UNKNOWN_LOCATION_ID = 99;
  private static final double LATITUDE = 42.3314;
  private static final double LONGITUDE = -83.0458;
  private static final long NOW = Instant.parse("2026-10-03T21:00:00Z").toEpochMilli();
  private static final double METERS_PER_DEGREE_LATITUDE =
      Math.PI * GeoDistance.EARTH_RADIUS_METERS / 180.0;

  @Mock
  private LocationService locationService;

  private GeoFenceProperties geoFenceProperties;
  private AppProperties appProperties;
  private GeoFenceService geoFenceService;

  @BeforeEach
  void setUp() {
    geoFenceProperties = new GeoFenceProperties();
    geoFenceProperties.setEnabled(true);

    appProperties = new AppProperties();
    appProperties.setMode("master");

    LocationEntity fenced = new LocationEntity(LOCATION_ID, "Joe's Bar", LATITUDE, LONGITUDE, "x");
    fenced.setGeoFenced(true);
    LocationEntity unfenced =
        new LocationEntity(UNFENCED_LOCATION_ID, "Open Bar", LATITUDE, LONGITUDE, "x");
    unfenced.setGeoFenced(false);
    lenient().when(locationService.getLocationByIdNullIfNotExists(LOCATION_ID)).thenReturn(fenced);
    lenient().when(locationService.getLocationByIdNullIfNotExists(UNFENCED_LOCATION_ID))
        .thenReturn(unfenced);

    geoFenceService = new GeoFenceServiceImpl(geoFenceProperties, appProperties, locationService,
        Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
  }

  /** A fresh, non-simulated reading the given distance due north of the location. */
  private static GeoPosition metersNorth(double meters, double accuracyMeters) {
    return new GeoPosition(LATITUDE + meters / METERS_PER_DEGREE_LATITUDE, LONGITUDE,
        accuracyMeters, NOW, false);
  }

  private GeoFenceViolationReason rejectionReason(Integer locationId, GeoPosition position) {
    return assertThrows(GeoFenceViolationException.class,
        () -> geoFenceService.verifyWithinFence(locationId, position)).getReason();
  }

  // ── When the fence is not enforced, anything goes ───────────────────────

  @Test
  void disabled_allowsMissingPosition() {
    geoFenceProperties.setEnabled(false);

    assertFalse(geoFenceService.isEnforced(LOCATION_ID));
    geoFenceService.verifyWithinFence(LOCATION_ID, null);
  }

  @Test
  void notMaster_allowsMissingPosition() {
    appProperties.setMode("standalone");

    assertFalse(geoFenceService.isEnforced(LOCATION_ID));
    geoFenceService.verifyWithinFence(LOCATION_ID, null);
  }

  @Test
  void locationNotGeoFenced_allowsMissingPosition() {
    assertFalse(geoFenceService.isEnforced(UNFENCED_LOCATION_ID));
    geoFenceService.verifyWithinFence(UNFENCED_LOCATION_ID, null);
  }

  @Test
  void unknownLocation_allowsMissingPosition() {
    assertFalse(geoFenceService.isEnforced(UNKNOWN_LOCATION_ID));
    geoFenceService.verifyWithinFence(UNKNOWN_LOCATION_ID, null);
  }

  @Test
  void locationWithoutCoordinates_allowsMissingPosition() {
    LocationEntity noCoordinates = new LocationEntity(LOCATION_ID, "Joe's Bar", null, null, "x");
    lenient().when(locationService.getLocationByIdNullIfNotExists(LOCATION_ID))
        .thenReturn(noCoordinates);

    assertFalse(geoFenceService.isEnforced(LOCATION_ID));
    geoFenceService.verifyWithinFence(LOCATION_ID, null);
  }

  // ── Rejections ───────────────────────────────────────────────────────────

  @Test
  void enforced_missingPosition_isRejected() {
    assertTrue(geoFenceService.isEnforced(LOCATION_ID));
    assertEquals(GeoFenceViolationReason.POSITION_REQUIRED, rejectionReason(LOCATION_ID, null));
  }

  @Test
  void simulatedPosition_isRejectedUnlessAllowed() {
    GeoPosition simulated = new GeoPosition(LATITUDE, LONGITUDE, 10, NOW, true);

    assertEquals(GeoFenceViolationReason.SIMULATED_POSITION_NOT_ALLOWED,
        rejectionReason(LOCATION_ID, simulated));

    geoFenceProperties.setAllowSimulatedPosition(true);
    geoFenceService.verifyWithinFence(LOCATION_ID, simulated);
  }

  @Test
  void stalePosition_isRejected() {
    long tooOld = NOW - (geoFenceProperties.getMaxPositionAgeSeconds() * 1000L) - 1;

    assertEquals(GeoFenceViolationReason.POSITION_TOO_OLD, rejectionReason(LOCATION_ID,
        new GeoPosition(LATITUDE, LONGITUDE, 10, tooOld, false)));
  }

  @Test
  void positionAtMaxAge_isAccepted() {
    long oldest = NOW - geoFenceProperties.getMaxPositionAgeSeconds() * 1000L;

    geoFenceService.verifyWithinFence(LOCATION_ID,
        new GeoPosition(LATITUDE, LONGITUDE, 10, oldest, false));
  }

  @Test
  void positionFromTheFuture_isRejectedBeyondClockSkew() {
    long slightlyAhead = NOW + GeoFenceServiceImpl.MAX_CLOCK_SKEW_MILLIS;
    long farAhead = slightlyAhead + 1;

    geoFenceService.verifyWithinFence(LOCATION_ID,
        new GeoPosition(LATITUDE, LONGITUDE, 10, slightlyAhead, false));
    assertEquals(GeoFenceViolationReason.POSITION_TOO_OLD, rejectionReason(LOCATION_ID,
        new GeoPosition(LATITUDE, LONGITUDE, 10, farAhead, false)));
  }

  @Test
  void imprecisePosition_isRejected() {
    assertEquals(GeoFenceViolationReason.POSITION_TOO_INACCURATE,
        rejectionReason(LOCATION_ID, metersNorth(0, 151)));
  }

  @Test
  void negativeAccuracy_isRejected() {
    assertEquals(GeoFenceViolationReason.POSITION_TOO_INACCURATE,
        rejectionReason(LOCATION_ID, metersNorth(0, -1)));
  }

  // ── Distance, with the default 50 m radius ──────────────────────────────

  @Test
  void atTheLocation_isAccepted() {
    geoFenceService.verifyWithinFence(LOCATION_ID, metersNorth(0, 5));
  }

  @Test
  void insideTheRadius_isAccepted() {
    geoFenceService.verifyWithinFence(LOCATION_ID, metersNorth(40, 5));
  }

  @Test
  void outsideTheRadius_isAcceptedWhenAccuracyReachesTheFence() {
    // 80 m away, but the device is only sure to within 40 m: it could be 40 m away.
    geoFenceService.verifyWithinFence(LOCATION_ID, metersNorth(80, 40));
  }

  @Test
  void outsideTheRadius_isRejectedWhenAccuracyDoesNotReachTheFence() {
    assertEquals(GeoFenceViolationReason.OUTSIDE_FENCE,
        rejectionReason(LOCATION_ID, metersNorth(80, 10)));
  }

  @Test
  void farAway_isRejectedEvenAtMaxAccuracy() {
    assertEquals(GeoFenceViolationReason.OUTSIDE_FENCE,
        rejectionReason(LOCATION_ID, metersNorth(250, 150)));
  }

  @Test
  void rejectionMessage_namesTheLocation() {
    GeoFenceViolationException e = assertThrows(GeoFenceViolationException.class,
        () -> geoFenceService.verifyWithinFence(LOCATION_ID, metersNorth(500, 10)));

    assertEquals("You must be at Joe's Bar to queue songs.", e.getMessage());
  }

  @Test
  void customRadius_isHonored() {
    geoFenceProperties.setRadiusMeters(18.288); // 20 yards

    geoFenceService.verifyWithinFence(LOCATION_ID, metersNorth(15, 0));
    assertEquals(GeoFenceViolationReason.OUTSIDE_FENCE,
        rejectionReason(LOCATION_ID, metersNorth(25, 5)));
  }

  // ── Status reported to the Web/Mobile UI ────────────────────────────────

  @Test
  void status_reportsSettingsAndCoordinates() {
    geoFenceProperties.setAllowSimulatedPosition(true);

    GeoFenceStatusDto status = geoFenceService.getStatus(LOCATION_ID);

    assertTrue(status.enforced());
    assertEquals(50.0, status.radiusMeters());
    assertEquals(150.0, status.maxAccuracyMeters());
    assertTrue(status.allowSimulatedPosition());
    assertEquals(LATITUDE, status.latitude());
    assertEquals(LONGITUDE, status.longitude());
  }

  @Test
  void status_unknownLocation_isNotEnforcedAndHasNoCoordinates() {
    GeoFenceStatusDto status = geoFenceService.getStatus(UNKNOWN_LOCATION_ID);

    assertFalse(status.enforced());
    assertNull(status.latitude());
    assertNull(status.longitude());
  }
}
