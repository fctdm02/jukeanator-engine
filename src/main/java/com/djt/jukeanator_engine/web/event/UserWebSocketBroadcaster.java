package com.djt.jukeanator_engine.web.event;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.user.event.RecentPlayAddedEvent;
import com.djt.jukeanator_engine.domain.user.event.UserCreditsChangedEvent;

/**
 * Sends a signed-in Web/Mobile UI patron their own live updates, over the user destinations
 * {@code /user/queue/credits} and {@code /user/queue/recent-plays}. Runs in every {@code app.mode}
 * -- above all on master, which owns every patron's account -- unlike the per-location
 * {@link WebSocketEventBroadcaster}.
 */
@Component
public class UserWebSocketBroadcaster {

  private final SimpMessagingTemplate messagingTemplate;

  public UserWebSocketBroadcaster(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @EventListener
  public void handleUserCreditsChangedEvent(UserCreditsChangedEvent event) {
    messagingTemplate.convertAndSendToUser(event.emailAddress(), "/queue/credits",
        new CreditsMessage(event.numCredits()));
  }

  @EventListener
  public void handleRecentPlayAddedEvent(RecentPlayAddedEvent event) {
    messagingTemplate.convertAndSendToUser(event.emailAddress(), "/queue/recent-plays",
        event.song());
  }
}
