package com.djt.jukeanator_engine.domain.location.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import com.djt.jukeanator_engine.domain.location.service.ConnectedSlaveRegistry;
import com.djt.jukeanator_engine.domain.location.service.LocationService;

/**
 * A slave's {@code /ws-slave} CONNECT: authenticated by its API key alone, with its self-reported
 * {@code location-id} used only to check that one location's key first -- one (deliberately slow)
 * BCrypt check per connect instead of one per location master serves.
 */
class StompLocationApiKeyChannelInterceptorTest {

  private static final Integer LOCATION_ID = 7;
  private static final String API_KEY = "the-real-key";

  private LocationService locationService;
  private ConnectedSlaveRegistry connectedSlaveRegistry;
  private StompLocationApiKeyChannelInterceptor interceptor;

  @BeforeEach
  void setUp() {
    locationService = mock(LocationService.class);
    connectedSlaveRegistry = new ConnectedSlaveRegistry();
    interceptor = new StompLocationApiKeyChannelInterceptor(locationService, connectedSlaveRegistry);
    when(locationService.verifyApiKey(LOCATION_ID, API_KEY)).thenReturn(true);
    when(locationService.resolveAndVerifyByApiKey(API_KEY)).thenReturn(LOCATION_ID);
  }

  @Test
  void aSlaveReportingItsLocation_isVerifiedAgainstThatLocationAlone() {

    StompHeaderAccessor connected = connect(String.valueOf(LOCATION_ID), API_KEY);

    assertLocation(connected, LOCATION_ID);
    verify(locationService, never()).resolveAndVerifyByApiKey(anyString());
  }

  @Test
  void aSlaveWithAWrongMissingOrGarbledLocation_isStillFoundByItsKey() {

    for (String hint : new String[] { "99", null, "not-a-number" }) {
      assertLocation(connect(hint, API_KEY), LOCATION_ID);
    }
  }

  @Test
  void aWrongKey_neverConnects_whateverLocationItClaims() {

    when(locationService.verifyApiKey(any(), any())).thenReturn(false);
    when(locationService.resolveAndVerifyByApiKey("stolen-id-wrong-key")).thenReturn(null);

    StompHeaderAccessor refused = connect(String.valueOf(LOCATION_ID), "stolen-id-wrong-key");

    assertNull(refused.getUser());
    assertFalse(connectedSlaveRegistry.isConnected(LOCATION_ID));
  }

  private StompHeaderAccessor connect(String locationIdHint, String apiKey) {

    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    if (locationIdHint != null) {
      accessor.setNativeHeader("location-id", locationIdHint);
    }
    accessor.setNativeHeader("location-api-key", apiKey);
    accessor.setSessionId("session-" + System.nanoTime());
    accessor.setLeaveMutable(true);
    Message<byte[]> message = MessageBuilder.createMessage(new byte[0],
        accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
    return StompHeaderAccessor.wrap(result);
  }

  private void assertLocation(StompHeaderAccessor connected, Integer expectedLocationId) {
    LocationPrincipal principal = assertInstanceOf(LocationPrincipal.class, connected.getUser());
    assertEquals(String.valueOf(expectedLocationId), principal.getName());
    assertTrue(connectedSlaveRegistry.isConnected(expectedLocationId));
  }
}
