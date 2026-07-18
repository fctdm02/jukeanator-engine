package com.djt.jukeanator_engine.domain.location.controller;

import java.security.Principal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import com.djt.jukeanator_engine.domain.location.dto.CommandReplyDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationEventMessage;
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
}
