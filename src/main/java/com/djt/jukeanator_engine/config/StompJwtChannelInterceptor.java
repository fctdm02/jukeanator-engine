package com.djt.jukeanator_engine.config;

import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;

/**
 * Reads a {@code token} header from the STOMP CONNECT frame, validates it as a JWT,
 * and sets the authenticated user on the WebSocket session so that Spring can route
 * user-specific destinations (e.g. {@code /user/queue/credits}).
 */
public class StompJwtChannelInterceptor implements ChannelInterceptor {

  private final JwtUtil jwtUtil;

  public StompJwtChannelInterceptor(JwtUtil jwtUtil) {
    this.jwtUtil = jwtUtil;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
      String token = accessor.getFirstNativeHeader("token");
      if (token != null && jwtUtil.isTokenValid(token)) {
        String email = jwtUtil.extractEmail(token);
        accessor.setUser(new StompPrincipal(email));
      }
    }
    return message;
  }

  /** Minimal {@link Principal} that carries only the email address. */
  private record StompPrincipal(String name) implements Principal {
    @Override public String getName() { return name; }
  }
}
