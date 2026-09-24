package com.djt.jukeanator_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.djt.jukeanator_engine.domain.financialledger.controller.FinancialLedgerSyncController;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerSyncService;
import com.djt.jukeanator_engine.domain.financialledger.service.MobileCreditUsageSlaveNotifier;
import com.djt.jukeanator_engine.domain.location.client.SlaveConnectionManager;
import com.djt.jukeanator_engine.domain.location.service.LibrarySyncService;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;
import com.djt.jukeanator_engine.domain.useractivity.controller.UserActivitySyncController;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityService;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivitySyncService;

/**
 * Slave-mode counterpart to {@link MySqlMasterModeJukeanatorEngineApplicationTests}: every other
 * {@code @SpringBootTest} runs as standalone or master, so the slave-only wiring -- {@code
 * SlaveConnectionManager} and the two outbox sync services, plus everything they depend on -- is
 * otherwise never assembled in a real application context before an actual slave boots. The
 * concrete subclasses run it once per repository type, since a slave may use either (each
 * subclass's own {@code @TestPropertySource} merges with the one declared here).
 *
 * <p>{@code app.master-instance-url} deliberately points at a port nothing listens on: {@code
 * SlaveConnectionManager}'s reconnect attempts and the sync services' sweeps must fail quietly
 * without affecting the context, exactly as they must on a slave whose master is unreachable. The
 * {@code app.location-*} values only satisfy {@code AppModeValidator}; nothing here contacts a
 * master.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.mode=slave", "app.master-instance-url=http://localhost:1",
    "app.location-id=1", "app.location-api-key=smoke-test-key" })
abstract class AbstractSlaveModeApplicationTests extends AbstractServiceIntegrationTest {

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private FinancialLedgerService financialLedgerService;

  @Autowired
  private UserActivityService userActivityService;

  @Test
  void slaveOnlyBeansAreWired() {

    assertBeanCount(SlaveConnectionManager.class, 1);
    assertBeanCount(LibrarySyncService.class, 1);
    assertBeanCount(FinancialLedgerSyncService.class, 1);
    assertBeanCount(UserActivitySyncService.class, 1);
  }

  @Test
  void masterOnlyBeansAreAbsent() {

    assertBeanCount(SlaveCommandGateway.class, 0);
    assertBeanCount(MobileCreditUsageSlaveNotifier.class, 0);
    assertBeanCount(FinancialLedgerSyncController.class, 0);
    assertBeanCount(UserActivitySyncController.class, 0);
  }

  // Exercises the outbox reads each sync service performs on every sweep, against this
  // repository type's real storage (e.g. the JPQL behind them under JPA).
  @Test
  void outboxReadsSucceed() {

    assertNotNull(financialLedgerService.getPendingMasterSync());
    assertNotNull(userActivityService.getPendingMasterSync(10));
    financialLedgerService.getLatestMobileCreditUsageTimestamp();
  }

  private void assertBeanCount(Class<?> beanType, int expectedCount) {
    assertEquals(expectedCount, applicationContext.getBeanNamesForType(beanType).length,
        "Expected " + expectedCount + " bean(s) of type " + beanType.getSimpleName()
            + " in slave mode");
  }
}
