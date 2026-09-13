package com.djt.jukeanator_engine.domain.financialledger.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.financialledger.repository.FinancialLedgerRepository;
import com.djt.jukeanator_engine.domain.financialledger.repository.FinancialLedgerRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.financialledger.repository.FinancialLedgerRepositoryJpaImpl;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerServiceImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.user.service.PricingService;
import com.djt.jukeanator_engine.domain.user.service.UserService;

/**
 * Wires the {@code financialledger} domain's beans, mirroring {@code LocationConfig}'s
 * {@code app.repository-type} bean-selection pair.
 */
@Configuration
public class FinancialLedgerConfig {

  @Bean
  @ConditionalOnProperty(name = "app.repository-type", havingValue = "filesystem",
      matchIfMissing = true)
  public FinancialLedgerRepository financialLedgerRepositoryFileSystemImpl(
      AppProperties appProperties) {

    return new FinancialLedgerRepositoryFileSystemImpl(appProperties.getDataDir());
  }

  @Bean
  @ConditionalOnProperty(name = "app.repository-type", havingValue = "jpa")
  public FinancialLedgerRepository financialLedgerRepositoryJpaImpl(
      EntityManagerFactory entityManagerFactory, PlatformTransactionManager transactionManager) {

    return new FinancialLedgerRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  @Bean
  public FinancialLedgerService financialLedgerService(
      FinancialLedgerRepository financialLedgerRepository,
      FinancialLedgerProperties financialLedgerProperties, UserService userService,
      PricingService pricingService, SongLibraryService songLibraryService) {

    return new FinancialLedgerServiceImpl(financialLedgerRepository, financialLedgerProperties,
        userService, pricingService, songLibraryService);
  }
}
