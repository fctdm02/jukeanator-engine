package com.djt.jukeanator_engine.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.location.security.StompLocationApiKeyChannelInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final JwtUtil jwtUtil;

  // Only present in master mode (see LocationConfig) — standalone/slave deployments never
  // construct this bean, so the /ws-slave endpoint below simply never gets registered for them.
  private final Optional<StompLocationApiKeyChannelInterceptor> locationApiKeyChannelInterceptor;

  public WebSocketConfig(JwtUtil jwtUtil,
      Optional<StompLocationApiKeyChannelInterceptor> locationApiKeyChannelInterceptor) {
    this.jwtUtil = jwtUtil;
    this.locationApiKeyChannelInterceptor = locationApiKeyChannelInterceptor;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();

    // Master-only, separate from the browser-facing /ws endpoint above: slaves dial this one
    // directly (no SockJS fallback needed — it's a backend-to-backend connection), authenticated
    // by StompLocationApiKeyChannelInterceptor rather than JWT.
    if (locationApiKeyChannelInterceptor.isPresent()) {
      registry.addEndpoint("/ws-slave").setAllowedOriginPatterns("*");
    }
  }

  @Bean
  public StompJwtChannelInterceptor stompJwtChannelInterceptor() {
    return new StompJwtChannelInterceptor(jwtUtil);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    locationApiKeyChannelInterceptor.ifPresentOrElse(
        locationInterceptor -> registration.interceptors(stompJwtChannelInterceptor(),
            locationInterceptor),
        () -> registration.interceptors(stompJwtChannelInterceptor()));
  }
}
