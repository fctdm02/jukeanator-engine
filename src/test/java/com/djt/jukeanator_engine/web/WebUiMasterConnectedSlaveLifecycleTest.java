package com.djt.jukeanator_engine.web;

import static com.djt.jukeanator_engine.web.WebUiTestClient.key;
import static com.djt.jukeanator_engine.web.WebUiTestClient.keys;
import static com.djt.jukeanator_engine.web.WebUiTestClient.list;
import static com.djt.jukeanator_engine.web.WebUiTestClient.uniqueEmail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.djt.jukeanator_engine.FakePaymentGatewayConfiguration;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.BackgroundMusicService;
import com.djt.jukeanator_engine.domain.common.exception.ResourceNotFoundException;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.location.client.SerializedSendWebSocketClient;
import com.djt.jukeanator_engine.domain.location.dto.CommandEnvelope;
import com.djt.jukeanator_engine.domain.location.dto.CommandReplyDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationEventMessage;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.location.service.ConnectedSlaveRegistry;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.config.SongQueueProperties;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.CheckSongsEligibilityRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueChangedEvent;
import com.djt.jukeanator_engine.domain.songqueue.exception.SongNotEligibleException;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueServiceImpl;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.web.WebUiTestClient.Response;
import com.djt.jukeanator_engine.web.event.LocationTopics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Web/Mobile UI's production path end to end: patrons on master, queueing at a bar whose
 * jukebox (a slave) is connected over {@code /ws-slave}. Covers what neither the standalone nor the
 * offline-master lifecycle tests can:
 * <ul>
 * <li>every queue operation forwarded to the slave and answered by it -- add, priority play,
 * playlist play, eligibility, move, remove, highest priority, now playing;</li>
 * <li>the slave's own refusals (a song already queued or just played, a song it does not have)
 * reaching the patron as the same 409/404 a standalone jukebox gives, never charged;</li>
 * <li>each spend charged on master and pushed to the slave's ledger -- the bar owner's revenue;</li>
 * <li>live updates over real {@code /ws} STOMP connections -- the location's queue and now-playing
 * relayed from the slave, and each patron's own credits and recent plays delivered to that patron
 * only.</li>
 * </ul>
 *
 * <p>The master is real (a random port, the JPA repositories, real security). The slave is
 * simulated, but speaks the real protocol ({@code SlaveConnectionManager}'s CONNECT, command and
 * event frames) and runs every command against a real {@link SongQueueServiceImpl} over a small
 * in-memory library, so the slave-side queue rules are production's. Patrons call the REST API as
 * the UI does (through {@link MockMvc}, the same filter chain) and hold real STOMP connections to
 * {@code /ws} with their JWT, as the UI does.
 *
 * <p>Requires the live MySQL {@code jukeanator_test} database, like the other master-mode
 * integration tests, and a real local port. Locations and patrons are unique per run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.mode=master", "app.repository-type=jpa" })
@Import(FakePaymentGatewayConfiguration.class)
class WebUiMasterConnectedSlaveLifecycleTest {

  private static final long WAIT_MILLIS = 10_000L;
  private static final String PASSWORD = "secret123";

  /** The bar's library: four songs by four different artists. */
  private static final int SONG_A_ALBUM = 501, SONG_A = 9001;
  private static final int SONG_B_ALBUM = 502, SONG_B = 9002;
  private static final int SONG_C_ALBUM = 503, SONG_C = 9003;
  private static final int SONG_D_ALBUM = 504, SONG_D = 9004;

  @LocalServerPort
  private int port;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtUtil jwtUtil;

  @Autowired
  private ConnectedSlaveRegistry connectedSlaveRegistry;

  @TempDir
  Path slaveDataDir;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<StompSession> sessions = new CopyOnWriteArrayList<>();

  private WebUiTestClient ui;
  private Integer locationId;
  private SimulatedSlave slave;
  private JsonNode pricing;

