package com.djt.jukeanator_engine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.djt.jukeanator_engine.domain.location.config.LocationProperties;
import com.djt.jukeanator_engine.domain.location.repository.LocationRepository;
import com.djt.jukeanator_engine.domain.location.repository.LocationRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.location.repository.LocationRepositoryPostgresImpl;
import com.djt.jukeanator_engine.domain.location.security.LocationApiKeyAuthenticationFilter;
import com.djt.jukeanator_engine.domain.location.security.StompLocationApiKeyChannelInterceptor;
import com.djt.jukeanator_engine.domain.location.service.ConnectedSlaveRegistry;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.location.service.LocationServiceImpl;
import com.djt.jukeanator_engine.domain.location.service.LocationServiceRegistry;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;

/**
 * Wires the {@code location} domain's master-only beans. None of these beans exist unless
 * {@code app.mode=master} — standalone and slave deployments never construct them, so this
 * feature carries zero risk for existing single-tenant installs.
 */
@Configuration
public class LocationConfig {

  @Bean
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  @ConditionalOnProperty(name = "location.repository-type", havingValue = "filesystem",
      matchIfMissing = true)
  public LocationRepository locationRepositoryFileSystemImpl(AppProperties appProperties,
      LocationProperties locationProperties) {

    return new LocationRepositoryFileSystemImpl(effectiveStorageRoot(appProperties, locationProperties));
  }

  @Bean
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  @ConditionalOnProperty(name = "location.repository-type", havingValue = "postgres")
  public LocationRepository locationRepositoryPostgresImpl() {

    return new LocationRepositoryPostgresImpl();
  }

  @Bean
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  public ConnectedSlaveRegistry connectedSlaveRegistry() {
    return new ConnectedSlaveRegistry();
  }

  @Bean
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  public LocationService locationService(AppProperties appProperties,
      LocationProperties locationProperties, LocationRepository locationRepository,
      PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper, ConnectedSlaveRegistry connectedSlaveRegistry) {

    return new LocationServiceImpl(locationRepository, passwordEncoder, eventPublisher,
        objectMapper, effectiveStorageRoot(appProperties, locationProperties),
        connectedSlaveRegistry);
  }

  @Bean
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  public LocationApiKeyAuthenticationFilter locationApiKeyAuthenticationFilter(
      LocationService locationService) {

    return new LocationApiKeyAuthenticationFilter(locationService);
  }

  @Bean
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  public StompLocationApiKeyChannelInterceptor stompLocationApiKeyChannelInterceptor(
      LocationService locationService, ConnectedSlaveRegistry connectedSlaveRegistry) {

    return new StompLocationApiKeyChannelInterceptor(locationService, connectedSlaveRegistry);
  }

  @Bean
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  public SlaveCommandGateway slaveCommandGateway(SimpMessagingTemplate messagingTemplate,
      ObjectMapper objectMapper, ConnectedSlaveRegistry connectedSlaveRegistry,
      LocationProperties locationProperties) {

    return new SlaveCommandGateway(messagingTemplate, objectMapper, connectedSlaveRegistry,
        locationProperties.getCommandTimeoutMs());
  }

  @Bean
  @ConditionalOnProperty(name = "app.mode", havingValue = "master")
  public LocationServiceRegistry locationServiceRegistry(SlaveCommandGateway slaveCommandGateway,
      LocationService locationService) {

    return new LocationServiceRegistry(slaveCommandGateway, locationService);
  }

  private static String effectiveStorageRoot(AppProperties appProperties,
      LocationProperties locationProperties) {

    if (locationProperties.getStorageRoot() != null
        && !locationProperties.getStorageRoot().isBlank()) {
      return locationProperties.getStorageRoot();
    }
    return appProperties.getEffectiveRootPath() + java.io.File.separator + "locations";
  }
}
