package com.djt.jukeanator_engine.web;

import static com.djt.jukeanator_engine.web.BrokerMessageCapture.userDestination;
import static com.djt.jukeanator_engine.web.WebUiTestClient.key;
import static com.djt.jukeanator_engine.web.WebUiTestClient.keys;
import static com.djt.jukeanator_engine.web.WebUiTestClient.list;
import static com.djt.jukeanator_engine.web.WebUiTestClient.uniqueEmail;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.djt.jukeanator_engine.FakePaymentGatewayConfiguration;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.location.controller.LocationController;
import com.djt.jukeanator_engine.domain.location.controller.LocationEventStompController;
import com.djt.jukeanator_engine.domain.location.dto.LocationEventMessage;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotAlbumDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotArtistDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotGenreDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotSongDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.web.WebUiTestClient.Response;
import com.djt.jukeanator_engine.web.event.LocationTopics;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Web/Mobile UI against a master -- the production deployment, where every patron's account,
 * credits and playlists live, and every location's jukebox is a slave reached over
 * {@code /ws-slave}. Covers what only master does: the location picker, browsing a location's
 * synced library, per-location pricing, playlists tagged across locations, and how the UI's queue
 * and player calls behave while that location's jukebox is offline.
 *
 * <p>A location is provisioned over the admin endpoint and its library synced exactly as a slave's
 * {@code LibrarySyncService} does it; no slave is ever connected (an in-process {@code /ws-slave}
 * session needs a real port), so every queue/player call is the "jukebox offline" case -- which is
 * also what proves a patron is never charged for a play that did not happen. Queueing at a
 * connected slave is covered by {@code WebPatronPlayLifecycleTest}.
 *
 * <p>Requires the live MySQL {@code jukeanator_test} database, like the other master-mode
 * integration tests; locations and patrons are unique per run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.mode=master", "app.repository-type=jpa" })
@Import(FakePaymentGatewayConfiguration.class)
class WebUiMasterPatronLifecycleTest {

  private static final String PASSWORD = "secret123";
  private static final String MY_FAVORITES = "My Favorites";
  private static final byte[] COVER_ART = { 1, 2, 3, 4 };

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtUtil jwtUtil;

  @Autowired
  @Qualifier("brokerChannel")
  private AbstractSubscribableChannel brokerChannel;

  /** Where a connected slave's forwarded events arrive on master. */
  @Autowired
  private LocationEventStompController locationEvents;

  private WebUiTestClient ui;
  private BrokerMessageCapture stomp;

  private Integer locationId;
  private String locationName;

  @BeforeEach
  void setUp() throws Exception {

    ui = new WebUiTestClient(mockMvc);
    stomp = new BrokerMessageCapture(brokerChannel);

    // ── An admin provisions the bar's location; its slave then syncs its library ─────────
    String adminToken = jwtUtil.generateToken(uniqueEmail("admin"), UserRole.ROLE_ADMIN.name());
    locationName = "Web UI Tavern " + System.nanoTime();
    JsonNode provisioned = ui.post(adminToken, "/api/locations",
        new RegisterLocationRequest(locationName, 42.3314, -83.0458)).expect(200).json();
    locationId = provisioned.path("locationId").asInt();
    String apiKey = provisioned.path("apiKey").asText();

    Map<String, String> slaveHeaders = Map.of(LocationController.LOCATION_ID_HEADER,
        String.valueOf(locationId), LocationController.LOCATION_API_KEY_HEADER, apiKey);
    ui.postWithHeaders(null, slaveHeaders, "/api/locations/{locationId}/library-sync/metadata",
        librarySnapshot(), locationId).expect(200);
    for (int sourceAlbumId : new int[] { 501, 502 }) {
      int status = mockMvc.perform(post("/api/locations/{locationId}/library-sync/cover-art/{id}",
          locationId, sourceAlbumId)
          .header(LocationController.LOCATION_ID_HEADER, String.valueOf(locationId))
          .header(LocationController.LOCATION_API_KEY_HEADER, apiKey)
          .contentType(MediaType.IMAGE_JPEG).content(COVER_ART))
          .andReturn().getResponse().getStatus();
      assertEquals(204, status);
    }
  }

  @AfterEach
  void tearDown() {
    stomp.detach();
  }

