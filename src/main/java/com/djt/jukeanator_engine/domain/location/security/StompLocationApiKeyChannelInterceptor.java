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
 * Master-only. Reads the {@code location-api-key} native header from the {@code /ws-slave} STOMP
 * CONNECT frame and resolves the location by that key alone (never trusting the slave's
 * self-reported {@code location-id} header) — a fresh slave's own guess at its locationId (from
 * {@code app.location-id}) may not match the id an admin later assigns by hand when inserting its
 * row into master's database, so authentication can't require them to match up front. On success,
 * sets a {@link LocationPrincipal} on the session (enabling
 * {@code convertAndSendToUser(locationId, ...)} routing) and marks the location connected in
 * {@link ConnectedSlaveRegistry}; the resolved id is then pushed back to the slave as soon as the
 * session is fully established (see {@code LocationEventStompController}'s {@code
 * SessionConnectedEvent} listener), which corrects its local {@code locationMetadata.txt} if it
 * was wrong. Mirrors {@code StompJwtChannelInterceptor}'s shape for JWT.
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
      String apiKey = accessor.getFirstNativeHeader("location-api-key");
      Integer locationId = locationService.resolveAndVerifyByApiKey(apiKey);
      if (locationId != null) {
        accessor.setUser(new LocationPrincipal(locationId));
        connectedSlaveRegistry.markConnected(locationId, accessor.getSessionId());
        locationService.recordHeartbeat(locationId);
      }
    }
    return message;
  }
}
