package com.djt.jukeanator_engine.domain.useractivity.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.useractivity.repository.UserActivityRepository;
import com.djt.jukeanator_engine.domain.useractivity.repository.UserActivityRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.useractivity.repository.UserActivityRepositoryJpaImpl;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityEventListener;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityService;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityServiceImpl;

/**
 * Wires the {@code useractivity} domain's beans, mirroring {@code FinancialLedgerConfig}'s {@code
 * app.repository-type} bean-selection pair.
 */
@Configuration
public class UserActivityConfig {

  @Bean
  @ConditionalOnProperty(name = "app.repository-type", havingValue = "filesystem",
      matchIfMissing = true)
  public UserActivityRepository userActivityRepositoryFileSystemImpl(AppProperties appProperties) {

    return new UserActivityRepositoryFileSystemImpl(appProperties.getDataDir());
  }

  @Bean
  @ConditionalOnProperty(name = "app.repository-type", havingValue = "jpa")
  public UserActivityRepository userActivityRepositoryJpaImpl(
      EntityManagerFactory entityManagerFactory, PlatformTransactionManager transactionManager) {

    return new UserActivityRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  @Bean
  public UserActivityService userActivityService(UserActivityRepository userActivityRepository,
      ApplicationEventPublisher eventPublisher, ObjectProvider<LocationService> locationService) {
    return new UserActivityServiceImpl(userActivityRepository, eventPublisher,
        locationService::getObject);
  }

  @Bean
  public UserActivityEventListener userActivityEventListener(
      UserActivityRepository userActivityRepository) {

    return new UserActivityEventListener(userActivityRepository);
  }
}
