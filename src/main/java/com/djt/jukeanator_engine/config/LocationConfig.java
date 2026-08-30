package com.djt.jukeanator_engine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import com.djt.jukeanator_engine.domain.location.config.LocationProperties;
import com.djt.jukeanator_engine.domain.location.repository.LocationRepository;
import com.djt.jukeanator_engine.domain.location.repository.LocationRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.location.repository.LocationRepositoryJpaImpl;
import com.djt.jukeanator_engine.domain.location.security.LocationApiKeyAuthenticationFilter;
import com.djt.jukeanator_engine.domain.location.security.StompLocationApiKeyChannelInterceptor;
import com.djt.jukeanator_engine.domain.location.service.ConnectedSlaveRegistry;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.location.service.LocationServiceImpl;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;

/**
 * Wires the {@code location} domain's beans. {@link LocationRepository} and {@link LocationService}
 * are available regardless of {@code app.mode} — a standalone or slave instance can locally register
 * a location (persisted to its own JSON/JPA store) so the operator has a ready-made record to turn
 * into a SQL insert against the master's hosted database. The remaining beans below (slave-connection
 * tracking, API-key auth for incoming slave traffic, and the master→slave command gateway) are
 * meaningful only when this instance <em>is</em> the master, so they stay gated on
 * {@code app.mode=master}.
 */
@Configuration
public class LocationConfig {

  @Bean
  @ConditionalOnProperty(name = "app.repository-type", havingValue = "filesystem",
      matchIfMissing = true)
  public LocationRepository locationRepositoryFileSystemImpl(AppProperties appProperties) {

    return new LocationRepositoryFileSystemImpl(appProperties.getDataDir());
  }

  @Bean
  @ConditionalOnProperty(name = "app.repository-type", havingValue = "jpa")
  public LocationRepository locationRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    return new LocationRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  @Bean
  public ConnectedSlaveRegistry connectedSlaveRegistry() {
    return new ConnectedSlaveRegistry();
  }

  @Bean
  public LocationService locationService(AppProperties appProperties,
      LocationProperties locationProperties, LocationRepository locationRepository,
      PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper, ConnectedSlaveRegistry connectedSlaveRegistry,
      SongLibraryRepository songLibraryRepository) {

    return new LocationServiceImpl(locationRepository, passwordEncoder, eventPublisher,
        objectMapper, perLocationSyncStorageRoot(appProperties, locationProperties),
        connectedSlaveRegistry, songLibraryRepository);
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

  // Governs only where synced per-location library.json/cover-art land (LocationServiceImpl's
  // locationStorageRoot) -- independent of where the location list itself is stored, which is
  // always app.data-dir directly for the filesystem repository, matching every other
  // filesystem-backed repository in the app.
  private static String perLocationSyncStorageRoot(AppProperties appProperties,
      LocationProperties locationProperties) {

    if (locationProperties.getStorageRoot() != null
        && !locationProperties.getStorageRoot().isBlank()) {
      return locationProperties.getStorageRoot();
    }
    return appProperties.getDataDir() + java.io.File.separator + "locations";
  }
}
