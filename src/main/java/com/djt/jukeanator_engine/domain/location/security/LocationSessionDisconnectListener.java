package com.djt.jukeanator_engine.domain.location.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import com.djt.jukeanator_engine.domain.location.service.ConnectedSlaveRegistry;

/** Master-only. Marks a location disconnected when its {@code /ws-slave} session drops, so the
 * public location picker's "online" status and {@code SlaveCommandGateway}'s fast-fail check stay
 * accurate. */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class LocationSessionDisconnectListener {

  private final ConnectedSlaveRegistry connectedSlaveRegistry;

  public LocationSessionDisconnectListener(ConnectedSlaveRegistry connectedSlaveRegistry) {
    this.connectedSlaveRegistry = connectedSlaveRegistry;
  }

  @EventListener
  public void handleSessionDisconnect(SessionDisconnectEvent event) {
    connectedSlaveRegistry.markDisconnected(event.getSessionId());
  }
}
