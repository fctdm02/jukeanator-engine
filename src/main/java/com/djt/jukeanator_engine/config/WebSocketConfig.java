package com.djt.jukeanator_engine.config;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.location.security.StompLocationApiKeyChannelInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private static final long HEARTBEAT_INTERVAL_MILLIS = 10_000;

  private final JwtUtil jwtUtil;

  // Only present in master mode (see LocationConfig) — standalone/slave deployments never
  // construct this bean, so the /ws-slave endpoint below simply never gets registered for them.
  private final Optional<StompLocationApiKeyChannelInterceptor> locationApiKeyChannelInterceptor;

  private TaskScheduler messageBrokerTaskScheduler;

  public WebSocketConfig(JwtUtil jwtUtil,
      Optional<StompLocationApiKeyChannelInterceptor> locationApiKeyChannelInterceptor) {
    this.jwtUtil = jwtUtil;
    this.locationApiKeyChannelInterceptor = locationApiKeyChannelInterceptor;
  }

  /**
   * The scheduler {@code @EnableWebSocketMessageBroker} already defines for the broker. It is
   * injected {@code @Lazy} because that bean is created by the same configuration this class
   * feeds (the pattern the Spring reference recommends). Reusing it, rather than declaring a new
   * {@code TaskScheduler} bean, leaves Spring Boot's auto-configured scheduler for
   * {@code @Scheduled} untouched.
   */
  @Autowired
  public void setMessageBrokerTaskScheduler(
      @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler) {
    this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    // Without a task scheduler the simple broker answers every CONNECT with heart-beat:0,0, which
    // turns heartbeats off in both directions. An idle /ws-slave connection can then be silently
    // dropped by a router, NAT or the OS, and nobody notices until the slave's next send fails
    // (losing that event). With heartbeats the link is kept alive, and a dead one is detected and
    // reconnected within a few intervals instead.
    registry.enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] {HEARTBEAT_INTERVAL_MILLIS, HEARTBEAT_INTERVAL_MILLIS})
        .setTaskScheduler(messageBrokerTaskScheduler);
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
