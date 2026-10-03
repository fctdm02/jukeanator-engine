package com.djt.jukeanator_engine.domain.songqueue.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.djt.jukeanator_engine.AbstractControllerTest;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.location.config.GeoFenceProperties;
import com.djt.jukeanator_engine.domain.location.controller.GeoPositionHeaders;
import com.djt.jukeanator_engine.domain.location.model.GeoDistance;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.service.GeoFenceServiceImpl;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.service.UserService;

/**
 * Drives {@link SongQueueController} as master would run it with {@code app.geo-fence.enabled},
 * using the real {@link GeoFenceServiceImpl} and simulating the browser by setting the
 * {@code X-Geo-*} headers directly, so no device location or https connection is needed.
 */
class SongQueueControllerGeoFenceTest extends AbstractControllerTest {

  private static final Integer LOCATION_ID = 7;
  private static final String BASE_PATH = "/api/locations/" + LOCATION_ID + "/song-queue";
  private static final double LATITUDE = 42.3314;
  private static final double LONGITUDE = -83.0458;
  private static final long NOW = Instant.parse("2026-10-03T21:00:00Z").toEpochMilli();
  private static final double METERS_PER_DEGREE_LATITUDE =
      Math.PI * GeoDistance.EARTH_RADIUS_METERS / 180.0;
  private static final String WEB_USER = "web@domain.com";

  @Mock
  private SongQueueService songQueueService;

  @Mock
  private UserService userService;

  @Mock
  private SongLibraryService songLibraryService;

  @Mock
  private LocationService locationService;

  private GeoFenceProperties geoFenceProperties;
  private SongQueueController songQueueController;

