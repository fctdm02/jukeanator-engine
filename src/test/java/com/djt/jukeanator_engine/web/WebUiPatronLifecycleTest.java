package com.djt.jukeanator_engine.web;

import static com.djt.jukeanator_engine.web.BrokerMessageCapture.userDestination;
import static com.djt.jukeanator_engine.web.WebUiTestClient.key;
import static com.djt.jukeanator_engine.web.WebUiTestClient.keys;
import static com.djt.jukeanator_engine.web.WebUiTestClient.list;
import static com.djt.jukeanator_engine.web.WebUiTestClient.texts;
import static com.djt.jukeanator_engine.web.WebUiTestClient.uniqueEmail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.djt.jukeanator_engine.FakePaymentGatewayConfiguration;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.web.WebUiTestClient.Response;
import com.djt.jukeanator_engine.web.event.LocationTopics;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Functional tests of every REST endpoint and STOMP subscription the Web/Mobile UI
 * ({@code static/js/app.js}) uses, each exercised the way a patron actually reaches it -- signing
 * up, browsing, buying credits, building playlists, queueing songs and closing the account -- so a
 * break in the API contract surfaces here rather than in an already-published mobile app.
 *
 * <p>Runs a real standalone jukebox: the whole Spring context, {@code SecurityConfig}'s rules,
 * {@code JwtAuthenticationFilter}, every service and the filesystem repositories, reached over
 * {@link MockMvc} (no port is opened). Only Add Funds' payment processor is faked
 * ({@link FakePaymentGatewayConfiguration}), since a standalone instance has no Braintree account.
 * What the UI's STOMP subscriptions would receive is captured at the broker
 * ({@link BrokerMessageCapture}).
 *
 * <p>The song queue is locked for the duration, so queued songs stay put instead of being played
 * out by {@code SongPlayerServiceImpl}'s background thread. Every patron is registered under an
 * email unique to the run, since the filesystem user store outlives a single test.
 *
 * <p>The narratives' credit arithmetic assumes {@code application-test.yml}'s pricing
 * (priorityCostMultiplier 2, creditsPerDollar 3, webCostMultiplier 2), which
 * {@link #setUp()} verifies through the same {@code /api/users/pricing-config} the UI prices with.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakePaymentGatewayConfiguration.class)
class WebUiPatronLifecycleTest {

  private static final String SONG_FIXTURES =
      "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/"
          + "SongScannerTest/RequireMetadataUseGenreTopFolder";

  private static final String PASSWORD = "secret123";
  private static final String NEW_PASSWORD = "new-secret456";
  private static final String MY_FAVORITES = "My Favorites";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SongLibraryService songLibraryService;

  @Autowired
  private SongQueueService songQueueService;

  @Autowired
  private JwtUtil jwtUtil;

  @Autowired
  @Qualifier("brokerChannel")
  private AbstractSubscribableChannel brokerChannel;

  private WebUiTestClient ui;
  private BrokerMessageCapture stomp;

  /** As the UI's boot sequence resolves it. */
  private Integer locationId;
  private JsonNode pricing;

  @BeforeEach
  void setUp() throws Exception {

    ui = new WebUiTestClient(mockMvc);
    stomp = new BrokerMessageCapture(brokerChannel);

    // The operator's one-time setup at the kiosk: scan the library, then hold the queue still.
    asSystem(() -> {
      if (songLibraryService.getAlbums(songLibraryService.getOwnLocationId()).isEmpty()) {
        songLibraryService.scanFileSystemForSongs(new ScanRequest(SONG_FIXTURES));
      }
      songQueueService.lock();
      Integer ownLocationId = songLibraryService.getOwnLocationId();

      // Hot Here lists only music that has been played -- queueing a song counts as a play -- so
      // a freshly scanned library gets one play per song, queued at the kiosk.
      if (songLibraryService.getMusicByPopularity(ownLocationId).songs().isEmpty()) {
        for (AlbumDto album : songLibraryService.getAlbums(ownLocationId)) {
          for (SongDto song : album.songs()) {
            songQueueService.addSongToQueue(ownLocationId, new AddSongToQueueRequest(
                SongQueueService.LOCAL_USERNAME, album.albumId(), song.songId(), 1, false));
          }
        }
      }
      songQueueService.flushQueue(ownLocationId);
    });
    stomp.clear();

    // ── The UI's boot sequence (the init block at the bottom of app.js) ──────────────────
    locationId = ui.get(null, "/api/users/own-location-id").expect(200).json().asInt();
    pricing = ui.get(null, "/api/users/pricing-config?locationId={locationId}", locationId)
        .expect(200).json();
    assertEquals(2, pricing.path("priorityCostMultiplier").asInt(), "see class javadoc");
    assertEquals(3, pricing.path("creditsPerDollar").asInt(), "see class javadoc");
    assertEquals(2, pricing.path("webCostMultiplier").asInt(), "see class javadoc");
  }

  @AfterEach
  void tearDown() throws Exception {
    stomp.detach();
    asSystem(() -> songQueueService.flushQueue(songLibraryService.getOwnLocationId()));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Signed out
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void aVisitor_browsesTheJukeboxSignedOut_andMustSignInToPlay() {

    // ── Boot: the location header, the location picker, pricing and geo-fencing ──────────
    JsonNode ownLocation = ui.get(null, "/api/users/own-location").expect(200).json();
    assertEquals(locationId.intValue(), ownLocation.path("locationId").asInt());
    assertFalse(ownLocation.path("name").asText("").isBlank());

    Response picker = ui.get(null, "/api/locations");
    assertEquals(404, picker.status(), "the location picker is master-only; app.js falls back to "
        + "the own location on any failure: " + picker.body());

    assertEquals(pricing, ui.get(null, "/api/users/pricing-config").expect(200).json(),
        "without a locationId, pricing defaults to this jukebox's own");

    JsonNode geoFence =
        ui.get(null, "/api/locations/{locationId}/geo-fence", locationId).expect(200).json();
    assertFalse(geoFence.path("enforced").asBoolean(), "only master enforces geo-fences");

    // ── Home screen ───────────────────────────────────────────────────────────────────────
    JsonNode home = ui.get(null, "/api/users/home-public").expect(200).json();
    for (String hotHere : List.of("artistsHotHere", "albumsHotHere", "songsHotHere")) {
      assertFalse(home.path(hotHere).isEmpty(), hotHere);
      assertTrue(home.path(hotHere).size() <= 10, hotHere + " is capped at 10");
    }

    // Signed-in-only content answers 401, which app.js turns into the login screen.
    assertEquals(401, ui.get(null, "/api/users/home").status());
    assertEquals(401, ui.get(null, "/api/users/me").status());
    assertEquals(401, ui.get(null, "/api/users/playlists").status());
    assertEquals(401, ui.get(null, "/api/users/playlists/favorites/songs").status());
    assertEquals(401, ui.get(null, "/api/users/search-history").status());

    // ── Hot Here → See All: infinite scroll pages each list until a page comes back empty ──
    List<JsonNode> songs = pageThroughPopular("songPage", "songs");
    List<JsonNode> albums = pageThroughPopular("albumPage", "albums");
    List<JsonNode> artists = pageThroughPopular("artistPage", "artists");
    assertTrue(songs.size() >= 6, "the fixture library's songs: " + songs.size());
    assertEquals(songs.size(), songs.stream().map(WebUiTestClient::key).distinct().count(),
        "no song is repeated across pages");
    assertFalse(albums.isEmpty());
    assertFalse(artists.isEmpty());
    for (JsonNode song : songs) {
      assertTrue(song.path("albumId").isInt() && song.path("songId").isInt(),
          "every song the UI can act on is addressed by album and song id: " + song);
    }

    // ── Search, including its per-category infinite scroll ───────────────────────────────
    JsonNode artist = artists.get(0);
    String artistName = artist.path("artistName").asText();
    String search = libraryPath("search") + "?searchFor={searchFor}";
    JsonNode found = ui.get(null, search, artistName).expect(200).json();
    assertTrue(list(found.path("artists")).stream()
        .anyMatch(a -> a.path("artistId").asInt() == artist.path("artistId").asInt()),
        "searching for an artist's name finds that artist: " + found);
    JsonNode nextPage = ui.get(null, search + "&songPage=1&albumPage=1&artistPage=1",
        artistName).expect(200).json();
    assertTrue(nextPage.path("songs").isEmpty() && nextPage.path("albums").isEmpty()
        && nextPage.path("artists").isEmpty(), "a fixture-sized result fits on one page");

    JsonNode nothing = ui.get(null, search, "zzz no such music zzz").expect(200).json();
    assertTrue(nothing.path("songs").isEmpty() && nothing.path("albums").isEmpty()
        && nothing.path("artists").isEmpty(), "no hits is an empty result, not an error");

    // ── Artist screen: from a search hit (by artistId) and from a song popup (by albumId) ──
    JsonNode artistDetail = ui.get(null, libraryPath("artists/" + artist.path("artistId").asInt()))
        .expect(200).json();
    assertEquals(artistName, artistDetail.path("artistName").asText());
    assertFalse(artistDetail.path("albums").isEmpty());
    int albumId = artistDetail.path("albums").get(0).path("albumId").asInt();

    JsonNode artistByAlbum =
        ui.get(null, libraryPath("artistByAlbum/" + albumId)).expect(200).json();
    assertEquals(artist.path("artistId").asInt(), artistByAlbum.path("artistId").asInt());

    // ── Album screen ──────────────────────────────────────────────────────────────────────
    JsonNode album = ui.get(null, libraryPath("albums/" + albumId)).expect(200).json();
    assertEquals(albumId, album.path("albumId").asInt());
    assertFalse(album.path("songs").isEmpty());
    for (JsonNode song : album.path("songs")) {
      assertEquals(albumId, song.path("albumId").asInt());
      assertFalse(song.path("songName").asText("").isBlank());
    }

    // ── Cover art <img> tags: images, cacheable; a missing one 404s to the <img> fallback ──
    for (JsonNode a : albums) {
      Response coverArt = ui.get(null, libraryPath("albums/" + a.path("albumId").asInt()
          + "/coverArt"));
      assertEquals(200, coverArt.status(), "every fixture album has a cover.jpg: " + a);
      assertTrue(coverArt.raw().getContentType().startsWith("image/"), coverArt.raw()
          .getContentType());
      assertTrue(coverArt.bytes().length > 0);
      assertTrue(coverArt.raw().getHeader(HttpHeaders.CACHE_CONTROL).contains("max-age"));
    }
    int artistCoverArt = ui.get(null, libraryPath("artists/" + artist.path("artistId").asInt()
        + "/coverArt")).status();
    assertTrue(artistCoverArt == 200 || artistCoverArt == 404, "status: " + artistCoverArt);

    // ── Now Playing widget and Song Queue screen ──────────────────────────────────────────
    ui.get(null, playerPath("nowPlayingSong")).expect(200);
    assertTrue(queuedSongs().isEmpty());
    assertEquals(2, ui.get(null, queuePath("highestPriority")).expect(200).json().asInt(),
        "an empty queue's next priority level");

    // ── Credit packages, shown before signing up ─────────────────────────────────────────
    JsonNode packages = ui.get(null, "/api/users/credit-packages").expect(200).json();
    assertEquals(List.of("pkg-28", "pkg-14", "pkg-7"),
        list(packages).stream().map(p -> p.path("id").asText()).toList());
    for (JsonNode p : packages) {
      assertTrue(p.path("credits").asInt() > 0 && p.path("priceUsd").decimalValue().signum() > 0);
    }

    // ── Trying to play, favorite or pay while signed out: 401 (→ login screen) ────────────
    JsonNode song = songs.get(0);
    assertEquals(401, ui.post(null, queuePath("addSong"), addSongBody(song, 1, false)).status());
    assertEquals(401, ui.post(null, queuePath("addMultipleSongs"),
        Map.of("songIdentifiers", List.of(songRef(song)), "priority", 1)).status());
    assertEquals(401, ui.post(null, queuePath("checkSongsEligibility"),
        Map.of("songIdentifiers", List.of(songRef(song)), "priority", 1)).status());
    assertEquals(401, ui.post(null, queuePath("moveSongUpInQueue"), moveBody(song)).status());
    assertEquals(401, ui.post(null, "/api/users/playlists/favorites/songs", songRef(song))
        .status());
    assertEquals(401, ui.get(null, "/api/users/payment/client-token").status());
    assertEquals(401, ui.post(null, "/api/users/add-funds", new AddFundsRequest("pkg-7", "n"))
        .status());
    assertTrue(queuedSongs().isEmpty(), "nothing reached the queue");
  }

  // ─────────────────────────────────────────────────────────────────────────
  // A patron's whole life on the jukebox
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void aPatron_signsUpBuysCreditsBuildsPlaylistsQueuesSongsAndClosesTheirAccount()
      throws Exception {

    List<JsonNode> songs = songsByDifferentArtists(4);
    JsonNode songA = songs.get(0);
    JsonNode songB = songs.get(1);
    JsonNode songC = songs.get(2);
    JsonNode songD = songs.get(3);
    String email = uniqueEmail("patron");

    // ── 1. Register: signed in at once, with no credits ───────────────────────────────────
    JsonNode auth = ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "Ron", email, PASSWORD)).expect(200).json();
    String token = auth.path("token").asText();
    assertEquals(email, auth.path("emailAddress").asText());
    assertEquals(UserRole.ROLE_USER.name(), auth.path("role").asText());

    JsonNode profile = ui.get(token, "/api/users/me").expect(200).json();
    assertEquals("Pat", profile.path("firstName").asText());
    assertEquals("Ron", profile.path("lastName").asText());
    assertEquals(email, profile.path("emailAddress").asText());
    assertEquals(0, profile.path("numCredits").asInt());
    assertEquals(0, profile.path("balanceUsd").decimalValue().signum());

    // ── 2. The signed-in home screen and the Playlists tab ────────────────────────────────
    JsonNode home = ui.get(token, "/api/users/home").expect(200).json();
    assertEquals(List.of(MY_FAVORITES), texts(home.path("myPlaylists")));
    assertTrue(home.path("myRecentPlays").isEmpty());
    assertTrue(home.path("searchHistory").isEmpty());
    assertFalse(home.path("songsHotHere").isEmpty());

    JsonNode playlists = ui.get(token, "/api/users/playlists").expect(200).json();
    assertEquals(1, playlists.size());
    assertEquals(MY_FAVORITES, playlists.get(0).path("name").asText());
    assertEquals(0, playlists.get(0).path("songCount").asInt());
    assertTrue(playlists.get(0).path("firstSongAlbumId").isNull());
    assertTrue(ui.get(token, "/api/users/playlists/favorites/songs").expect(200).json().isEmpty());

    // ── 3. Trying to play with no credits: 402, nothing queued, nothing charged ───────────
    Response broke = ui.post(token, queuePath("addSong"), addSongBody(songA, 1, false));
    broke.expect(402);
    assertEquals("InsufficientCreditsException", broke.error(),
        "app.js shows this error's message as-is and refreshes the credits widget");
    assertTrue(queuedSongs().isEmpty());
    assertEquals(0, balance(token));

    // ── 4. Add Funds: the payment sheet, then a purchase ──────────────────────────────────
    JsonNode clientToken =
        ui.get(token, "/api/users/payment/client-token").expect(200).json();
    assertFalse(clientToken.path("clientToken").asText("").isBlank());

    Response unknownPackage = ui.post(token, "/api/users/add-funds",
        new AddFundsRequest("pkg-999", "fake-nonce"));
    unknownPackage.expect(402);
    assertEquals("PaymentException", unknownPackage.error());
    Response noPaymentMethod = ui.post(token, "/api/users/add-funds",
        new AddFundsRequest("pkg-14", " "));
    noPaymentMethod.expect(402);
    assertEquals("PaymentException", noPaymentMethod.error());
    assertEquals(0, balance(token), "a refused purchase adds nothing");

    JsonNode purchase = ui.post(token, "/api/users/add-funds",
        new AddFundsRequest("pkg-14", "fake-nonce")).expect(200).json();
    assertEquals(24, purchase.path("creditsAdded").asInt());
    assertEquals(7, purchase.path("bonusCreditsAdded").asInt());
    assertEquals(31, purchase.path("numCredits").asInt());
    assertMoney(usd(31), purchase.path("balanceUsd").decimalValue());
    assertFalse(purchase.path("transactionId").asText("").isBlank());
    assertFalse(purchase.path("paymentSource").asText("").isBlank());

    stomp.await(userDestination(email, "/queue/credits"),
        message -> message.path("numCredits").asInt() == 31);
    profile = ui.get(token, "/api/users/me").expect(200).json();
    assertEquals(31, profile.path("numCredits").asInt());
    assertMoney(usd(31), profile.path("balanceUsd").decimalValue());

    // ── 5. Searching, and the search history under the search box ─────────────────────────
    String artistName = songA.path("artistName").asText();
    ui.post(token, "/api/users/search-history", Map.of("query", "  " + artistName + "  "))
        .expect(204);
    ui.post(token, "/api/users/search-history", Map.of("query", "zzz")).expect(204);
    ui.post(token, "/api/users/search-history", Map.of("query", "   ")).expect(204);
    ui.post(token, "/api/users/search-history", Map.of()).expect(204);
    assertEquals(List.of("zzz", artistName), searchHistory(token),
        "most recent first, trimmed, blanks ignored");

    ui.post(token, "/api/users/search-history", Map.of("query", artistName)).expect(204);
    assertEquals(List.of(artistName, "zzz"), searchHistory(token),
        "repeating a search moves it to the top instead of duplicating it");

    ui.delete(token, "/api/users/search-history/{index}", null, 1).expect(204);
    ui.delete(token, "/api/users/search-history/{index}", null, 99).expect(204);
    assertEquals(List.of(artistName), searchHistory(token));
    assertEquals(List.of(artistName),
        texts(ui.get(token, "/api/users/home").expect(200).json().path("searchHistory")));

    // ── 6. My Favorites, from the song popup's heart ──────────────────────────────────────
    ui.post(token, "/api/users/playlists/favorites/songs", songRef(songA)).expect(204);
    ui.post(token, "/api/users/playlists/favorites/songs", songRef(songB)).expect(204);
    assertEquals(List.of(key(songA), key(songB)), keys(favorites(token)));
    for (JsonNode favorite : favorites(token)) {
      assertEquals(locationId.intValue(), favorite.path("locationId").asInt(),
          "every favorite is tagged with the location it came from");
    }

    ui.delete(token, "/api/users/playlists/favorites/songs", songRef(songB)).expect(204);
    assertEquals(List.of(key(songA)), keys(favorites(token)));

    Response noSuchSong = ui.post(token, "/api/users/playlists/favorites/songs",
        Map.of("locationId", locationId, "albumId", 999_999, "songId", 1));
    noSuchSong.expect(404);
    assertEquals(List.of(key(songA)), keys(favorites(token)));

    JsonNode favoriteSongs = ui.get(token, "/api/users/playlists/{name}/songs", MY_FAVORITES)
        .expect(200).json();
    assertEquals(List.of(key(songA)), keys(favoriteSongs));
    assertEquals(songA.path("songName").asText(), favoriteSongs.get(0).path("songName").asText());

    // ── 7. A playlist of their own: create, fill, reorder, rename ─────────────────────────
    ui.post(token, "/api/users/playlists", Map.of("playlistName", "  Road Trip  ")).expect(204);
    ui.post(token, "/api/users/playlists", Map.of("playlistName", "Road Trip")).expect(409);
    ui.post(token, "/api/users/playlists", Map.of("playlistName", MY_FAVORITES)).expect(409);

    for (JsonNode song : List.of(songC, songB, songD)) {
      ui.post(token, "/api/users/playlists/{name}/songs", songRef(song), "Road Trip").expect(204);
    }
    ui.post(token, "/api/users/playlists/{name}/songs", songRef(songA), "No Such Playlist")
        .expect(404);

    playlists = ui.get(token, "/api/users/playlists").expect(200).json();
    assertEquals(List.of(MY_FAVORITES, "Road Trip"),
        list(playlists).stream().map(p -> p.path("name").asText()).toList());
    assertEquals(1, playlists.get(0).path("songCount").asInt());
    assertEquals(songA.path("albumId").asInt(), playlists.get(0).path("firstSongAlbumId").asInt());
    assertEquals(3, playlists.get(1).path("songCount").asInt());
    assertEquals(songC.path("albumId").asInt(), playlists.get(1).path("firstSongAlbumId").asInt());
    assertEquals(List.of(MY_FAVORITES, "Road Trip"),
        texts(ui.get(token, "/api/users/home").expect(200).json().path("myPlaylists")));

    assertEquals(List.of(key(songC), key(songB), key(songD)), keys(
        ui.get(token, "/api/users/playlists/{name}/songs", "Road Trip").expect(200).json()));

    // Edit Track Order sends {albumId, songId} only -- the stored locationId must survive it.
    ui.put(token, "/api/users/playlists/{name}/songs",
        List.of(moveBody(songD), moveBody(songB), moveBody(songC)), "Road Trip").expect(204);
    JsonNode roadTrip = ui.get(token, "/api/users/playlists/{name}/songIdentifiers", "Road Trip")
        .expect(200).json();
    assertEquals(List.of(key(songD), key(songB), key(songC)), keys(roadTrip));
    for (JsonNode id : roadTrip) {
      assertEquals(locationId.intValue(), id.path("locationId").asInt());
    }

    // Rename -- refused onto or from My Favorites, onto another playlist, or to a blank name.
    ui.post(token, "/api/users/playlists", Map.of("playlistName", "Brunch")).expect(204);
    ui.put(token, "/api/users/playlists/{name}", Map.of("playlistName", "Brunch"), "Road Trip")
        .expect(409);
    ui.put(token, "/api/users/playlists/{name}", Map.of("playlistName", MY_FAVORITES),
        "Road Trip").expect(400);
    ui.put(token, "/api/users/playlists/{name}", Map.of("playlistName", "Mine"), MY_FAVORITES)
        .expect(400);
    ui.put(token, "/api/users/playlists/{name}", Map.of("playlistName", "  "), "Road Trip")
        .expect(400);
    ui.put(token, "/api/users/playlists/{name}", Map.of("playlistName", "Late Night"),
        "No Such Playlist").expect(404);
    ui.put(token, "/api/users/playlists/{name}", Map.of("playlistName", " Late Night "),
        "Road Trip").expect(204);

    ui.get(token, "/api/users/playlists/{name}/songs", "Road Trip").expect(404);
    JsonNode lateNight = ui.get(token, "/api/users/playlists/{name}/songIdentifiers",
        "Late Night").expect(200).json();
    assertEquals(List.of(key(songD), key(songB), key(songC)), keys(lateNight),
        "a rename keeps the songs and their order");

    // One image URL per playlist: My Favorites' own image, the cover art of the first song at the
    // location it came from, or the generic image for an empty playlist.
    assertEquals("/images/MyFavorites_Playlist.png", playlistCoverArt(token, MY_FAVORITES));
    assertEquals(libraryPath("albums/" + songD.path("albumId").asInt() + "/coverArt"),
        playlistCoverArt(token, "Late Night"));
    assertEquals("/images/Generic_Playlist.png", playlistCoverArt(token, "Brunch"));
    assertEquals("/images/Generic_Playlist.png", playlistCoverArt(token, "No Such Playlist"));

    // Delete -- My Favorites can never be deleted.
    ui.delete(token, "/api/users/playlists/{name}", null, "Brunch").expect(204);
    ui.delete(token, "/api/users/playlists/{name}", null, "Brunch").expect(404);
    ui.delete(token, "/api/users/playlists/{name}", null, MY_FAVORITES).expect(400);

    // ── 8. Playing a playlist: Multi-Select Mode checks, then queues the selection ───────
    List<Object> elsewhere = List.of(Map.of("locationId", locationId + 1000,
        "albumId", songA.path("albumId").asInt(), "songId", songA.path("songId").asInt()));
    List<Object> toCheck = new ArrayList<>(list(lateNight));
    toCheck.addAll(elsewhere);
    JsonNode eligibility = ui.post(token, queuePath("checkSongsEligibility"),
        Map.of("songIdentifiers", toCheck, "priority", 1)).expect(200).json();
    assertEquals(List.of(key(songD), key(songB), key(songC), key(songA)), keys(eligibility),
        "results come back in request order");
    for (int i = 0; i < 3; i++) {
      assertTrue(eligibility.get(i).path("ineligibleReason").isNull(), eligibility.toString());
    }
    assertEquals("is not available at this location",
        eligibility.get(3).path("ineligibleReason").asText());

    stomp.clear();
    JsonNode queued = ui.post(token, queuePath("addMultipleSongs"),
        Map.of("songIdentifiers", toCheck, "priority", 1)).expect(200).json();
    assertEquals(List.of(key(songD), key(songB), key(songC)), keys(queued),
        "the song from another location is dropped");
    for (JsonNode entry : queued) {
      assertEquals(email, entry.path("username").asText());
      assertEquals(1, entry.path("priority").asInt());
    }
    int expectedBalance = 31 - 3 * queueAddCost(1, false); // 25
    assertEquals(expectedBalance, balance(token), "each queued song is charged as a normal play");
    assertEquals(List.of(key(songD), key(songB), key(songC)), keys(queuedSongs()));
    stomp.await(LocationTopics.queue(locationId), message -> message.size() == 3);
    for (JsonNode song : List.of(songD, songB, songC)) {
      stomp.await(userDestination(email, "/queue/recent-plays"),
          message -> key(message).equals(key(song)));
    }

    // Queued songs are now ineligible until they have played (and the minimum wait has passed).
    JsonNode recheck = ui.post(token, queuePath("checkSongsEligibility"),
        Map.of("songIdentifiers", List.of(songRef(songD)), "priority", 1)).expect(200).json();
    assertFalse(recheck.get(0).path("ineligibleReason").isNull(), recheck.toString());

    // ── 9. A priority play from the song popup, at the next free priority level ──────────
    int priorityLevel = ui.get(token, queuePath("highestPriority")).expect(200).json().asInt();
    assertEquals(2, priorityLevel, "one above the queue's top (priority-1) entry");

    stomp.clear();
    JsonNode priorityPlay = ui.post(token, queuePath("addSong"),
        addSongBody(songA, priorityLevel, true)).expect(200).json();
    assertEquals(email, priorityPlay.path("username").asText());
    assertEquals(2, priorityPlay.path("priority").asInt());
    expectedBalance -= queueAddCost(2, true); // 25 - 8 = 17
    assertEquals(expectedBalance, balance(token));
    assertEquals(List.of(key(songA), key(songD), key(songB), key(songC)), keys(queuedSongs()),
        "a priority play jumps the normal plays");
    assertEquals(3, ui.get(token, queuePath("highestPriority")).expect(200).json().asInt());

    stomp.await(userDestination(email, "/queue/recent-plays"),
        message -> key(message).equals(key(songA)));
    stomp.await(userDestination(email, "/queue/credits"),
        message -> message.path("numCredits").asInt() == 17);
    stomp.await(LocationTopics.queue(locationId), message -> message.size() == 4);

    JsonNode recentPlays = ui.get(token, "/api/users/home").expect(200).json()
        .path("myRecentPlays");
    assertEquals(key(songA), key(recentPlays.get(0)), "most recent first");
    assertEquals(Set.of(key(songA), key(songB), key(songC), key(songD)),
        new HashSet<>(keys(recentPlays)));

    // ── 10. Reordering and removing on the Song Queue screen ──────────────────────────────
    ui.post(token, queuePath("moveSongUpInQueue"), moveBody(songC)).expect(200);
    expectedBalance -= queueActionCost(1); // 17 - 6 = 11
    assertEquals(expectedBalance, balance(token));
    assertEquals(List.of(key(songA), key(songD), key(songC), key(songB)), keys(queuedSongs()));

    ui.post(token, queuePath("moveSongDownInQueue"), moveBody(songD)).expect(200);
    expectedBalance -= queueActionCost(1); // 11 - 6 = 5
    assertEquals(expectedBalance, balance(token));
    assertEquals(List.of(key(songA), key(songC), key(songD), key(songB)), keys(queuedSongs()));

    // Moving the priority-2 song costs 12; only 5 are held -- refused, the queue untouched.
    Response cannotAfford = ui.post(token, queuePath("moveSongDownInQueue"), moveBody(songA));
    cannotAfford.expect(402);
    assertEquals("InsufficientCreditsException", cannotAfford.error());
    assertEquals(expectedBalance, balance(token));
    assertEquals(List.of(key(songA), key(songC), key(songD), key(songB)), keys(queuedSongs()));

    // Top up with the smallest package.
    ui.post(token, "/api/users/add-funds", new AddFundsRequest("pkg-7", "fake-nonce"))
        .expect(200);
    expectedBalance += 13; // 18

    // Moving the bottom song down changes nothing, and so costs nothing.
    ui.post(token, queuePath("moveSongDownInQueue"), moveBody(songB)).expect(200);
    assertEquals(expectedBalance, balance(token), "a move that changes nothing is free");
    assertEquals(List.of(key(songA), key(songC), key(songD), key(songB)), keys(queuedSongs()));

    ui.post(token, queuePath("removeSongDownFromQueue"), moveBody(songB)).expect(200);
    expectedBalance -= queueActionCost(1); // 12
    assertEquals(expectedBalance, balance(token));
    assertEquals(List.of(key(songA), key(songC), key(songD)), keys(queuedSongs()));

    // Removing a song that is no longer queued changes nothing and costs nothing.
    ui.post(token, queuePath("removeSongDownFromQueue"), moveBody(songB)).expect(200);
    assertEquals(expectedBalance, balance(token));

    // ── 11. The Account screen: name, password ────────────────────────────────────────────
    JsonNode updated = ui.put(token, "/api/users/me", new UpdateProfileRequest("Patricia", null))
        .expect(200).json();
    assertEquals("Patricia", updated.path("firstName").asText());
    assertEquals("Ron", updated.path("lastName").asText(), "an omitted field is left unchanged");
    assertEquals(expectedBalance, updated.path("numCredits").asInt());

    // A wrong current password is a 400 (never a 401, which app.js treats as signed out), and a
    // new password must meet the same rules as at registration.
    Response wrongCurrent = ui.post(token, "/api/users/change-password",
        new ChangePasswordRequest("not-my-password", NEW_PASSWORD));
    wrongCurrent.expect(400);
    assertEquals("IncorrectPasswordException", wrongCurrent.error());
    ui.post(token, "/api/users/change-password", new ChangePasswordRequest(PASSWORD, "short1"))
        .expect(400);
    ui.post(token, "/api/users/change-password",
        new ChangePasswordRequest(PASSWORD, NEW_PASSWORD)).expect(204);

    ui.post(null, "/api/users/login", new LoginRequest(email, PASSWORD)).expect(401);
    // An email address is one account however it is typed -- e.g. capitalized by a phone keyboard.
    JsonNode login = ui.post(null, "/api/users/login",
        new LoginRequest("  " + email.toUpperCase(java.util.Locale.ROOT) + " ", NEW_PASSWORD))
        .expect(200).json();
    String newToken = login.path("token").asText();
    assertEquals(email, login.path("emailAddress").asText());
    assertEquals(expectedBalance, balance(newToken), "a new session sees the same account");
    assertEquals(List.of(MY_FAVORITES, "Late Night"), list(
        ui.get(newToken, "/api/users/playlists").expect(200).json()).stream()
        .map(p -> p.path("name").asText()).toList());

    // ── 12. Closing the account ───────────────────────────────────────────────────────────
    ui.delete(newToken, "/api/users/me", null).expect(204);

    ui.post(null, "/api/users/login", new LoginRequest(email, NEW_PASSWORD)).expect(401);
    // A token issued before closing is still a well-formed JWT, but no longer names an account:
    // 401, which app.js handles by signing out.
    assertEquals(401, ui.get(newToken, "/api/users/me").status());
    assertEquals(401, ui.get(newToken, "/api/users/home").status());
    Response closedPlay = ui.post(newToken, queuePath("addSong"), addSongBody(songB, 1, false));
    assertFalse(closedPlay.isSuccessful(), closedPlay.body());
    assertFalse(keys(queuedSongs()).contains(key(songB)));

    // The email address is free again, for a brand-new account with nothing carried over.
    String reRegistered = ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "Ron", email, PASSWORD)).expect(200).json()
        .path("token").asText();
    assertEquals(0, balance(reRegistered));
    assertEquals(List.of(MY_FAVORITES), texts(
        ui.get(reRegistered, "/api/users/home").expect(200).json().path("myPlaylists")));
    assertTrue(favorites(reRegistered).isEmpty());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // What a patron must never get away with
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void aPatron_cannotChooseTheirOwnPriorityOrPrice() throws Exception {

    List<JsonNode> songs = songsByDifferentArtists(3);
    String token = registerWithCredits(uniqueEmail("chancer"), "pkg-7"); // 13 credits

    // A normal play always queues at priority 1, whatever priority the request names.
    JsonNode normal = ui.post(token, queuePath("addSong"), addSongBody(songs.get(0), 50, false))
        .expect(200).json();
    assertEquals(1, normal.path("priority").asInt());
    assertEquals(13 - queueAddCost(1, false), balance(token));

    // A priority play below priority 1 would cost nothing: refused, not queued, not charged.
    for (int freePriority : new int[] { 0, -5 }) {
      Response refused = ui.post(token, queuePath("addSong"),
          addSongBody(songs.get(1), freePriority, true));
      refused.expect(400);
    }
    Map<String, Object> noPriority = new LinkedHashMap<>(addSongBody(songs.get(1), 1, true));
    noPriority.remove("priority");
    ui.post(token, queuePath("addSong"), noPriority).expect(400);
    assertEquals(List.of(key(songs.get(0))), keys(queuedSongs()));
    assertEquals(13 - queueAddCost(1, false), balance(token));

    // A priority play is charged for exactly the priority it is queued at -- including priority 1,
    // which is what highestPriority offers while only background (priority-0) songs are queued.
    JsonNode priorityPlay = ui.post(token, queuePath("addSong"),
        addSongBody(songs.get(2), 1, true)).expect(200).json();
    assertEquals(1, priorityPlay.path("priority").asInt());
    assertEquals(13 - queueAddCost(1, false) - queueAddCost(1, true), balance(token));
  }

  @Test
  void aPatron_cannotReachTheOperatorsControls() {

    String token = registerWithCredits(uniqueEmail("curious"), "pkg-28");
    JsonNode song = songsByDifferentArtists(1).get(0);

    Map<String, Object> scan = Map.of("rootPath", SONG_FIXTURES);
    List<Response> attempts = List.of(
        ui.post(token, libraryPath("scan"), scan),
        ui.post(token, libraryPath("scanNoPath"), null),
        ui.post(token, libraryPath("resetSongStatistics"), null),
        ui.post(token, libraryPath("restoreSongStatistics"), "CDStats.TXT"),
        ui.post(token, libraryPath("storeSongLibraryAndStatistics"), null),
        ui.post(token, libraryPath("downloadAlbumCoverArt"), Map.of()),
        ui.post(token, libraryPath("authenticateForAdminPanel"), Map.of()),
        ui.post(token, libraryPath("albums/" + song.path("albumId").asInt()
            + "/updateAlbumMetadata"), Map.of()),
        ui.post(token, queuePath("addAlbum"), Map.of("albumId", song.path("albumId").asInt())),
        ui.post(token, queuePath("flushQueue"), null),
        ui.post(token, queuePath("randomizeQueue"), null),
        ui.post(token, queuePath("saveQueueAsPlaylist"), "playlist.txt"),
        ui.post(token, queuePath("loadPlaylistIntoQueue"), Map.of()),
        ui.post(token, playerPath("next"), null),
        ui.post(token, playerPath("pause"), null),
        ui.post(token, playerPath("stop"), null),
        ui.post(token, playerPath("lockQueue"), null),
        ui.post(token, playerPath("unlockQueue"), null));

    for (Response attempt : attempts) {
      assertEquals(403, attempt.status(), "a patron must be refused: " + attempt.body());
    }
    assertEquals(48 + 24, balance(token), "nothing was charged");
    assertTrue(queuedSongs().isEmpty(), "nothing was queued");
  }

  @Test
  void anyRequestWithoutAValidToken_isTreatedAsSignedOut() {

    String email = uniqueEmail("tokens");
    String token = registerWithCredits(email, "pkg-7");

    assertEquals(401, ui.get("not-a-jwt", "/api/users/me").status());
    assertEquals(401, ui.get(token + "x", "/api/users/me").status(), "a tampered signature");
    // Public endpoints stay usable with a bad token, as with none.
    ui.get("not-a-jwt", "/api/users/home-public").expect(200);

    // Another user's email cannot be claimed without their password...
    assertEquals(401, ui.post(null, "/api/users/login", new LoginRequest(email, "wrong")).status());
    assertEquals(401, ui.post(null, "/api/users/login",
        new LoginRequest(uniqueEmail("nobody"), PASSWORD)).status());

    // ...and the request body can never name someone else as the one queueing.
    JsonNode song = songsByDifferentArtists(1).get(0);
    Map<String, Object> impersonation = new LinkedHashMap<>(addSongBody(song, 1, false));
    impersonation.put("username", "someone-else@example.com");
    JsonNode entry = ui.post(token, queuePath("addSong"), impersonation).expect(200).json();
    assertEquals(email, entry.path("username").asText());
    assertEquals(13 - queueAddCost(1, false), balance(token));

    // An admin-role token is honored only for the operator's own controls.
    String adminToken = jwtUtil.generateToken(uniqueEmail("operator"), UserRole.ROLE_ADMIN.name());
    assertNotEquals(403, ui.post(adminToken, playerPath("lockQueue"), null).status());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Regressions: defects found by these narratives, fixed before release
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void aSongTheQueueRulesRefuse_isNeitherQueuedNorCharged() {

    JsonNode song = songsByDifferentArtists(1).get(0);
    String email = uniqueEmail("repeat");
    String token = registerWithCredits(email, "pkg-7");

    ui.post(token, queuePath("addSong"), addSongBody(song, 1, false)).expect(200);
    stomp.clear();

    // Queueing it again -- from the song popup, as a normal or a priority play -- is a 409 with a
    // reason app.js shows as-is.
    for (boolean priorityPlay : new boolean[] { false, true }) {
      Response again = ui.post(token, queuePath("addSong"),
          addSongBody(song, priorityPlay ? 2 : 1, priorityPlay));
      again.expect(409);
      assertEquals("SongNotEligibleException", again.error());
      String message = again.json().path("message").asText();
      assertTrue(message.startsWith("\"" + song.path("songName").asText() + "\""), message);
    }

    assertEquals(1, queuedSongs().size());
    assertEquals(13 - queueAddCost(1, false), balance(token), "a refused play is never charged");
    assertTrue(stomp.to(userDestination(email, "/queue/credits")).isEmpty(),
        "no credit change was announced");
    assertTrue(stomp.to(userDestination(email, "/queue/recent-plays")).isEmpty(),
        "nor a recent play");

    // A song that does not exist is a 404, likewise uncharged.
    ui.post(token, queuePath("addSong"),
        Map.of("albumId", 999_999, "songId", 1, "priority", 1, "priorityPlay", false))
        .expect(404);
    assertEquals(13 - queueAddCost(1, false), balance(token));
  }

  @Test
  void routineAccountMistakes_areClientErrors_notServerErrors() {

    String email = uniqueEmail("mistakes");
    String token = registerWithCredits(email, "pkg-7");

    Response duplicate = ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "Ron", email, PASSWORD));
    duplicate.expect(409);
    assertEquals("EmailAlreadyRegisteredException", duplicate.error());
    ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "Ron", email.toUpperCase(java.util.Locale.ROOT), PASSWORD))
        .expect(409);

    Response wrongPassword = ui.post(token, "/api/users/change-password",
        new ChangePasswordRequest("not-my-password", NEW_PASSWORD));
    wrongPassword.expect(400);
    assertEquals("IncorrectPasswordException", wrongPassword.error());
    assertEquals(200, ui.get(token, "/api/users/me").status(), "the patron is still signed in");
  }

  @Test
  void accountDetails_areValidatedServerSide() {

    for (String badEmail : new String[] { "", "   ", "not-an-email", "pat@nowhere", "a b@x.com" }) {
      assertEquals(400, ui.post(null, "/api/users/register",
          new RegisterRequest("Pat", "Ron", badEmail, PASSWORD)).status(), badEmail);
    }
    for (String badPassword : new String[] { "", "short1", "nodigitshere", "12345678",
        "a1" + "x".repeat(71) }) {
      Response refused = ui.post(null, "/api/users/register",
          new RegisterRequest("Pat", "Ron", uniqueEmail("weak"), badPassword));
      refused.expect(400);
      assertFalse(refused.json().path("message").asText().isBlank());
    }
    assertEquals(400, ui.post(null, "/api/users/register",
        new RegisterRequest(" ", "Ron", uniqueEmail("nameless"), PASSWORD)).status());
    assertEquals(400, ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "R".repeat(51), uniqueEmail("long-name"), PASSWORD)).status());

    // Addresses are stored trimmed and lower-cased.
    String email = uniqueEmail("tidy");
    JsonNode auth = ui.post(null, "/api/users/register",
        new RegisterRequest(" Pat ", " Ron ", " " + email.toUpperCase(java.util.Locale.ROOT), PASSWORD))
        .expect(200).json();
    assertEquals(email, auth.path("emailAddress").asText());
    JsonNode profile = ui.get(auth.path("token").asText(), "/api/users/me").expect(200).json();
    assertEquals("Pat", profile.path("firstName").asText());

    String token = auth.path("token").asText();
    ui.post(token, "/api/users/change-password", new ChangePasswordRequest(PASSWORD, ""))
        .expect(400);
    ui.put(token, "/api/users/me", new UpdateProfileRequest("Patricia", "  ")).expect(400);
    assertEquals("Pat", ui.get(token, "/api/users/me").expect(200).json().path("firstName")
        .asText(), "a refused update changes nothing");
  }

  @Test
  void anUnknownSongLibraryId_isNotFound() {

    assertEquals(404, ui.get(null, libraryPath("albums/999999")).status());
    assertEquals(404, ui.get(null, libraryPath("artists/999999")).status());
    assertEquals(404, ui.get(null, libraryPath("artistByAlbum/999999")).status());
    assertEquals(404, ui.get(null, libraryPath("albums/999999/coverArt")).status());
    assertEquals(404, ui.get(null, libraryPath("songs/999999/1")).status());
    assertEquals(404, ui.get(null, "/api/locations/999999/song-library/popular").status(),
        "an unknown location");
  }

  @Test
  void aNewAccount_hasMyFavorites_beforeEverOpeningTheHomeScreen() {

    JsonNode song = songsByDifferentArtists(1).get(0);
    String token = registerWithCredits(uniqueEmail("deep-link"), "pkg-7");

    assertEquals(List.of(MY_FAVORITES), list(ui.get(token, "/api/users/playlists").expect(200)
        .json()).stream().map(p -> p.path("name").asText()).toList());
    ui.post(token, "/api/users/playlists/favorites/songs", songRef(song)).expect(204);
    assertEquals(List.of(key(song)), keys(favorites(token)));
  }

  @Test
  void favoritingASongTwice_keepsOneFavorite() {

    JsonNode song = songsByDifferentArtists(1).get(0);
    String token = registerWithCredits(uniqueEmail("double-tap"), "pkg-7");

    ui.post(token, "/api/users/playlists/favorites/songs", songRef(song)).expect(204);
    ui.post(token, "/api/users/playlists/favorites/songs", songRef(song)).expect(204);
    assertEquals(List.of(key(song)), keys(favorites(token)));
    assertEquals(1, ui.get(token, "/api/users/playlists").expect(200).json().get(0)
        .path("songCount").asInt());

    ui.delete(token, "/api/users/playlists/favorites/songs", songRef(song)).expect(204);
    assertTrue(favorites(token).isEmpty());

    // An ordinary playlist may hold a song more than once, as a music playlist can.
    ui.post(token, "/api/users/playlists", Map.of("playlistName", "Encore")).expect(204);
    ui.post(token, "/api/users/playlists/{name}/songs", songRef(song), "Encore").expect(204);
    ui.post(token, "/api/users/playlists/{name}/songs", songRef(song), "Encore").expect(204);
    assertEquals(List.of(key(song), key(song)), keys(ui.get(token,
        "/api/users/playlists/{name}/songIdentifiers", "Encore").expect(200).json()));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────

  /** A new patron holding the credits of one Add Funds purchase of {@code packageId}. */
  private String registerWithCredits(String email, String packageId) {
    String token = ui.post(null, "/api/users/register",
        new RegisterRequest("Pat", "Ron", email, PASSWORD)).expect(200).json()
        .path("token").asText();
    ui.post(token, "/api/users/add-funds", new AddFundsRequest(packageId, "fake-nonce"))
        .expect(200);
    return token;
  }

  private int balance(String token) {
    return ui.get(token, "/api/users/me").expect(200).json().path("numCredits").asInt();
  }

  private List<String> searchHistory(String token) {
    return texts(ui.get(token, "/api/users/search-history").expect(200).json());
  }

  private JsonNode favorites(String token) {
    return ui.get(token, "/api/users/playlists/favorites/songs").expect(200).json();
  }

  /** Where GET /api/users/playlists/{name}/coverArt redirects to. */
  private String playlistCoverArt(String token, String playlistName) {
    Response response = ui.get(token, "/api/users/playlists/{name}/coverArt", playlistName)
        .expect(302);
    return response.raw().getHeader(HttpHeaders.LOCATION);
  }

  private JsonNode queuedSongs() {
    return ui.get(null, queuePath("queuedSongs")).expect(200).json();
  }

  /** Mirrors app.js's queueAddCost(), priced from /api/users/pricing-config. */
  private int queueAddCost(int priority, boolean priorityPlay) {
    int swingCost = priorityPlay ? priority * pricing.path("priorityCostMultiplier").asInt() : 1;
    return swingCost * pricing.path("webCostMultiplier").asInt();
  }

  /** Mirrors app.js's queueActionCost(). */
  private int queueActionCost(int priority) {
    return Math.max(1, priority * 3) * pricing.path("webCostMultiplier").asInt();
  }

  /** Credits' dollar value at this location's kiosk rate, as the server reports balanceUsd. */
  private BigDecimal usd(int credits) {
    return BigDecimal.valueOf(credits).divide(
        BigDecimal.valueOf(pricing.path("creditsPerDollar").asInt()), 2, RoundingMode.HALF_UP);
  }

  /**
   * {@code count} songs, each by a different artist where the library allows -- so queueing them
   * never trips the consecutive-plays-by-one-artist rule -- and none the queue's rules currently
   * refuse. (The queue is empty, but another test context sharing the test data dir may have
   * played a song within the minimum time between plays.)
   */
  private List<JsonNode> songsByDifferentArtists(int count) {

    List<JsonNode> songs = new ArrayList<>(pageThroughPopular("songPage", "songs"));
    try {
      asSystem(() -> songs.removeIf(song -> songQueueService.isSongEligibleForQueue(locationId,
          song.path("albumId").asInt(), song.path("songId").asInt(), 1) != null));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    List<JsonNode> chosen = new ArrayList<>();
    Set<String> artists = new HashSet<>();
    for (JsonNode song : songs) {
      if (chosen.size() < count && artists.add(song.path("artistName").asText())) {
        chosen.add(song);
      }
    }
    for (JsonNode song : songs) {
      if (chosen.size() < count && !chosen.contains(song)) {
        chosen.add(song);
      }
    }
    assertEquals(count, chosen.size(), "the fixture library is too small");
    assertTrue(artists.size() >= Math.min(count, 3), "artists: " + artists);
    return chosen;
  }

  /** Hot Here → See All's infinite scroll: one category, page by page, until a page is empty. */
  private List<JsonNode> pageThroughPopular(String pageParameter, String resultField) {

    List<JsonNode> items = new ArrayList<>();
    for (int page = 0; page < 50; page++) {
      JsonNode result = ui.get(null, libraryPath("popular") + "?" + pageParameter + "=" + page)
          .expect(200).json();
      JsonNode pageItems = result.path(resultField);
      if (pageItems.isEmpty()) {
        return items;
      }
      items.addAll(list(pageItems));
    }
    fail("Paging " + resultField + " never reached an empty page");
    return items;
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

  /** The body the song popup's Play / Play Next buttons send. */
  private static Map<String, Object> addSongBody(JsonNode song, int priority,
      boolean priorityPlay) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("albumId", song.path("albumId").asInt());
    body.put("songId", song.path("songId").asInt());
    body.put("priority", priority);
    body.put("priorityPlay", priorityPlay);
    return body;
  }

  /** The body the favorite / add-to-playlist actions send. */
  private Map<String, Object> songRef(JsonNode song) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("locationId", locationId);
    body.put("albumId", song.path("albumId").asInt());
    body.put("songId", song.path("songId").asInt());
    return body;
  }

  /** The body the Song Queue screen's up/down/remove buttons and Edit Track Order send. */
  private static Map<String, Object> moveBody(JsonNode song) {
    return Map.of("albumId", song.path("albumId").asInt(), "songId", song.path("songId").asInt());
  }

  private static void assertMoney(BigDecimal expected, BigDecimal actual) {
    assertEquals(0, expected.compareTo(actual), "expected $" + expected + " but was $" + actual);
  }

  @FunctionalInterface
  private interface SystemCall {
    void run() throws Exception;
  }

  /**
   * Runs the operator's own setup as the system principal, as the kiosk's background threads do.
   * The context is cleared straight afterwards so it can never leak into a request made on this
   * thread -- every request must authenticate (or not) exactly as the UI's would.
   */
  private static void asSystem(SystemCall call) throws Exception {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(SystemPrincipal.SystemAuthenticationToken.INSTANCE);
    SecurityContextHolder.setContext(context);
    try {
      call.run();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
