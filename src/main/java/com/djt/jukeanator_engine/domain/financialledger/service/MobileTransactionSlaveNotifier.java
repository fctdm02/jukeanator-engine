package com.djt.jukeanator_engine.domain.financialledger.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalMobileTransactionDto;
import com.djt.jukeanator_engine.domain.financialledger.event.MobilePurchaseRecordedEvent;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;

/**
 * Master-only. Mirrors each Web/Mobile Add Funds purchase down to the slave of the location it was
 * bought for (a {@code recordMobileTransaction} command over the {@code /ws-slave} channel), so
 * the slave's own ledger and jukebox-split totals include it, just like a swipe on its own card
 * reader. Best-effort, on a background daemon executor so the patron's purchase never waits on
 * it: a push that fails is recovered by the slave's catch-up pull (see
 * {@code FinancialLedgerSyncService}), and the slave's store is idempotent on master's id.
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class MobileTransactionSlaveNotifier {

  private static final Logger log = LoggerFactory.getLogger(MobileTransactionSlaveNotifier.class);

  public static final String RECORD_MOBILE_TRANSACTION_COMMAND = "recordMobileTransaction";

  private final SlaveCommandGateway slaveCommandGateway;

  private final ExecutorService notifyExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "mobile-transaction-slave-notifier");
    t.setDaemon(true);
    return t;
  });

  public MobileTransactionSlaveNotifier(SlaveCommandGateway slaveCommandGateway) {
    this.slaveCommandGateway = slaveCommandGateway;
  }

  @PreDestroy
  public void shutdown() {
    notifyExecutor.shutdownNow();
  }

  @EventListener
  public void handleMobilePurchaseRecordedEvent(MobilePurchaseRecordedEvent event) {

    LocalMobileTransactionDto transaction = event.transaction();
    notifyExecutor.submit(() -> {
      try {
        slaveCommandGateway.sendCommand(transaction.locationId(),
            RECORD_MOBILE_TRANSACTION_COMMAND, transaction);
      } catch (Throwable t) {
        log.info("Could not push mobile purchase {} to locationId {} -- the slave's catch-up "
            + "pull will recover it: {}", transaction.persistentIdentity(),
            transaction.locationId(), t.getMessage());
      }
    });
  }
}
