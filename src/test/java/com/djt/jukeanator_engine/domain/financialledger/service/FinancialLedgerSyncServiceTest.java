package com.djt.jukeanator_engine.domain.financialledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.MasterSyncOutboxEntry;
import com.djt.jukeanator_engine.domain.financialledger.event.LocalFinancialTransactionRecordedEvent;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Covers {@link FinancialLedgerSyncService}, the slave-side half of the financial-ledger mirror,
 * against a real (JDK built-in, no new test dependency) local HTTP server standing in for master
 * -- {@code FinancialLedgerSyncService} builds its own {@code RestClient} internally from {@link
 * AppProperties} (mirroring {@code LibrarySyncService}'s own shape), so a real listener socket is
 * the simplest way to observe exactly what it sends. {@link FinancialLedgerService} is mocked: the
 * outbox contents and what gets acknowledged are its concern, covered by {@code
 * FinancialLedgerServiceTest}.
 *
 * @author tmyers
 */
class FinancialLedgerSyncServiceTest {

  private static final Integer LOCATION_ID = Integer.valueOf(77);
  private static final String API_KEY = "secret-key";
  private static final String MOBILE_PATH =
      "/api/locations/" + LOCATION_ID + "/financial-ledger/mobile-credit-usage";

  private static final ObjectMapper MAPPER = ObjectMappers.create();

  private HttpServer server;
  private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
  private final Map<String, Integer> statusByPath = new ConcurrentHashMap<>();
  private volatile String mobileResponseJson = "[]";

  private FinancialLedgerService financialLedgerService;
  private SongLibraryService songLibraryService;
  private FinancialLedgerSyncService service;

  private final Instant openPeriodStart = Instant.parse("2026-09-01T00:00:00Z");

  private record CapturedRequest(String method, String path, String query, String locationIdHeader,
      String apiKeyHeader, Map<String, Object> body) {
  }

  @BeforeEach
  void startFakeMaster() throws IOException {

    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      byte[] requestBody = exchange.getRequestBody().readAllBytes();
      requests.add(new CapturedRequest(exchange.getRequestMethod(), path,
          exchange.getRequestURI().getRawQuery(),
          exchange.getRequestHeaders().getFirst("location-id"),
          exchange.getRequestHeaders().getFirst("location-api-key"),
          requestBody.length == 0 ? Map.of()
              : MAPPER.readValue(requestBody, new TypeReference<Map<String, Object>>() {})));

      int status = statusByPath.getOrDefault(path, 0);
      if (status != 0) {
        exchange.sendResponseHeaders(status, -1);
      } else if ("GET".equals(exchange.getRequestMethod())) {
        byte[] json = mobileResponseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.length);
        exchange.getResponseBody().write(json);
      } else {
        exchange.sendResponseHeaders(204, -1);
      }
      exchange.close();
    });
    server.start();

    financialLedgerService = mock(FinancialLedgerService.class);
    songLibraryService = mock(SongLibraryService.class);
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    when(financialLedgerService.getOpenPeriodStartDate()).thenReturn(openPeriodStart);
    when(financialLedgerService.getPendingMasterSync()).thenReturn(List.of());

    service = newService("http://localhost:" + server.getAddress().getPort());
  }

  private FinancialLedgerSyncService newService(String masterInstanceUrl) {

    AppProperties appProperties = new AppProperties();
    appProperties.setMasterInstanceUrl(masterInstanceUrl);
    appProperties.setLocationApiKey(API_KEY);
    return new FinancialLedgerSyncService(appProperties, financialLedgerService,
        songLibraryService);
  }

  @AfterEach
  void stopFakeMaster() {
    service.shutdown();
    server.stop(0);
  }

  // ── slave-to-master outbox ───────────────────────────────────────────────

  @Test
  void sweep_pushesEachPendingEntryToItsEndpoint_withLocationHeaders_andAcknowledgesThem() {

    MasterSyncOutboxEntry cash = new MasterSyncOutboxEntry(MasterSyncOutboxEntry.Kind.CASH,
        LOCATION_ID, Integer.valueOf(42), new LocalTransactionSyncDto(42, 1, Instant.now()));
    MasterSyncOutboxEntry card = new MasterSyncOutboxEntry(
        MasterSyncOutboxEntry.Kind.CREDIT_CARD, LOCATION_ID, Integer.valueOf(43),
        new LocalTransactionSyncDto(43, 1, Instant.now()));
    MasterSyncOutboxEntry period = new MasterSyncOutboxEntry(
        MasterSyncOutboxEntry.Kind.SPLIT_PERIOD, LOCATION_ID, Integer.valueOf(44),
        new JukeboxSplitPeriodSyncDto(44, openPeriodStart.minusSeconds(3600),
            openPeriodStart.minusNanos(1), 50, new BigDecimal("2.00"), new BigDecimal("1.00"),
            BigDecimal.ZERO, new BigDecimal("3.00"), new BigDecimal("1.50"),
            new BigDecimal("1.50")));
    when(financialLedgerService.getPendingMasterSync()).thenReturn(List.of(cash, card, period));

    triggerSweep();

    verify(financialLedgerService, timeout(5000)).markSyncedToMaster(List.of(cash, card, period));

    CapturedRequest cashRequest = requestTo("/local-cash");
    assertEquals("POST", cashRequest.method());
    assertEquals(String.valueOf(LOCATION_ID), cashRequest.locationIdHeader());
    assertEquals(API_KEY, cashRequest.apiKeyHeader());
    assertEquals(42, cashRequest.body().get("sourceTransactionId"));
    assertEquals(43, requestTo("/local-credit-card").body().get("sourceTransactionId"));
    assertEquals(44, requestTo("/split-period").body().get("sourcePeriodId"));
  }

  @Test
  void sweep_leavesAnEntryMasterRejectedPending_withoutBlockingTheOnesBehindIt() {

    statusByPath.put("/api/locations/" + LOCATION_ID + "/financial-ledger/local-cash", 400);
    MasterSyncOutboxEntry rejected = new MasterSyncOutboxEntry(MasterSyncOutboxEntry.Kind.CASH,
        LOCATION_ID, Integer.valueOf(42), new LocalTransactionSyncDto(42, 1, Instant.now()));
    MasterSyncOutboxEntry accepted = new MasterSyncOutboxEntry(
        MasterSyncOutboxEntry.Kind.CREDIT_CARD, LOCATION_ID, Integer.valueOf(43),
        new LocalTransactionSyncDto(43, 1, Instant.now()));
    when(financialLedgerService.getPendingMasterSync()).thenReturn(List.of(rejected, accepted));

    triggerSweep();

    verify(financialLedgerService, timeout(5000)).markSyncedToMaster(List.of(accepted));
  }

  @Test
  void sweep_acknowledgesNothing_whenMasterIsUnreachable() throws Exception {

    service.shutdown();
    // A port nothing is listening on.
    int deadPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      deadPort = socket.getLocalPort();
    }
    service = newService("http://localhost:" + deadPort);

    MasterSyncOutboxEntry cash = new MasterSyncOutboxEntry(MasterSyncOutboxEntry.Kind.CASH,
        LOCATION_ID, Integer.valueOf(42), new LocalTransactionSyncDto(42, 1, Instant.now()));
    when(financialLedgerService.getPendingMasterSync()).thenReturn(List.of(cash));

    triggerSweep();

    verify(financialLedgerService, timeout(5000)).markSyncedToMaster(List.of());
    verify(financialLedgerService, never()).receiveMobileCreditUsage(any());
  }

  // ── master-to-slave mobile catch-up ──────────────────────────────────────

  @Test
  void sweep_pullsTheWholeOpenPeriodOnStartup_andStoresEverySpendMasterReturns() {

    // Even with spends already mirrored, the first sweep after start-up re-pulls the whole open
    // period -- a live push may have been missed while this slave was down.
    when(financialLedgerService.getLatestMobileCreditUsageTimestamp())
        .thenReturn(openPeriodStart.plusSeconds(86_400));
    mobileResponseJson = "[{\"userEmail\":\"alice@example.com\",\"locationId\":77,\"amount\":-4,"
        + "\"type\":\"QUEUE_ADD\",\"timestamp\":\"2026-09-02T10:00:00Z\",\"songAlbumId\":1,"
        + "\"songId\":2,\"resultingBalance\":6,\"syncId\":\"sync-a\"}]";

    triggerSweep();

    verify(financialLedgerService, timeout(5000)).receiveMobileCreditUsage(
        new UserSongCreditUsageDto("alice@example.com", LOCATION_ID, -4,
            UserSongCreditUsageType.QUEUE_ADD, Instant.parse("2026-09-02T10:00:00Z"), 1, 2, 6,
            "sync-a"));
    CapturedRequest pull = requestTo("/mobile-credit-usage");
    assertEquals("GET", pull.method());
    assertEquals(API_KEY, pull.apiKeyHeader());
    assertEquals(openPeriodStart, sinceOf(pull));
  }

  @Test
  void sweep_afterASuccessfulPull_onlyReachesBackTheOverlapWindowPastTheLatestMirroredSpend() {

    Instant latest = openPeriodStart.plusSeconds(86_400);
    when(financialLedgerService.getLatestMobileCreditUsageTimestamp()).thenReturn(latest);

    triggerSweep();
    awaitMobilePulls(1);
    triggerSweep();
    awaitMobilePulls(2);

    List<CapturedRequest> pulls = mobilePulls();
    assertEquals(openPeriodStart, sinceOf(pulls.get(0)));
    assertEquals(latest.minus(FinancialLedgerSyncService.MOBILE_CATCH_UP_OVERLAP),
        sinceOf(pulls.get(1)));
  }

  @Test
  void sweep_afterAFailedPull_goesBackToPullingTheWholeOpenPeriod() {

    Instant latest = openPeriodStart.plusSeconds(86_400);
    when(financialLedgerService.getLatestMobileCreditUsageTimestamp()).thenReturn(latest);

    triggerSweep();
    awaitMobilePulls(1);

    statusByPath.put(MOBILE_PATH, 503);
    triggerSweep();
    awaitMobilePulls(2);

    statusByPath.remove(MOBILE_PATH);
    triggerSweep();
    awaitMobilePulls(3);

    assertEquals(openPeriodStart, sinceOf(mobilePulls().get(2)),
        "A failed sweep may mean a missed live push -- the next one must re-pull the whole period");
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private void triggerSweep() {
    service.handleLocalFinancialTransactionRecordedEvent(new LocalFinancialTransactionRecordedEvent(
        LocalFinancialTransactionRecordedEvent.Kind.CASH, LOCATION_ID, Integer.valueOf(1), 1,
        Instant.now()));
  }

  private CapturedRequest requestTo(String pathSuffix) {
    return requests.stream()
        .filter(r -> r.path().endsWith(pathSuffix))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No request to *" + pathSuffix + " reached master"));
  }

  private List<CapturedRequest> mobilePulls() {
    return requests.stream().filter(r -> r.path().equals(MOBILE_PATH)).toList();
  }

  private void awaitMobilePulls(int count) {
    long deadline = System.currentTimeMillis() + 5000;
    while (mobilePulls().size() < count && System.currentTimeMillis() < deadline) {
      Thread.onSpinWait();
    }
    assertTrue(mobilePulls().size() >= count,
        "Expected " + count + " mobile catch-up pull(s) to reach the fake master within 5s -- "
            + "sweeps run on FinancialLedgerSyncService's own background executor");
  }

  private static Instant sinceOf(CapturedRequest request) {
    String query = URLDecoder.decode(request.query(), StandardCharsets.UTF_8);
    return Instant.parse(query.substring(query.indexOf("since=") + "since=".length()));
  }
}