  @BeforeEach
  void setUp() throws Exception {

    ui = new WebUiTestClient(mockMvc);

    // ── An admin provisions the bar; its jukebox connects ────────────────────────────────
    String adminToken = jwtUtil.generateToken(uniqueEmail("admin"), UserRole.ROLE_ADMIN.name());
    JsonNode provisioned = ui.post(adminToken, "/api/locations",
        new RegisterLocationRequest("Connected Tavern " + System.nanoTime(), 42.33, -83.04))
        .expect(200).json();
    locationId = provisioned.path("locationId").asInt();

    slave = new SimulatedSlave(locationId, slaveDataDir);
    slave.connect(provisioned.path("apiKey").asText());
    awaitTrue("master to register the slave", () -> connectedSlaveRegistry.isConnected(locationId));

    pricing = ui.get(null, "/api/users/pricing-config?locationId={locationId}", locationId)
        .expect(200).json();
  }

  @AfterEach
  void tearDown() {
    for (StompSession session : sessions) {
      if (session.isConnected()) {
        session.disconnect();
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // A patron's night at a bar, through master
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void aPatron_queuesAtAConnectedBar_throughMaster_andEverythingArrivesLive() throws Exception {

    JsonNode bar = list(ui.get(null, "/api/locations").expect(200).json()).stream()
        .filter(l -> l.path("locationId").asInt() == locationId).findFirst().orElseThrow();
    assertTrue(bar.path("online").asBoolean(), "the picker shows the bar's jukebox online");

    // ── Sign up, open the live connection, buy credits ───────────────────────────────────
    String email = uniqueEmail("night-out");
    String token = register(email);
    PatronSocket socket = connectPatron(token);
    ui.post(token, "/api/users/add-funds", new AddFundsRequest("pkg-14", "fake-nonce")).expect(200);
    socket.await("/user/queue/credits", m -> m.path("numCredits").asInt() == 31);
    int balance = 31;

    // ── The song popup's checks, answered by the bar's jukebox ──────────────────────────
    JsonNode eligibility = ui.post(token, queuePath("checkSongsEligibility"),
        Map.of("songIdentifiers", List.of(songRef(SONG_A_ALBUM, SONG_A),
            songRef(SONG_B_ALBUM, SONG_B)), "priority", 1)).expect(200).json();
    assertTrue(eligibility.get(0).path("ineligibleReason").isNull(), eligibility.toString());
    assertTrue(eligibility.get(1).path("ineligibleReason").isNull(), eligibility.toString());
    assertEquals(2, ui.get(token, queuePath("highestPriority")).expect(200).json().asInt(),
        "an empty queue's next priority level, from the jukebox");

    // ── A normal play: queued on the jukebox, charged on master, revenue to the bar ──────
    int commandsBefore = slave.commandCount("addSongToQueue");
    JsonNode played = ui.post(token, queuePath("addSong"), addSongBody(SONG_A_ALBUM, SONG_A, 1,
        false)).expect(200).json();
    assertEquals(email, played.path("username").asText());
    assertEquals(commandsBefore + 1, slave.commandCount("addSongToQueue"));
    balance -= queueAddCost(1, false);
    assertEquals(balance, balance(token));
    assertEquals(List.of(SONG_A_ALBUM + "/" + SONG_A), slave.queueKeys());

    socket.await(LocationTopics.queue(locationId),
        m -> m.size() == 1 && key(m.get(0)).equals(SONG_A_ALBUM + "/" + SONG_A));
    socket.await("/user/queue/recent-plays", m -> key(m).equals(SONG_A_ALBUM + "/" + SONG_A));
    socket.await("/user/queue/credits", m -> m.path("numCredits").asInt() == 31 - queueAddCost(1,
        false));
    slave.awaitSpend(-queueAddCost(1, false));

    // ── The jukebox's own refusals: same status and message as standalone, never charged ──
    Response again = ui.post(token, queuePath("addSong"), addSongBody(SONG_A_ALBUM, SONG_A, 1,
        false));
    again.expect(409);
    assertEquals("SongNotEligibleException", again.error());
    assertTrue(again.json().path("message").asText().contains("is already in the queue"),
        again.body());
    Response noSuchSong = ui.post(token, queuePath("addSong"), addSongBody(SONG_A_ALBUM, 4242, 1,
        false));
    noSuchSong.expect(404);
    assertEquals("ResourceNotFoundException", noSuchSong.error());
    assertEquals(balance, balance(token), "a refused play is never charged");
    assertEquals(1, slave.queueKeys().size());

    // ── A priority play jumps the queue ──────────────────────────────────────────────────
    int priority = ui.get(token, queuePath("highestPriority")).expect(200).json().asInt();
    assertEquals(2, priority);
    ui.post(token, queuePath("addSong"), addSongBody(SONG_B_ALBUM, SONG_B, priority, true))
        .expect(200);
    balance -= queueAddCost(priority, true);
    assertEquals(balance, balance(token));
    assertEquals(List.of(SONG_B_ALBUM + "/" + SONG_B, SONG_A_ALBUM + "/" + SONG_A),
        slave.queueKeys());

    // ── Playing a playlist: one batch to the jukebox, each song charged as a normal play ──
    JsonNode batch = ui.post(token, queuePath("addMultipleSongs"), Map.of("songIdentifiers",
        List.of(songRef(SONG_C_ALBUM, SONG_C), songRef(SONG_D_ALBUM, SONG_D)), "priority", 1))
        .expect(200).json();
    assertEquals(List.of(SONG_C_ALBUM + "/" + SONG_C, SONG_D_ALBUM + "/" + SONG_D), keys(batch));
    balance -= 2 * queueAddCost(1, false);
    assertEquals(balance, balance(token));
    socket.await("/user/queue/recent-plays", m -> key(m).equals(SONG_D_ALBUM + "/" + SONG_D));
    assertEquals(List.of(SONG_B_ALBUM + "/" + SONG_B, SONG_A_ALBUM + "/" + SONG_A,
        SONG_C_ALBUM + "/" + SONG_C, SONG_D_ALBUM + "/" + SONG_D), keys(
            ui.get(null, queuePath("queuedSongs")).expect(200).json()));

    // ── The Song Queue screen: move and remove, priced from the jukebox's queue ──────────
    ui.post(token, queuePath("moveSongUpInQueue"), moveBody(SONG_D_ALBUM, SONG_D)).expect(200);
    balance -= queueActionCost(1);
    ui.post(token, queuePath("removeSongDownFromQueue"), moveBody(SONG_C_ALBUM, SONG_C))
        .expect(200);
    balance -= queueActionCost(1);
    assertEquals(balance, balance(token));
    assertEquals(List.of(SONG_B_ALBUM + "/" + SONG_B, SONG_A_ALBUM + "/" + SONG_A,
        SONG_D_ALBUM + "/" + SONG_D), slave.queueKeys());
    socket.await(LocationTopics.queue(locationId), m -> m.size() == 3
        && key(m.get(2)).equals(SONG_D_ALBUM + "/" + SONG_D));

    // ── The jukebox plays the next song: Now Playing updates live, and it can't be replayed ──
    slave.playNext();
    socket.await(LocationTopics.nowPlaying(locationId),
        m -> key(m.path("song")).equals(SONG_B_ALBUM + "/" + SONG_B));
    assertEquals(SONG_B_ALBUM + "/" + SONG_B,
        key(ui.get(null, playerPath("nowPlayingSong")).expect(200).json()));
    Response replay = ui.post(token, queuePath("addSong"), addSongBody(SONG_B_ALBUM, SONG_B, 1,
        false));
    replay.expect(409);
    assertTrue(replay.json().path("message").asText().contains("has already been played"),
        replay.body());

    // ── A play the balance does not cover is refused before it reaches the jukebox ───────
    int addsBefore = slave.commandCount("addSongToQueue");
    int unaffordable = balance / pricing.path("priorityCostMultiplier").asInt()
        / pricing.path("webCostMultiplier").asInt() + 1;
    ui.post(token, queuePath("addSong"), addSongBody(SONG_C_ALBUM, SONG_C, unaffordable, true))
        .expect(402);
    assertEquals(addsBefore, slave.commandCount("addSongToQueue"));
    assertEquals(balance, balance(token));

    // ── The bar owner's accounting matches what the patron spent, on master and the slave ─
    int spent = 31 - balance;
    String adminToken = jwtUtil.generateToken(uniqueEmail("owner"), UserRole.ROLE_ADMIN.name());
    Instant now = Instant.now();
    JsonNode ledger = ui.get(adminToken,
        "/api/locations/{locationId}/credit-ledger?from={from}&to={to}", locationId,
        now.minusSeconds(3600), now.plusSeconds(3600)).expect(200).json();
    assertEquals(-spent, list(ledger).stream().mapToInt(u -> u.path("amount").asInt()).sum());
    awaitTrue("the slave to receive every spend",
        () -> slave.spendTotal() == -spent && slave.spendCount() == ledger.size());

    // ── The jukebox goes offline: the patron is told so, and never charged ──────────────
    slave.disconnect();
    awaitTrue("master to register the disconnect",
        () -> !connectedSlaveRegistry.isConnected(locationId));
    ui.post(token, queuePath("addSong"), addSongBody(SONG_C_ALBUM, SONG_C, 1, false)).expect(503);
    assertEquals(balance, balance(token));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Live updates reach the right connections only
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void eachPatronsCreditsAndRecentPlays_reachThatPatronOnly_whileTheBarsQueueReachesEveryone()
      throws Exception {

    String alice = uniqueEmail("alice");
    String bob = uniqueEmail("bob");
    String aliceToken = register(alice);
    String bobToken = register(bob);

    PatronSocket aliceSocket = connectPatron(aliceToken);
    PatronSocket bobSocket = connectPatron(bobToken);
    PatronSocket signedOut = connectPatron(null);
    PatronSocket forged = connectPatron(aliceToken + "x");

    // Alice buys credits and queues a song.
    ui.post(aliceToken, "/api/users/add-funds", new AddFundsRequest("pkg-7", "fake-nonce"))
        .expect(200);
    aliceSocket.await("/user/queue/credits", m -> m.path("numCredits").asInt() == 13);
    ui.post(aliceToken, queuePath("addSong"), addSongBody(SONG_A_ALBUM, SONG_A, 1, false))
        .expect(200);
    aliceSocket.await("/user/queue/recent-plays",
        m -> key(m).equals(SONG_A_ALBUM + "/" + SONG_A));

    // Everyone watching the bar sees its queue change...
    for (PatronSocket watcher : List.of(aliceSocket, bobSocket, signedOut, forged)) {
      watcher.await(LocationTopics.queue(locationId), m -> m.size() == 1);
    }

    // ...then Bob buys credits too.
    ui.post(bobToken, "/api/users/add-funds", new AddFundsRequest("pkg-28", "fake-nonce"))
        .expect(200);
    bobSocket.await("/user/queue/credits", m -> m.path("numCredits").asInt() == 72);

    // Give any misrouted message time to arrive before checking nothing else did.
    Thread.sleep(1_000);
    assertEquals(List.of(13, 13 - queueAddCost(1, false)),
        aliceSocket.to("/user/queue/credits").stream().map(m -> m.path("numCredits").asInt())
            .toList(), "Alice sees only her own balance");
    assertEquals(List.of(72), bobSocket.to("/user/queue/credits").stream()
        .map(m -> m.path("numCredits").asInt()).toList(), "Bob sees only his own balance");
    assertTrue(bobSocket.to("/user/queue/recent-plays").isEmpty(),
        "Bob never sees Alice's plays");
    for (PatronSocket anonymous : List.of(signedOut, forged)) {
      assertTrue(anonymous.to("/user/queue/credits").isEmpty(), "no token, no balance");
      assertTrue(anonymous.to("/user/queue/recent-plays").isEmpty(), "no token, no plays");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private String register(String email) {
    return ui.post(null, "/api/users/register", new RegisterRequest("Pat", "Ron", email,
        PASSWORD)).expect(200).json().path("token").asText();
  }

  private int balance(String token) {
    return ui.get(token, "/api/users/me").expect(200).json().path("numCredits").asInt();
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

  private String queuePath(String endpoint) {
    return "/api/locations/" + locationId + "/song-queue/" + endpoint;
  }

  private String playerPath(String endpoint) {
    return "/api/locations/" + locationId + "/song-player/" + endpoint;
  }

  private Map<String, Object> songRef(int albumId, int songId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("locationId", locationId);
    body.put("albumId", albumId);
    body.put("songId", songId);
    return body;
  }

  private static Map<String, Object> addSongBody(int albumId, int songId, int priority,
      boolean priorityPlay) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("albumId", albumId);
    body.put("songId", songId);
    body.put("priority", priority);
    body.put("priorityPlay", priorityPlay);
    return body;
  }

  private static Map<String, Object> moveBody(int albumId, int songId) {
    return Map.of("albumId", albumId, "songId", songId);
  }

  private static void awaitTrue(String description, BooleanSupplier condition)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + WAIT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(50);
    }
    fail("Timed out waiting for " + description);
  }

  private WebSocketStompClient stompClient() {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setMessageConverter(new JacksonJsonMessageConverter());
    return client;
  }

  // ── A patron's live connection, as app.js opens it ───────────────────────────────────

  /**
   * Connects to {@code /ws} (its raw-WebSocket SockJS transport) as app.js does -- the JWT in the
   * CONNECT frame's {@code token} header, or none when signed out -- and subscribes to everything
   * app.js subscribes to.
   */
  private PatronSocket connectPatron(String token) throws Exception {

    StompHeaders connectHeaders = new StompHeaders();
    if (token != null) {
      connectHeaders.set("token", token);
    }
    StompSession session = stompClient().connectAsync("ws://localhost:" + port + "/ws/websocket",
        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
        .get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
    sessions.add(session);

    PatronSocket socket = new PatronSocket();
    for (String destination : List.of(LocationTopics.queue(locationId),
        LocationTopics.nowPlaying(locationId), "/user/queue/credits",
        "/user/queue/recent-plays")) {
      session.subscribe(destination, socket.handlerFor(destination));
    }
    // A SUBSCRIBE frame is processed asynchronously; let the broker register them all.
    Thread.sleep(500);
    return socket;
  }

  /** What one live connection has received, per destination. */
  private final class PatronSocket {

    private record Received(String destination, JsonNode payload) {}

    private final List<Received> received = new CopyOnWriteArrayList<>();

    StompFrameHandler handlerFor(String destination) {
      return new StompFrameHandler() {
        @Override
        public Type getPayloadType(StompHeaders headers) {
          return Object.class;
        }

        // The client hands an Object-typed frame over as its raw body bytes: parse them, exactly
        // as app.js's JSON.parse(frame.body) does.
        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
          try {
            JsonNode json = payload instanceof byte[] body ? objectMapper.readTree(body)
                : objectMapper.valueToTree(payload);
            received.add(new Received(destination, json));
          } catch (java.io.IOException e) {
            throw new IllegalStateException("Frame on " + destination + " is not JSON", e);
          }
        }
      };
    }

    List<JsonNode> to(String destination) {
      return received.stream().filter(r -> r.destination().equals(destination))
          .map(Received::payload).toList();
    }

    JsonNode await(String destination, Predicate<JsonNode> condition) throws InterruptedException {
      long deadline = System.currentTimeMillis() + WAIT_MILLIS;
      while (System.currentTimeMillis() < deadline) {
        for (JsonNode payload : to(destination)) {
          if (condition.test(payload)) {
            return payload;
          }
        }
        Thread.sleep(25);
      }
      fail("No matching message on " + destination + "; received: " + received);
      return null;
    }
  }

  // ── The bar's jukebox ─────────────────────────────────────────────────────────────────

  /**
   * A slave as {@code SlaveConnectionManager} is one: it dials master's {@code /ws-slave} with its
   * API key, answers each command from {@code /user/queue/commands} on
   * {@code /location-command-reply} (re-raising nothing -- a refusal goes back as an error reply
   * carrying the exception's type), and forwards its queue and now-playing changes on
   * {@code /location-events}. Commands run against a real {@link SongQueueServiceImpl}.
   */
  private final class SimulatedSlave {

    private final Integer locationId;
    private final SongQueueServiceImpl songQueue;
    private final Map<String, AtomicInteger> commandCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<JsonNode> spends = new CopyOnWriteArrayList<>();
    private volatile StompSession session;
    private volatile SongDto nowPlaying;

    SimulatedSlave(Integer locationId, Path dataDir) throws Exception {

      this.locationId = locationId;
      RootFolderEntity library = buildLibrary();
      SongLibraryService songLibraryService = mock(SongLibraryService.class);
      when(songLibraryService.getOwnLocationId()).thenReturn(locationId);
      when(songLibraryService.getSongLibraryRoot(locationId)).thenReturn(library);
      BackgroundMusicService backgroundMusic = mock(BackgroundMusicService.class);
      when(backgroundMusic.isEnabled()).thenReturn(false);

      songQueue = new SongQueueServiceImpl(new SongQueueProperties(), songLibraryService,
          backgroundMusic, new SongQueueRepositoryFileSystemImpl(dataDir.toString(),
              songLibraryService),
          this::publishLocally, Optional.empty(), dataDir.resolve("RecentSongPlays.json"));
    }

    void connect(String apiKey) throws Exception {
      StompHeaders connectHeaders = new StompHeaders();
      connectHeaders.set("location-id", String.valueOf(locationId));
      connectHeaders.set("location-api-key", apiKey);
      // The production slave's client: it sends command replies (STOMP thread) and events (this
      // thread) concurrently, as a real slave does -- see SerializedSendWebSocketClient.
      WebSocketStompClient slaveClient =
          new WebSocketStompClient(new SerializedSendWebSocketClient());
      slaveClient.setMessageConverter(new JacksonJsonMessageConverter());
      session = slaveClient.connectAsync("ws://localhost:" + port + "/ws-slave",
          new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
          .get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
      sessions.add(session);
      session.subscribe("/user/queue/commands", new StompFrameHandler() {
        @Override
        public Type getPayloadType(StompHeaders headers) {
          return CommandEnvelope.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
          session.send("/location-command-reply", execute((CommandEnvelope) payload));
        }
      });
      Thread.sleep(500);
    }

    void disconnect() {
      session.disconnect();
    }

    /** The jukebox starts the next song, and tells master, as SongPlayerServiceImpl does. */
    void playNext() {
      SongQueueEntryDto next = songQueue.dequeueNextSong();
      nowPlaying = next.song();
      session.send("/location-events",
          new LocationEventMessage(LocationTopics.NOW_PLAYING, nowPlaying));
    }

    List<String> queueKeys() {
      return songQueue.getQueuedSongs(locationId).stream()
          .map(e -> e.song().albumId() + "/" + e.song().songId()).toList();
    }

    int commandCount(String commandType) {
      return commandCounts.computeIfAbsent(commandType, k -> new AtomicInteger()).get();
    }

    int spendTotal() {
      return spends.stream().mapToInt(s -> s.path("amount").asInt()).sum();
    }

    int spendCount() {
      return spends.size();
    }

    void awaitSpend(int amount) throws InterruptedException {
      awaitTrue("the slave to receive a spend of " + amount,
          () -> spends.stream().anyMatch(s -> s.path("amount").asInt() == amount
              && s.path("locationId").asInt() == locationId));
    }

    /** Forwards the slave's own queue changes to master, as SlaveConnectionManager does. */
    private void publishLocally(Object event) {
      if (event instanceof SongQueueChangedEvent changed && session != null) {
        session.send("/location-events",
            new LocationEventMessage(LocationTopics.QUEUE, changed.queuedSongs()));
      }
    }

    private CommandReplyDto execute(CommandEnvelope envelope) {
      commandCounts.computeIfAbsent(envelope.commandType(), k -> new AtomicInteger())
          .incrementAndGet();
      try {
        return new CommandReplyDto(envelope.correlationId(), true,
            dispatch(envelope.commandType(), envelope.payload()), null);
      } catch (SongNotEligibleException | ResourceNotFoundException e) {
        return new CommandReplyDto(envelope.correlationId(), false, null, e.getMessage(),
            e.getClass().getSimpleName());
      } catch (Exception e) {
        return new CommandReplyDto(envelope.correlationId(), false, null, e.getMessage(),
            e.getClass().getSimpleName());
      }
    }

    /** The slice of SlaveConnectionManager.dispatch the Web/Mobile UI reaches. */
    private Object dispatch(String commandType, Object payload) {
      switch (commandType) {
        case "getHighestPriority":
          return songQueue.getHighestPriority(locationId);
        case "getQueuedSongs":
          return songQueue.getQueuedSongs(locationId);
        case "isSongEligibleForQueue": {
          JsonNode p = objectMapper.valueToTree(payload);
          return songQueue.isSongEligibleForQueue(locationId, p.path("albumId").asInt(),
              p.path("songId").asInt(), p.path("priority").asInt());
        }
        case "checkSongsEligibility": {
          CheckSongsEligibilityRequest p =
              objectMapper.convertValue(payload, CheckSongsEligibilityRequest.class);
          return songQueue.checkSongsEligibility(locationId, p.songIdentifiers(), p.priority());
        }
        case "addSongToQueue":
          return songQueue.addSongToQueue(locationId,
              objectMapper.convertValue(payload, AddSongToQueueRequest.class));
        case "addMultipleSongsToQueue":
          return songQueue.addMultipleSongsToQueue(locationId,
              objectMapper.convertValue(payload, AddMultipleSongsToQueueRequest.class));
        case "moveSongUpInQueue":
          return songQueue.moveSongUpInQueue(locationId,
              objectMapper.convertValue(payload, ChangeSongQueueRequest.class));
        case "moveSongDownInQueue":
          return songQueue.moveSongDownInQueue(locationId,
              objectMapper.convertValue(payload, ChangeSongQueueRequest.class));
        case "removeSongDownFromQueue":
          return songQueue.removeSongDownFromQueue(locationId,
              objectMapper.convertValue(payload, ChangeSongQueueRequest.class));
        case "getNowPlayingSong":
          return nowPlaying;
        case "recordMobileCreditUsage":
          spends.add(objectMapper.valueToTree(payload));
          return null;
        default:
          throw new IllegalArgumentException("Unknown commandType: " + commandType);
      }
    }
  }

  /** Four songs, each by a different artist, so queueing never trips the consecutive-artist rule. */
  private static RootFolderEntity buildLibrary() throws Exception {

    RootFolderEntity root = new RootFolderEntity("/fixture/connected-tavern");
    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(1);
    root.addChildFolder(genre);

    int[][] songs = { { SONG_A_ALBUM, SONG_A }, { SONG_B_ALBUM, SONG_B }, { SONG_C_ALBUM, SONG_C },
        { SONG_D_ALBUM, SONG_D } };
    for (int i = 0; i < songs.length; i++) {
      String artistName = "Artist " + (char) ('A' + i);
      ArtistFolderEntity artist = new ArtistFolderEntity(genre, artistName);
      artist.setId(10 + i);
      genre.addChildFolder(artist);

      AlbumFolderEntity album = new AlbumFolderEntity(artist, "Album " + (char) ('A' + i));
      album.setId(songs[i][0]);
      album.createCoverArtEntity();
      artist.addChildFolder(album);

      SongFileEntity song = new SongFileEntity(album, "01 - Song " + (char) ('A' + i) + ".mp3");
      song.setId(songs[i][1]);
      song.setArtistName(artistName);
      song.setSongName("Song " + (char) ('A' + i));
      song.setTrackNumber(1);
      song.setNumPlays(0);
      album.addChildSong(song);
    }
    root.initialize();
    return root;
  }
}