  @Override
  protected Object getController() {

    // Built here rather than in a @BeforeEach: the superclass builds MockMvc from this in its own
    // @BeforeEach, which runs first. The @Mock fields are already injected by then.
    if (songQueueController == null) {
      geoFenceProperties = new GeoFenceProperties();
      geoFenceProperties.setEnabled(true);
      AppProperties appProperties = new AppProperties();
      appProperties.setMode("master");

      GeoFenceServiceImpl geoFenceService = new GeoFenceServiceImpl(geoFenceProperties,
          appProperties, locationService, Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
      songQueueController = new SongQueueController(songQueueService, userService,
          songLibraryService, geoFenceService);
    }
    return songQueueController;
  }

  @BeforeEach
  void setUpLocation() {
    LocationEntity location =
        new LocationEntity(LOCATION_ID, "Joe's Bar", LATITUDE, LONGITUDE, "x");
    location.setGeoFenced(true);
    lenient().when(locationService.getLocationByIdNullIfNotExists(LOCATION_ID))
        .thenReturn(location);
  }

  private static Authentication webUser() {
    return new UsernamePasswordAuthenticationToken(WEB_USER, null, List.of());
  }

  private static Authentication adminUser() {
    return new UsernamePasswordAuthenticationToken("admin@domain.com", null,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  /** Adds the headers the Web UI sends: a fresh reading the given distance north of the bar. */
  private static MockHttpServletRequestBuilder withPosition(MockHttpServletRequestBuilder request,
      double metersNorth, double accuracyMeters, boolean simulated) {

    request.header(GeoPositionHeaders.LATITUDE,
            String.valueOf(LATITUDE + metersNorth / METERS_PER_DEGREE_LATITUDE))
        .header(GeoPositionHeaders.LONGITUDE, String.valueOf(LONGITUDE))
        .header(GeoPositionHeaders.ACCURACY, String.valueOf(accuracyMeters))
        .header(GeoPositionHeaders.TIMESTAMP, String.valueOf(NOW));
    if (simulated) {
      request.header(GeoPositionHeaders.SIMULATED, "true");
    }
    return request;
  }

  private MockHttpServletRequestBuilder addSong(Authentication principal) throws Exception {
    MockHttpServletRequestBuilder request = post(BASE_PATH + "/addSong")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AddSongToQueueRequest("u", 3, 4, 1, false)));
    return principal != null ? request.principal(principal) : request;
  }

  private MockHttpServletRequestBuilder changeQueue(String endpoint) throws Exception {
    return post(BASE_PATH + "/" + endpoint)
        .principal(webUser())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new ChangeSongQueueRequest(3, 4)));
  }

  private static SongQueueEntryDto aQueueEntry() {
    SongDto song = new SongDto(1, "Genre", 2, "Artist", 3, "Album", "/cover.jpg", 4, "Song", 1, 0);
    return new SongQueueEntryDto(WEB_USER, song, 1, "/music/song.mp3");
  }

  // ── addSong ──────────────────────────────────────────────────────────────

  @Test
  void addSong_webUserWithoutPosition_isRefusedAndNotCharged() throws Exception {
    mockMvc.perform(addSong(webUser()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("GeoFenceViolationException")))
        .andExpect(jsonPath("$.message", is("Your device location is required to queue songs at "
            + "Joe's Bar. Please allow location access and try again.")));

    verify(songQueueService, never()).addSongToQueue(any(), any());
    verify(userService, never()).handleSongAddedToQueueEvent(any(), any());
  }

  @Test
  void addSong_webUserInsideFence_isQueued() throws Exception {
    when(songQueueService.addSongToQueue(any(), any())).thenReturn(aQueueEntry());

    mockMvc.perform(withPosition(addSong(webUser()), 20, 10, false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.songPath", is("/music/song.mp3")));
  }

  @Test
  void addSong_webUserOutsideFence_isRefused() throws Exception {
    mockMvc.perform(withPosition(addSong(webUser()), 250, 10, false))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message", is("You must be at Joe's Bar to queue songs.")));

    verify(songQueueService, never()).addSongToQueue(any(), any());
  }

  @Test
  void addSong_simulatedPosition_isRefusedUnlessAllowed() throws Exception {
    mockMvc.perform(withPosition(addSong(webUser()), 0, 10, true))
        .andExpect(status().isForbidden());

    geoFenceProperties.setAllowSimulatedPosition(true);
    when(songQueueService.addSongToQueue(any(), any())).thenReturn(aQueueEntry());

    mockMvc.perform(withPosition(addSong(webUser()), 0, 10, true))
        .andExpect(status().isOk());
  }

  @Test
  void addSong_adminWithoutPosition_isQueued() throws Exception {
    when(songQueueService.addSongToQueue(any(), any())).thenReturn(aQueueEntry());

    mockMvc.perform(addSong(adminUser()))
        .andExpect(status().isOk());
  }

  @Test
  void addSong_localCallerWithoutPosition_isQueued() throws Exception {
    // No String (email) principal: the JFC/Swing kiosk or a system caller.
    when(songQueueService.addSongToQueue(any(), any())).thenReturn(aQueueEntry());

    mockMvc.perform(addSong(null))
        .andExpect(status().isOk());
  }

  // ── addMultipleSongs (Play Playlist) ────────────────────────────────────

  @Test
  void addMultipleSongs_webUserWithoutPosition_isRefusedAndNotCharged() throws Exception {
    AddMultipleSongsToQueueRequest request = new AddMultipleSongsToQueueRequest(null,
        List.of(new SongIdentifier(LOCATION_ID, 3, 1)), 1);

    mockMvc.perform(post(BASE_PATH + "/addMultipleSongs")
            .principal(webUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(userService);
    verify(songQueueService, never()).addMultipleSongsToQueue(any(), any());
  }

  @Test
  void addMultipleSongs_webUserInsideFence_isQueued() throws Exception {
    when(userService.getAffordableQueueAddCount(WEB_USER, LOCATION_ID, 1, false)).thenReturn(1);
    when(songQueueService.addMultipleSongsToQueue(any(), any())).thenReturn(List.of(aQueueEntry()));
    AddMultipleSongsToQueueRequest request = new AddMultipleSongsToQueueRequest(null,
        List.of(new SongIdentifier(LOCATION_ID, 3, 1)), 1);

    mockMvc.perform(withPosition(post(BASE_PATH + "/addMultipleSongs")
            .principal(webUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)), 0, 25, false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", is(1)));
  }

  // ── Reorder / remove ─────────────────────────────────────────────────────

  @Test
  void moveSongUp_webUserWithoutPosition_isRefusedAndNotCharged() throws Exception {
    mockMvc.perform(changeQueue("moveSongUpInQueue"))
        .andExpect(status().isForbidden());

    verify(songQueueService, never()).moveSongUpInQueue(any(), any());
    verify(userService, never()).chargeCreditsForQueueAction(any(), any(), any());
  }

  @Test
  void moveSongDown_webUserWithoutPosition_isRefused() throws Exception {
    mockMvc.perform(changeQueue("moveSongDownInQueue"))
        .andExpect(status().isForbidden());

    verify(songQueueService, never()).moveSongDownInQueue(any(), any());
  }

  @Test
  void removeSong_webUserWithoutPosition_isRefused() throws Exception {
    mockMvc.perform(changeQueue("removeSongDownFromQueue"))
        .andExpect(status().isForbidden());

    verify(songQueueService, never()).removeSongDownFromQueue(any(), any());
  }

  @Test
  void moveSongUp_webUserInsideFence_isMovedAndCharged() throws Exception {
    when(songQueueService.moveSongUpInQueue(any(), any())).thenReturn(1);

    mockMvc.perform(withPosition(changeQueue("moveSongUpInQueue"), 10, 10, false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(1)));

    verify(userService).chargeCreditsForQueueAction(WEB_USER, 1, LOCATION_ID);
  }

  // ── Malformed headers count as no position ───────────────────────────────

  @Test
  void addSong_malformedPosition_isTreatedAsMissing() throws Exception {
    mockMvc.perform(addSong(webUser())
            .header(GeoPositionHeaders.LATITUDE, "not-a-number")
            .header(GeoPositionHeaders.LONGITUDE, String.valueOf(LONGITUDE))
            .header(GeoPositionHeaders.ACCURACY, "10")
            .header(GeoPositionHeaders.TIMESTAMP, String.valueOf(NOW)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message", is("Your device location is required to queue songs at "
            + "Joe's Bar. Please allow location access and try again.")));
  }
}
