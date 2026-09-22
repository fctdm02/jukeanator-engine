package com.djt.jukeanator_engine.domain.financialledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.djt.jukeanator_engine.domain.financialledger.event.LocalFinancialTransactionRecordedEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Covers {@link FinancialLedgerSyncService}, the slave-side half of the mirroring feature, against
 * a real (JDK built-in, no new test dependency) local HTTP server standing in for master --
 * {@code FinancialLedgerSyncService} builds its own {@code RestClient} internally from {@link
 * AppProperties} (mirroring {@code LibrarySyncService}'s own shape), so a real listener socket is
 * the simplest way to observe exactly what it sends, without changing production code just to make
 * it more mockable.
 *
 * @author tmyers
 */
class FinancialLedgerSyncServiceTest {

  private static final Integer LOCATION_ID = Integer.valueOf(77);
  private static final String API_KEY = "secret-key";

  private static final ObjectMapper MAPPER = ObjectMappers.create();

  private HttpServer server;
  private CountDownLatch requestReceived;
  private final AtomicReference<String> capturedPath = new AtomicReference<>();
  private final AtomicReference<String> capturedMethod = new AtomicReference<>();
  private final AtomicReference<String> capturedLocationIdHeader = new AtomicReference<>();
  private final AtomicReference<String> capturedApiKeyHeader = new AtomicReference<>();
  private final AtomicReference<Map<String, Object>> capturedBody = new AtomicReference<>();

  private FinancialLedgerSyncService service;

  @BeforeEach
  void startFakeMaster() throws IOException {

    requestReceived = new CountDownLatch(1);

    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      capturedPath.set(exchange.getRequestURI().getPath());
      capturedMethod.set(exchange.getRequestMethod());
      capturedLocationIdHeader.set(exchange.getRequestHeaders().getFirst("location-id"));
      capturedApiKeyHeader.set(exchange.getRequestHeaders().getFirst("location-api-key"));
      capturedBody.set(MAPPER.readValue(exchange.getRequestBody().readAllBytes(),
          new TypeReference<Map<String, Object>>() {}));
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
      requestReceived.countDown();
    });
    server.start();

    AppProperties appProperties = new AppProperties();
    appProperties.setMasterInstanceUrl(
        "http://localhost:" + server.getAddress().getPort());
    appProperties.setLocationApiKey(API_KEY);

    service = new FinancialLedgerSyncService(appProperties);
  }

  @AfterEach
  void stopFakeMaster() {
    server.stop(0);
  }

  @Test
  void cashEvent_postsToLocalCashEndpoint_withLocationHeadersAndSourceTransactionId()
      throws Exception {

    Instant timestamp = Instant.now();
    service.handleLocalFinancialTransactionRecordedEvent(new LocalFinancialTransactionRecordedEvent(
        LocalFinancialTransactionRecordedEvent.Kind.CASH, LOCATION_ID, Integer.valueOf(42), 1,
        timestamp));

    awaitRequest();

    assertEquals("POST", capturedMethod.get());
    assertEquals("/api/locations/" + LOCATION_ID + "/financial-ledger/local-cash",
        capturedPath.get());
    assertEquals(String.valueOf(LOCATION_ID), capturedLocationIdHeader.get());
    assertEquals(API_KEY, capturedApiKeyHeader.get());
    assertEquals(42, capturedBody.get().get("sourceTransactionId"));
    assertEquals(1, capturedBody.get().get("amountDollars"));
  }

  @Test
  void creditCardEvent_postsToLocalCreditCardEndpoint() throws Exception {

    service.handleLocalFinancialTransactionRecordedEvent(new LocalFinancialTransactionRecordedEvent(
        LocalFinancialTransactionRecordedEvent.Kind.CREDIT_CARD, LOCATION_ID, Integer.valueOf(9),
        1, Instant.now()));

    awaitRequest();

    assertEquals("/api/locations/" + LOCATION_ID + "/financial-ledger/local-credit-card",
        capturedPath.get());
    assertEquals(9, capturedBody.get().get("sourceTransactionId"));
  }

  @Test
  void event_withNoLocationId_isNeverPosted() throws Exception {

    service.handleLocalFinancialTransactionRecordedEvent(new LocalFinancialTransactionRecordedEvent(
        LocalFinancialTransactionRecordedEvent.Kind.CASH, null, Integer.valueOf(1), 1,
        Instant.now()));

    assertFalse(requestReceived.await(1, TimeUnit.SECONDS),
        "A transaction with no locationId (e.g. before a slave's own location is established) "
            + "must never be mirrored -- there is nowhere on master to attribute it to");
  }

  private void awaitRequest() throws InterruptedException {
    assertTrue(requestReceived.await(5, TimeUnit.SECONDS),
        "Expected the mirror POST to reach the fake master within 5s -- event handling dispatches "
            + "onto FinancialLedgerSyncService's own background executor, so this may need to wait "
            + "briefly for that thread to run");
  }
}
