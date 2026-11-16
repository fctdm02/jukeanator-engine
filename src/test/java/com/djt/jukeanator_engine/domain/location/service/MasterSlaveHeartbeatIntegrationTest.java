package com.djt.jukeanator_engine.domain.location.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.FakePaymentGatewayConfiguration;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;

/**
 * Master's STOMP broker must agree to heartbeats on {@code /ws-slave}. Without a broker task
 * scheduler (see {@code WebSocketConfig#configureMessageBroker}) the simple broker answers every
 * CONNECT with {@code heart-beat:0,0}, which turns heartbeats off in both directions: an idle slave
 * connection can then be dropped silently by a router, NAT or the OS, and the slave only finds out
 * when its next event send fails and that event is lost.
 *
 * <p>A simulated slave connects exactly as {@code SlaveConnectionManager} does, asking for 10s/10s,
 * and the test asserts the {@code heart-beat} header of master's CONNECTED frame.
 *
 * <p>Requires the same live MySQL {@code jukeanator_test} database as
 * {@link MasterSlaveLibrarySyncIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.mode=master", "app.repository-type=jpa" })
@Import(FakePaymentGatewayConfiguration.class)
class MasterSlaveHeartbeatIntegrationTest extends AbstractServiceIntegrationTest {

  /** What SlaveConnectionManager asks for, and what master is configured to offer. */
  private static final long[] SLAVE_REQUESTED_HEARTBEAT = {10_000, 10_000};

  private static final long WAIT_MILLIS = 10_000L;

  @LocalServerPort
  private int port;

  @Autowired
  private LocationService locationService;

  @Test
  void slaveConnect_masterAgreesToHeartbeatsInBothDirections() throws Exception {

    ProvisionedLocationDto provisioned = locationService.registerLocation(
        new RegisterLocationRequest("Heartbeat Tavern " + System.nanoTime(), 1.0, 2.0));

    ThreadPoolTaskScheduler clientScheduler = new ThreadPoolTaskScheduler();
    clientScheduler.setPoolSize(1);
    clientScheduler.setDaemon(true);
    clientScheduler.initialize();

    WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    // Required by WebSocketStompClient whenever the CONNECT frame asks for heartbeats.
    stompClient.setTaskScheduler(clientScheduler);

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.set("location-id", String.valueOf(provisioned.locationId()));
    connectHeaders.set("location-api-key", provisioned.apiKey());
    connectHeaders.setHeartbeat(SLAVE_REQUESTED_HEARTBEAT);

    CompletableFuture<StompHeaders> connectedFrameHeaders = new CompletableFuture<>();
    StompSession session = stompClient
        .connectAsync("ws://localhost:" + port + "/ws-slave", new WebSocketHttpHeaders(),
            connectHeaders, new StompSessionHandlerAdapter() {
              @Override
              public void afterConnected(StompSession connected, StompHeaders headers) {
                connectedFrameHeaders.complete(headers);
              }
            })
        .get(WAIT_MILLIS, TimeUnit.MILLISECONDS);

    try {
      long[] negotiated =
          connectedFrameHeaders.get(WAIT_MILLIS, TimeUnit.MILLISECONDS).getHeartbeat();
      // CONNECTED heart-beat is master's own sx,sy: it will send every 10s and expects one every
      // 10s. Before the fix this was 0,0.
      assertArrayEquals(new long[] {10_000, 10_000}, negotiated);
    } finally {
      session.disconnect();
      clientScheduler.shutdown();
    }
  }
}
