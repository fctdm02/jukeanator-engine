package com.djt.jukeanator_engine.domain.financialledger.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import com.djt.jukeanator_engine.domain.user.event.LocationSongCreditUsageRecordedEvent;
import jakarta.annotation.PreDestroy;

/**
 * Master-only. Mirrors each location-attributed mobile/web song-credit spend down to that
 * location's slave as it happens (a {@code recordMobileCreditUsage} command over the existing
 * {@code /ws-slave} channel -- master can't dial a slave directly), so the slave has its own copy
 * for its jukebox-split mobile total. Best-effort, on a background daemon executor so the patron's
 * HTTP request never waits on it: a push that fails (the slave went offline, or the command timed
 * out) is recovered by the slave's own catch-up pull (see {@code FinancialLedgerSyncService}), and
 * the slave's store is idempotent on the spend's {@code syncId}, so receiving it both ways is
 * harmless.
 *
 * @author tmyers
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class MobileCreditUsageSlaveNotifier {

  private static final Logger log = LoggerFactory.getLogger(MobileCreditUsageSlaveNotifier.class);

  static final String RECORD_MOBILE_CREDIT_USAGE_COMMAND = "recordMobileCreditUsage";

  private final SlaveCommandGateway slaveCommandGateway;

  private final ExecutorService notifyExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "mobile-credit-usage-slave-notifier");
    t.setDaemon(true);
    return t;
  });

  public MobileCreditUsageSlaveNotifier(SlaveCommandGateway slaveCommandGateway) {
    this.slaveCommandGateway = slaveCommandGateway;
  }

  @PreDestroy
  public void shutdown() {
    notifyExecutor.shutdownNow();
  }

  @EventListener
  public void handleLocationSongCreditUsageRecordedEvent(
      LocationSongCreditUsageRecordedEvent event) {

    UserSongCreditUsageDto usage = event.usage();
    notifyExecutor.submit(() -> {
      try {
        slaveCommandGateway.sendCommand(usage.locationId(), RECORD_MOBILE_CREDIT_USAGE_COMMAND,
            usage);
      } catch (Throwable t) {
        log.info("Could not push mobile credit usage " + usage.syncId() + " to locationId "
            + usage.locationId() + " -- the slave's catch-up pull will recover it: "
            + t.getMessage());
      }
    });
  }
}
