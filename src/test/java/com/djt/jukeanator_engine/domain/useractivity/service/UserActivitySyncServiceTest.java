package com.djt.jukeanator_engine.domain.useractivity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.useractivity.model.PendingUserActivity;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Covers {@link UserActivitySyncService}, the slave-side half of the user-activity mirror, against
 * a real (JDK built-in) local HTTP server standing in for master -- same approach as {@code
 * FinancialLedgerSyncServiceTest}. {@link UserActivityService} is mocked: which records are
 * pending, and what acknowledging them does, is the repositories' concern (see {@code
 * UserActivityRepositoryFileSystemImplTest}/{@code UserActivityRepositoryJpaImplTest}).
 *
 * @author tmyers
 */
class UserActivitySyncServiceTest {

  private static final Integer LOCATION_ID = Integer.valueOf(77);
  private static final String API_KEY = "secret-key";

  private static final ObjectMapper MAPPER = ObjectMappers.create();

  private HttpServer server;
  private volatile int responseStatus = 204;
  private final List<String> paths = new CopyOnWriteArrayList<>();
  private final List<String> apiKeyHeaders = new CopyOnWriteArrayList<>();
  private final List<List<Map<String, Object>>> bodies = new CopyOnWriteArrayList<>();

  private UserActivityService userActivityService;
  private UserActivitySyncService service;

  @BeforeEach
  void startFakeMaster() throws IOException {

    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      paths.add(exchange.getRequestURI().getPath());
      apiKeyHeaders.add(exchange.getRequestHeaders().getFirst("location-api-key"));
      bodies.add(MAPPER.readValue(exchange.getRequestBody().readAllBytes(),
          new TypeReference<List<Map<String, Object>>>() {}));
      exchange.sendResponseHeaders(responseStatus, -1);
      exchange.close();
    });
    server.start();

    userActivityService = mock(UserActivityService.class);
    SongLibraryService songLibraryService = mock(SongLibraryService.class);
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);

    AppProperties appProperties = new AppProperties();
    appProperties.setMasterInstanceUrl("http://localhost:" + server.getAddress().getPort());
    appProperties.setLocationApiKey(API_KEY);
    service = new UserActivitySyncService(appProperties, userActivityService, songLibraryService);
  }

  @AfterEach
  void stopFakeMaster() {
    service.shutdown();
    server.stop(0);
  }

  @Test
  void sweep_pushesPendingRecordsInOneBatch_andAcknowledgesThem() {

    // The second record was captured before this slave's own location was known.
    List<PendingUserActivity> pending =
        List.of(pending("1", LOCATION_ID, "a"), pending("2", null, "b"));
    when(userActivityService.getPendingMasterSync(anyInt())).thenReturn(pending, List.of());

    service.sweepNow();

    verify(userActivityService, timeout(5000)).markSyncedToMaster(pending);
    assertEquals(List.of("/api/locations/" + LOCATION_ID + "/user-activity/sync"), paths);
    assertEquals(API_KEY, apiKeyHeaders.get(0));
    assertEquals(List.of("a", "b"),
        bodies.get(0).stream().map(record -> record.get("activityId")).toList());
  }

  @Test
  void sweep_acknowledgesNothing_whenMasterRefusesTheBatch() {

    responseStatus = 500;
    when(userActivityService.getPendingMasterSync(anyInt()))
        .thenReturn(List.of(pending("1", LOCATION_ID, "a")));

    service.sweepNow();
    // Queued behind the first on the single sync thread -- once it starts, the first sweep
    // (including any acknowledgement it would have made) has fully finished.
    service.sweepNow();

    verify(userActivityService, timeout(5000).times(2)).getPendingMasterSync(anyInt());
    verify(userActivityService, never()).markSyncedToMaster(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void sweep_keepsDrainingFullBatches_untilTheOutboxIsEmpty() {

    List<PendingUserActivity> fullBatch = IntStream.range(0, UserActivitySyncService.BATCH_SIZE)
        .mapToObj(i -> pending(String.valueOf(i), LOCATION_ID, "id-" + i))
        .toList();
    List<PendingUserActivity> remainder = List.of(pending("x", LOCATION_ID, "last"));
    when(userActivityService.getPendingMasterSync(anyInt()))
        .thenReturn(fullBatch, remainder, List.of());

    service.sweepNow();

    verify(userActivityService, timeout(5000)).markSyncedToMaster(remainder);
    verify(userActivityService, times(1)).markSyncedToMaster(fullBatch);
    assertEquals(2, bodies.size(), "One POST per batch");
  }

  private static PendingUserActivity pending(String cursor, Integer locationId,
      String activityId) {
    return new PendingUserActivity(cursor, new UserActivityRecord(locationId,
        UserActivitySource.SWING_UI, "LOCAL", UserActivityType.TAB_NAVIGATION, Instant.now(),
        Map.of(), activityId));
  }
}
