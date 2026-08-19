package com.djt.jukeanator_engine.domain.location.controller;

import java.security.Principal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import com.djt.jukeanator_engine.domain.location.dto.CommandReplyDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationEventMessage;
import com.djt.jukeanator_engine.domain.location.security.LocationPrincipal;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;

/**
 * Master-only STOMP {@code @MessageMapping} handlers for the {@code /ws-slave} channel — the
 * inbound half of the master&lt;-&gt;slave persistent connection. Handles both directions a slave
 * sends: its own real-time events (republished per-location for web/mobile clients already
 * subscribed to master's browser-facing {@code /ws}) and replies to commands the master sent via
 * {@link SlaveCommandGateway}.
 *
 * @author tmyers
 */
@Controller
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class LocationEventStompController {

  private static final String LOCATION_ID_CONFIRMED_DESTINATION = "/queue/location-id-confirmed";

  private final SimpMessagingTemplate messagingTemplate;
  private final SlaveCommandGateway slaveCommandGateway;

  public LocationEventStompController(SimpMessagingTemplate messagingTemplate,
      SlaveCommandGateway slaveCommandGateway) {
    this.messagingTemplate = messagingTemplate;
    this.slaveCommandGateway = slaveCommandGateway;
  }

  @MessageMapping("/location-events")
  public void handleLocationEvent(LocationEventMessage message, Principal principal) {

    String locationId = principal.getName();
    messagingTemplate.convertAndSend(
        "/topic/location/" + locationId + "/" + message.eventType(), message.payload());
  }

  @MessageMapping("/location-command-reply")
  public void handleCommandReply(CommandReplyDto reply) {
    slaveCommandGateway.completeReply(reply);
  }

  /**
   * Fires for every established {@code /ws-slave} session (this event type is shared with the
   * browser-facing {@code /ws} endpoint, hence the {@code instanceof} guard). {@code
   * StompLocationApiKeyChannelInterceptor} authenticates purely by API key, never trusting the
   * slave's self-reported {@code location-id} header, so this is how the slave learns its true,
   * master-assigned id — see {@code SlaveConnectionManager}'s subscription to this destination,
   * which corrects the slave's local {@code locationMetadata.txt} if it was wrong.
   */
  @EventListener
  public void handleSessionConnected(SessionConnectedEvent event) {

    if (event.getUser() instanceof LocationPrincipal locationPrincipal) {
      messagingTemplate.convertAndSendToUser(locationPrincipal.getName(),
          LOCATION_ID_CONFIRMED_DESTINATION, locationPrincipal.locationId());
    }
  }
}