  @Test
  void aPatron_picksALocation_browsesItsLibrary_buysCredits_andKeepsPlaylists() {

    // ── Boot: master serves no location of its own, so the UI picks one from the list ────
    assertTrue(ui.get(null, "/api/users/own-location-id").expect(200).json().isNull());
    assertTrue(ui.get(null, "/api/users/own-location").expect(200).json().isNull());

    JsonNode picker = ui.get(null, "/api/locations").expect(200).json();
    JsonNode ours = list(picker).stream()
        .filter(l -> l.path("locationId").asInt() == locationId).findFirst().orElseThrow();
    assertEquals(locationName, ours.path("name").asText());
    assertFalse(ours.path("online").asBoolean(), "no slave is connected");

    JsonNode pricing = ui.get(null, "/api/users/pricing-config?locationId={locationId}",
        locationId).expect(200).json();
    assertTrue(pricing.path("webCostMultiplier").asInt() >= 1);
    assertTrue(pricing.path("creditsPerDollar").asInt() >= 1);
    ui.get(null, "/api/locations/{locationId}/geo-fence", locationId).expect(200);

    // ── Browsing the location's synced library ───────────────────────────────────────────
    JsonNode popular = ui.get(null, libraryPath("popular")).expect(200).json();
    assertEquals(List.of("501/9002", "501/9001", "502/9003"), keys(popular.path("songs")),
        "most played first, as synced from the slave's own play counts");
    assertTrue(ui.get(null, libraryPath("popular") + "?songPage=1").expect(200).json()
        .path("songs").isEmpty());

    JsonNode found = ui.get(null, libraryPath("search") + "?searchFor={searchFor}", "Artist One")
        .expect(200).json();
    assertEquals(List.of("Artist One"), list(found.path("artists")).stream()
        .map(a -> a.path("artistName").asText()).toList());

    int artistId = found.path("artists").get(0).path("artistId").asInt();
    JsonNode artist = ui.get(null, libraryPath("artists/" + artistId)).expect(200).json();
    assertEquals(2, artist.path("albums").size());
    assertEquals(artistId,
        ui.get(null, libraryPath("artistByAlbum/501")).expect(200).json().path("artistId").asInt());

    JsonNode album = ui.get(null, libraryPath("albums/501")).expect(200).json();
    assertEquals("Album One", album.path("albumName").asText());
    assertEquals(List.of("501/9001", "501/9002"), keys(album.path("songs")));

    Response coverArt = ui.get(null, libraryPath("albums/501/coverArt")).expect(200);
    assertArrayEquals(COVER_ART, coverArt.bytes(), "served from master's synced copy");

    // ── Sign up and Add Funds ─────────────────────────────────────────────────────────────
    String email = uniqueEmail("master-patron");
    String token = ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "Ron", email, PASSWORD)).expect(200).json()
        .path("token").asText();
    assertEquals(0, balance(token));
    assertEquals("fake-client-token", ui.get(token, "/api/users/payment/client-token")
        .expect(200).json().path("clientToken").asText());
    JsonNode purchase = ui.post(token, "/api/users/add-funds",
        new AddFundsRequest("pkg-7", "fake-nonce")).expect(200).json();
    assertEquals(13, purchase.path("numCredits").asInt());
    assertEquals(13, balance(token));

    // ── Playlists hold songs tagged with the location they came from ──────────────────────
    ui.get(token, "/api/users/home").expect(200);
    ui.post(token, "/api/users/playlists/favorites/songs", songRef(501, 9002)).expect(204);
    ui.post(token, "/api/users/playlists", Map.of("playlistName", "Tavern Night")).expect(204);
    ui.post(token, "/api/users/playlists/{name}/songs", songRef(502, 9003), "Tavern Night")
        .expect(204);
    ui.post(token, "/api/users/playlists/{name}/songs", songRef(501, 9001), "Tavern Night")
        .expect(204);

    // A song the location's library does not have.
    ui.post(token, "/api/users/playlists/favorites/songs", songRef(501, 4242)).expect(404);

    JsonNode favorites = ui.get(token, "/api/users/playlists/favorites/songs").expect(200).json();
    assertEquals(List.of("501/9002"), keys(favorites));
    assertEquals(locationId.intValue(), favorites.get(0).path("locationId").asInt());

    // The playlist screens resolve each song against its own location's library -- master has
    // no library of its own to look them up in.
    JsonNode favoriteSongs = ui.get(token, "/api/users/playlists/{name}/songs", MY_FAVORITES)
        .expect(200).json();
    assertEquals(List.of("501/9002"), keys(favoriteSongs));
    assertEquals("Song B", favoriteSongs.get(0).path("songName").asText());
    JsonNode tavernNight = ui.get(token, "/api/users/playlists/{name}/songs", "Tavern Night")
        .expect(200).json();
    assertEquals(List.of("502/9003", "501/9001"), keys(tavernNight));

    ui.put(token, "/api/users/playlists/{name}/songs",
        List.of(Map.of("albumId", 501, "songId", 9001), Map.of("albumId", 502, "songId", 9003)),
        "Tavern Night").expect(204);
    JsonNode reordered = ui.get(token, "/api/users/playlists/{name}/songIdentifiers",
        "Tavern Night").expect(200).json();
    assertEquals(List.of("501/9001", "502/9003"), keys(reordered));
    for (JsonNode id : reordered) {
      assertEquals(locationId.intValue(), id.path("locationId").asInt());
    }

    JsonNode playlists = ui.get(token, "/api/users/playlists").expect(200).json();
    assertEquals(List.of(MY_FAVORITES, "Tavern Night"),
        list(playlists).stream().map(p -> p.path("name").asText()).toList());
    assertEquals(501, playlists.get(1).path("firstSongAlbumId").asInt());
  }

  @Test
  void whileALocationsJukeboxIsOffline_thePatronIsToldSo_andNeverCharged() {

    String email = uniqueEmail("offline-patron");
    String token = ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "Ron", email, PASSWORD)).expect(200).json()
        .path("token").asText();
    ui.post(token, "/api/users/add-funds", new AddFundsRequest("pkg-7", "fake-nonce"))
        .expect(200);

    // The Song Queue screen and Now Playing widget: 503, which app.js shows as an empty queue.
    for (String path : List.of(queuePath("queuedSongs"), queuePath("highestPriority"),
        playerPath("nowPlayingSong"))) {
      Response offline = ui.get(token, path);
      offline.expect(503);
      assertEquals("LocationOfflineException", offline.error());
    }

    // Every queue operation is refused before anything is charged.
    Map<String, Object> play = new LinkedHashMap<>();
    play.put("albumId", 501);
    play.put("songId", 9001);
    play.put("priority", 1);
    play.put("priorityPlay", false);
    List<Response> attempts = List.of(
        ui.post(token, queuePath("addSong"), play),
        ui.post(token, queuePath("addMultipleSongs"),
            Map.of("songIdentifiers", List.of(songRef(501, 9001)), "priority", 1)),
        ui.post(token, queuePath("checkSongsEligibility"),
            Map.of("songIdentifiers", List.of(songRef(501, 9001)), "priority", 1)),
        ui.post(token, queuePath("moveSongUpInQueue"), Map.of("albumId", 501, "songId", 9001)),
        ui.post(token, queuePath("moveSongDownInQueue"), Map.of("albumId", 501, "songId", 9001)),
        ui.post(token, queuePath("removeSongDownFromQueue"),
            Map.of("albumId", 501, "songId", 9001)));
    for (Response attempt : attempts) {
      attempt.expect(503);
    }
    assertEquals(13, balance(token), "nothing was charged");

    // The bar owner's accounting shows no spend there -- and is not the patron's to read.
    Instant now = Instant.now();
    String ledger = "/api/locations/{locationId}/credit-ledger?from={from}&to={to}";
    ui.get(token, ledger, locationId, now.minusSeconds(3600), now.plusSeconds(3600)).expect(403);
    String adminToken = jwtUtil.generateToken(uniqueEmail("admin"), UserRole.ROLE_ADMIN.name());
    assertTrue(ui.get(adminToken, ledger, locationId, now.minusSeconds(3600),
        now.plusSeconds(3600)).expect(200).json().isEmpty());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Regressions: defects found by these narratives, fixed before release
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void theHomeScreen_showsWhatIsHotAtThePickedLocation() {

    JsonNode home = ui.get(null, "/api/users/home-public?locationId={locationId}", locationId)
        .expect(200).json();
    assertEquals(List.of("501/9002", "501/9001", "502/9003"), keys(home.path("songsHotHere")));
    assertEquals(List.of("Artist One"), list(home.path("artistsHotHere")).stream()
        .map(a -> a.path("artistName").asText()).toList());

    String token = ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "Ron", uniqueEmail("home"), PASSWORD)).expect(200).json()
        .path("token").asText();
    JsonNode signedIn = ui.get(token, "/api/users/home?locationId={locationId}", locationId)
        .expect(200).json();
    assertEquals(List.of("501/9002", "501/9001", "502/9003"),
        keys(signedIn.path("songsHotHere")));

    // Master has no library of its own: without a location there is nothing hot, but no error.
    assertTrue(ui.get(null, "/api/users/home-public").expect(200).json().path("songsHotHere")
        .isEmpty());
    ui.get(null, "/api/users/home-public?locationId=999999").expect(404);
  }

  @Test
  void aPatronsOwnUpdates_reachThemLive() throws Exception {

    String email = uniqueEmail("live-credits");
    String token = ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "Ron", email, PASSWORD)).expect(200).json()
        .path("token").asText();
    ui.post(token, "/api/users/add-funds", new AddFundsRequest("pkg-7", "fake-nonce"))
        .expect(200);

    stomp.await(userDestination(email, "/queue/credits"),
        message -> message.path("numCredits").asInt() == 13);
  }

  @Test
  void aSlavesQueueAndNowPlaying_reachItsLocationsTopics_inTheStandalonePayloadShapes()
      throws Exception {

    Principal slave = () -> String.valueOf(locationId);
    Map<String, Object> song = Map.of("albumId", 501, "songId", 9001, "songName", "Song A");
    Map<String, Object> entry = Map.of("username", "patron@example.com", "song", song,
        "priority", 1);

    // Exactly what SlaveConnectionManager forwards over /ws-slave.
    locationEvents.handleLocationEvent(
        new LocationEventMessage(LocationTopics.QUEUE, List.of(entry)), slave);
    locationEvents.handleLocationEvent(
        new LocationEventMessage(LocationTopics.NOW_PLAYING, song), slave);
    locationEvents.handleLocationEvent(new LocationEventMessage(LocationTopics.QUEUE, null), slave);
    locationEvents.handleLocationEvent(
        new LocationEventMessage(LocationTopics.NOW_PLAYING, null), slave);

    stomp.await(LocationTopics.queue(locationId),
        message -> message.size() == 1 && "501/9001".equals(key(message.get(0))));
    stomp.await(LocationTopics.nowPlaying(locationId),
        message -> "501/9001".equals(key(message.path("song"))));
    stomp.await(LocationTopics.queue(locationId), message -> message.isArray()
        && message.isEmpty());
    stomp.await(LocationTopics.nowPlaying(locationId),
        message -> message.has("song") && message.path("song").isNull());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private int balance(String token) {
    return ui.get(token, "/api/users/me").expect(200).json().path("numCredits").asInt();
  }

  private Map<String, Object> songRef(int albumId, int songId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("locationId", locationId);
    body.put("albumId", albumId);
    body.put("songId", songId);
    return body;
  }

  private String libraryPath(String endpoint) {
    return "/api/locations/" + locationId + "/song-library/" + endpoint;
  }

  private String playerPath(String endpoint) {
    return "/api/locations/" + locationId + "/song-player/" + endpoint;
  }

  private String queuePath(String endpoint) {
    return "/api/locations/" + locationId + "/song-queue/" + endpoint;
  }

  /** One artist, two albums, three songs -- with Song B the most played. */
  private static LibrarySnapshotDto librarySnapshot() {

    LibrarySnapshotAlbumDto albumOne = new LibrarySnapshotAlbumDto(501, "Album One", 101,
        "Artist One", 2, "Rock", "hash-album-one", false, "Indie Label", "2020-01-01", false,
        List.of(new LibrarySnapshotSongDto(9001, "Song A", 1, 3),
            new LibrarySnapshotSongDto(9002, "Song B", 2, 5)));
    LibrarySnapshotAlbumDto albumTwo = new LibrarySnapshotAlbumDto(502, "Album Two", 101,
        "Artist One", 2, "Rock", "hash-album-two", false, "Indie Label", "2021-01-01", false,
        List.of(new LibrarySnapshotSongDto(9003, "Song C", 1, 1)));

    return new LibrarySnapshotDto(List.of(new LibrarySnapshotGenreDto(2, "Rock")),
        List.of(new LibrarySnapshotArtistDto(101, "Artist One")), List.of(albumOne, albumTwo));
  }
}
