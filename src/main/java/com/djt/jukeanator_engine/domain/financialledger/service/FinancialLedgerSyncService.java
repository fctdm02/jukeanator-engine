package com.djt.jukeanator_engine.domain.financialledger.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.common.security.SecurityContextPropagatingRunnable;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.financialledger.dto.MasterSyncOutboxEntry;
import com.djt.jukeanator_engine.domain.financialledger.event.JukeboxSplitFinalizedEvent;
import com.djt.jukeanator_engine.domain.financialledger.event.LocalFinancialTransactionRecordedEvent;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import jakarta.annotation.PreDestroy;

/**
 * Slave-only. Keeps this slave's financial ledger and master's in step, in both directions, on a
 * background daemon executor so a slow/unreachable master never delays the Swing UI's own
 * credit-award flow:
 *
 * <ul>
 * <li><b>Slave to master (outbox):</b> every local cash/credit-card transaction and every finalized
 * jukebox-split period this slave recorded stays in {@link
 * FinancialLedgerService#getPendingMasterSync()} until master acknowledges its push -- so nothing
 * recorded while master is unreachable is ever lost; it is simply pushed on a later sweep. Pushes
 * are idempotent on master (keyed by the slave's own id), so a re-push after a lost
 * acknowledgement is a safe no-op.
 * <li><b>Master to slave (catch-up):</b> master pushes each mobile/web spend down live as it
 * happens (see {@code MobileCreditUsageSlaveNotifier}); this pull covers any push that didn't
 * arrive. After start-up or any failed sweep it re-pulls the whole open split period; otherwise it
 * re-pulls just a short overlap window past the latest spend already mirrored.
 * </ul>
 *
 * <p>A sweep runs every {@link #SWEEP_INTERVAL_SECONDS} seconds, and immediately whenever a
 * transaction is recorded or a period finalized. Failures are logged, never thrown -- the next
 * sweep retries.
 *
 * @author tmyers
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "slave")
public class FinancialLedgerSyncService {

  private static final Logger log = LoggerFactory.getLogger(FinancialLedgerSyncService.class);

  static final long SWEEP_INTERVAL_SECONDS = 60;

  // How far behind the latest already-mirrored spend an incremental catch-up pull reaches back,
  // covering a live push that arrived out of order with a slightly earlier spend.
  static final Duration MOBILE_CATCH_UP_OVERLAP = Duration.ofMinutes(10);

  private final AppProperties appProperties;
  private final FinancialLedgerService financialLedgerService;
  private final SongLibraryService songLibraryService;
  private final RestClient restClient;

  private final ScheduledExecutorService syncExecutor =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "financial-ledger-sync-thread");
        t.setDaemon(true);
        return t;
      });

  private final AtomicBoolean fullMobileCatchUpNeeded = new AtomicBoolean(true);

  public FinancialLedgerSyncService(AppProperties appProperties,
      FinancialLedgerService financialLedgerService, SongLibraryService songLibraryService) {

    this.appProperties = appProperties;
    this.financialLedgerService = financialLedgerService;
    this.songLibraryService = songLibraryService;
    this.restClient = RestClient.builder().baseUrl(appProperties.getMasterInstanceUrl()).build();

    syncExecutor.scheduleWithFixedDelay(this::sweep, SWEEP_INTERVAL_SECONDS,
        SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void shutdown() {
    syncExecutor.shutdownNow();
  }

  @EventListener
  public void handleLocalFinancialTransactionRecordedEvent(
      LocalFinancialTransactionRecordedEvent event) {
    syncExecutor.execute(this::sweep);
  }

  @EventListener
  public void handleJukeboxSplitFinalizedEvent(JukeboxSplitFinalizedEvent event) {
    syncExecutor.execute(this::sweep);
  }

  // Runs only on syncExecutor's single thread, so sweeps never overlap. FinancialLedgerService
  // calls go through ServiceSecurityAspect, which needs an authenticated principal on this
  // otherwise-empty background thread.
  private void sweep() {

    new SecurityContextPropagatingRunnable(() -> {
      try {
        pushPendingToMaster();
        pullMobileCreditUsageFromMaster();
      } catch (Throwable t) {
        // Must never let a sync failure propagate anywhere that could affect the slave's own
        // credit-award flow -- the next sweep retries.
        fullMobileCatchUpNeeded.set(true);
        log.debug("Financial-ledger sync with master at " + appProperties.getMasterInstanceUrl()
            + " did not complete; will retry", t);
      }
    }, SystemPrincipal.SystemAuthenticationToken.INSTANCE).run();
  }

  private void pushPendingToMaster() {

    List<MasterSyncOutboxEntry> pending = financialLedgerService.getPendingMasterSync();
    List<MasterSyncOutboxEntry> acknowledged = new ArrayList<>();
    try {
      for (MasterSyncOutboxEntry entry : pending) {
        try {
          push(entry);
          acknowledged.add(entry);
        } catch (RestClientResponseException rejected) {
          // Master answered but refused this one (e.g. a location/api-key mismatch) -- leave it
          // pending and carry on, so one bad record never blocks everything queued behind it.
          log.warn("Master rejected mirrored " + entry.kind() + " " + entry.persistentIdentity()
              + " for locationId " + entry.locationId() + ": " + rejected.getStatusCode());
        }
      }
    } finally {
      // Whatever master already acknowledged stays acknowledged even if a later push failed.
      financialLedgerService.markSyncedToMaster(acknowledged);
    }
  }

  private void push(MasterSyncOutboxEntry entry) {

    String pathSegment = switch (entry.kind()) {
      case CASH -> "local-cash";
      case CREDIT_CARD -> "local-credit-card";
      case SPLIT_PERIOD -> "split-period";
    };

    restClient.post()
        .uri("/api/locations/{locationId}/financial-ledger/{pathSegment}", entry.locationId(),
            pathSegment)
        .header("location-id", String.valueOf(entry.locationId()))
        .header("location-api-key", appProperties.getLocationApiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(entry.payload())
        .retrieve()
        .toBodilessEntity();
  }

  private void pullMobileCreditUsageFromMaster() {

    Integer ownLocationId = songLibraryService.getOwnLocationId();
    Instant openPeriodStart = financialLedgerService.getOpenPeriodStartDate();
    if (ownLocationId == null || openPeriodStart == null) {
      return;
    }

    Instant since = catchUpSince(openPeriodStart,
        financialLedgerService.getLatestMobileCreditUsageTimestamp());

    List<UserSongCreditUsageDto> usages = restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/api/locations/{locationId}/financial-ledger/mobile-credit-usage")
            .queryParam("since", since.toString())
            .build(ownLocationId))
        .header("location-id", String.valueOf(ownLocationId))
        .header("location-api-key", appProperties.getLocationApiKey())
        .retrieve()
        .body(new ParameterizedTypeReference<List<UserSongCreditUsageDto>>() {});

    if (usages != null) {
      for (UserSongCreditUsageDto usage : usages) {
        financialLedgerService.receiveMobileCreditUsage(usage);
      }
    }
    fullMobileCatchUpNeeded.set(false);
  }

  // The whole open period on a full catch-up; otherwise just the overlap window past the latest
  // spend already mirrored (never reaching back before the open period).
  private Instant catchUpSince(Instant openPeriodStart, Instant latestMirrored) {

    if (fullMobileCatchUpNeeded.get() || latestMirrored == null) {
      return openPeriodStart;
    }
    Instant overlapStart = latestMirrored.minus(MOBILE_CATCH_UP_OVERLAP);
    return overlapStart.isAfter(openPeriodStart) ? overlapStart : openPeriodStart;
  }
}
