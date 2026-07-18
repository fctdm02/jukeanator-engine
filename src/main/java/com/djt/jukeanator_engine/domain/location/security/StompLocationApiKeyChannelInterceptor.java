package com.djt.jukeanator_engine.domain.location.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import com.djt.jukeanator_engine.domain.location.service.ConnectedSlaveRegistry;
import com.djt.jukeanator_engine.domain.location.service.LocationService;

/**
 * Master-only. Reads {@code location-id}/{@code location-api-key} native headers from the
 * {@code /ws-slave} STOMP CONNECT frame, verifies them the same way the HTTP library-sync
 * endpoints do, and — on success — sets a {@link LocationPrincipal} on the session (enabling
 * {@code convertAndSendToUser(locationId, ...)} routing) and marks the location connected in
 * {@link ConnectedSlaveRegistry}. Mirrors {@code StompJwtChannelInterceptor}'s shape for JWT.
 */
public class StompLocationApiKeyChannelInterceptor implements ChannelInterceptor {

  private final LocationService locationService;
  private final ConnectedSlaveRegistry connectedSlaveRegistry;

  public StompLocationApiKeyChannelInterceptor(LocationService locationService,
      ConnectedSlaveRegistry connectedSlaveRegistry) {
    this.locationService = locationService;
    this.connectedSlaveRegistry = connectedSlaveRegistry;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {

    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
      String locationId = accessor.getFirstNativeHeader("location-id");
      String apiKey = accessor.getFirstNativeHeader("location-api-key");
      if (locationId != null && apiKey != null && locationService.verifyApiKey(locationId, apiKey)) {
        accessor.setUser(new LocationPrincipal(locationId));
        connectedSlaveRegistry.markConnected(locationId, accessor.getSessionId());
        locationService.recordHeartbeat(locationId);
      }
    }
    return message;
  }
}
