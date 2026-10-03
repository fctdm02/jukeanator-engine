package com.djt.jukeanator_engine.domain.location.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.location.controller.GeoPositionHeaders;
import com.djt.jukeanator_engine.domain.location.dto.GeoFenceStatusDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationInfoSyncDto;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.location.model.GeoDistance;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;

/**
 * End-to-end geo-fencing on a master with {@code app.geo-fence.enabled=true}, over plain http and
 * ws on localhost -- no https, certificate, browser or device location involved:
 * <ol>
 * <li>A simulated slave connects to {@code /ws-slave} with its real API key and pushes its location
 * info on {@code /location-info}, exactly as {@code SlaveConnectionManager} does, and master's copy
 * of the location picks up the coordinates and geo-fence flag.</li>
 * <li>A Web/Mobile UI patron (a real JWT) calls {@code addSong} over HTTP with no position, with a
 * position outside the fence, and with one inside it; the browser's Geolocation API is simulated
 * by setting the {@code X-Geo-*} headers directly.</li>
 * </ol>
 *
 * <p>The slave is disconnected before any {@code addSong}, so a request that gets past the fence
 * fails fast with 503 {@code LocationOfflineException} instead of being queued on a slave. A 503
 * therefore proves the fence let the request through, and a 403 proves the fence stopped it.
 *
 * <p>Requires the same live MySQL {@code jukeanator_test} database as
 * {@link MasterSlaveLibrarySyncIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.mode=master", "app.repository-type=jpa",
    "app.geo-fence.enabled=true" })
class MasterSlaveGeoFenceIntegrationTest extends AbstractServiceIntegrationTest {

  private static final double LATITUDE = 42.3314;
  private static final double LONGITUDE = -83.0458;
  private static final double METERS_PER_DEGREE_LATITUDE =
      Math.PI * GeoDistance.EARTH_RADIUS_METERS / 180.0;
  private static final long WAIT_MILLIS = 10_000L;

  @LocalServerPort
  private int port;

  @Autowired
  private LocationService locationService;

  @Autowired
  private ConnectedSlaveRegistry connectedSlaveRegistry;

  @Autowired
  private JwtUtil jwtUtil;

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void slavePushesGeoFencedLocation_thenWebPatronMustBeInsideTheFenceToQueue() throws Exception {

    // ── master: an admin provisions the location, with placeholder coordinates ───
    String name = "Geo Fenced Tavern " + System.nanoTime();
    ProvisionedLocationDto provisioned =
        locationService.registerLocation(new RegisterLocationRequest(name, 1.0, 2.0));
    Integer locationId = provisioned.locationId();

    // ── slave: connects and pushes its real coordinates, geo-fenced ────────────
    StompSession slaveSession = connectAsSlave(provisioned.apiKey());
    slaveSession.send("/location-info",
        new LocationInfoSyncDto(name, LATITUDE, LONGITUDE, "LocationLogo.jpg", true));
    awaitTrue("master to apply the slave's coordinates", () -> {
      LocationEntity location = locationService.getLocationByIdNullIfNotExists(locationId);
      return location != null && Double.valueOf(LATITUDE).equals(location.getLatitude());
    });
    assertEquals(LONGITUDE, locationService.getLocationByIdNullIfNotExists(locationId).getLongitude());

    // ── Web UI: asks whether the location is fenced ─────────────────────────────
    GeoFenceStatusDto status = restTemplate.getForObject("/api/locations/{locationId}/geo-fence",
        GeoFenceStatusDto.class, locationId);
    assertTrue(status.enforced());
    assertEquals(LATITUDE, status.latitude());

    // Disconnect the slave: any request the fence lets through then fails fast with 503.
    slaveSession.disconnect();
    awaitTrue("master to register the slave disconnect",
        () -> !connectedSlaveRegistry.isConnected(locationId));

    String patronToken = jwtUtil.generateToken("geo-fence-patron@example.com",
        UserRole.ROLE_USER.name());

    // ── Web UI patron: no position, outside the fence, inside the fence ─────────
    ResponseEntity<String> noPosition = postAddSong(locationId, patronToken, null);
    assertEquals(HttpStatus.FORBIDDEN, noPosition.getStatusCode());
    assertTrue(noPosition.getBody().contains("GeoFenceViolationException"), noPosition.getBody());

    ResponseEntity<String> outside = postAddSong(locationId, patronToken, 300.0);
    assertEquals(HttpStatus.FORBIDDEN, outside.getStatusCode());
    assertTrue(outside.getBody().contains("You must be at " + name + " to queue songs."),
        outside.getBody());

    ResponseEntity<String> inside = postAddSong(locationId, patronToken, 20.0);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, inside.getStatusCode(), inside.getBody());

    // ── slave: the operator unticks Geo-fenced; no position is needed any more ───
    StompSession reconnected = connectAsSlave(provisioned.apiKey());
    reconnected.send("/location-info",
        new LocationInfoSyncDto(name, LATITUDE, LONGITUDE, "LocationLogo.jpg", false));
    awaitTrue("master to apply the slave's geo-fence flag",
        () -> !locationService.getLocationByIdNullIfNotExists(locationId).isGeoFenced());
    reconnected.disconnect();
    awaitTrue("master to register the slave disconnect",
        () -> !connectedSlaveRegistry.isConnected(locationId));

    assertFalse(restTemplate.getForObject("/api/locations/{locationId}/geo-fence",
        GeoFenceStatusDto.class, locationId).enforced());
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
        postAddSong(locationId, patronToken, null).getStatusCode());
  }

  // ── The simulated slave and Web UI ─────────────────────────────────────────

  /** Connects to master's /ws-slave the way SlaveConnectionManager does. */
  private StompSession connectAsSlave(String apiKey) throws Exception {

    WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.set("location-api-key", apiKey);

    return stompClient.connectAsync("ws://localhost:" + port + "/ws-slave",
        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
        .get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
  }

  /**
   * POSTs addSong as the Web UI would, with the X-Geo-* headers for a fresh reading the given
   * distance north of the location (10 m accuracy), or with no position when metersNorth is null.
   */
  private ResponseEntity<String> postAddSong(Integer locationId, String token,
      Double metersNorth) {

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    if (metersNorth != null) {
      headers.set(GeoPositionHeaders.LATITUDE,
          String.valueOf(LATITUDE + metersNorth / METERS_PER_DEGREE_LATITUDE));
      headers.set(GeoPositionHeaders.LONGITUDE, String.valueOf(LONGITUDE));
      headers.set(GeoPositionHeaders.ACCURACY, "10");
      headers.set(GeoPositionHeaders.TIMESTAMP, String.valueOf(System.currentTimeMillis()));
    }
    HttpEntity<AddSongToQueueRequest> request =
        new HttpEntity<>(new AddSongToQueueRequest(null, 1, 2, 1, false), headers);

    return restTemplate.postForEntity("/api/locations/{locationId}/song-queue/addSong", request,
        String.class, locationId);
  }

  private static void awaitTrue(String description, BooleanSupplier condition)
      throws InterruptedException {

    long deadline = System.currentTimeMillis() + WAIT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(100);
    }
    fail("Timed out waiting for " + description);
  }
}
