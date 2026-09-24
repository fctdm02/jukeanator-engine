package com.djt.jukeanator_engine.domain.useractivity.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.common.security.SecurityContextPropagatingRunnable;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.useractivity.model.PendingUserActivity;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import jakarta.annotation.PreDestroy;

/**
 * Slave-only. Mirrors this slave's user-activity log up to master through the same outbox pattern
 * {@code FinancialLedgerSyncService} uses for the financial ledger: every record stays in {@link
 * UserActivityService#getPendingMasterSync} until master acknowledges it, so nothing captured
 * while master is unreachable is lost -- it is simply pushed on a later sweep, and master's copy is
 * idempotent on {@code (locationId, activityId)}, so a re-push after a lost acknowledgement is a
 * safe no-op.
 *
 * <p>Activity is high-frequency (every tab switch, every pagination click), so unlike the
 * financial ledger it is never pushed per event: a sweep every {@link #SWEEP_INTERVAL_SECONDS}
 * seconds drains the outbox in batches of up to {@link #BATCH_SIZE}, one POST per batch. A batch
 * is acknowledged only as a whole, and the first failure (master unreachable or refusing) ends the
 * sweep -- the outbox is ordered, so nothing may be skipped past. Failures are logged, never
 * thrown; the next sweep retries.
 *
 * @author tmyers
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "slave")
public class UserActivitySyncService {

  private static final Logger log = LoggerFactory.getLogger(UserActivitySyncService.class);

  static final long SWEEP_INTERVAL_SECONDS = 60;
  static final int BATCH_SIZE = 500;

  // Bounds one sweep's work (e.g. a large backlog after a long outage) so it never runs
  // unbounded; the remainder is picked up by the next sweep.
  static final int MAX_BATCHES_PER_SWEEP = 20;

  private final AppProperties appProperties;
  private final UserActivityService userActivityService;
  private final SongLibraryService songLibraryService;
  private final RestClient restClient;

  private final ScheduledExecutorService syncExecutor =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "user-activity-sync-thread");
        t.setDaemon(true);
        return t;
      });

  public UserActivitySyncService(AppProperties appProperties,
      UserActivityService userActivityService, SongLibraryService songLibraryService) {

    this.appProperties = appProperties;
    this.userActivityService = userActivityService;
    this.songLibraryService = songLibraryService;
    this.restClient = RestClient.builder().baseUrl(appProperties.getMasterInstanceUrl()).build();

    syncExecutor.scheduleWithFixedDelay(this::sweep, SWEEP_INTERVAL_SECONDS,
        SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void shutdown() {
    syncExecutor.shutdownNow();
  }

  /** Queues one sweep now, on the sync executor -- production relies on the schedule alone. */
  void sweepNow() {
    syncExecutor.execute(this::sweep);
  }

  // Runs only on syncExecutor's single thread, so sweeps never overlap. UserActivityService calls
  // go through ServiceSecurityAspect, which needs an authenticated principal on this
  // otherwise-empty background thread.
  private void sweep() {

    new SecurityContextPropagatingRunnable(() -> {
      try {
        for (int batch = 0; batch < MAX_BATCHES_PER_SWEEP; batch++) {
          if (!pushNextBatch()) {
            return;
          }
        }
      } catch (Throwable t) {
        // Must never let a sync failure propagate anywhere that could affect the slave's own
        // operation -- the next sweep retries.
        log.debug("User-activity sync with master at " + appProperties.getMasterInstanceUrl()
            + " did not complete; will retry", t);
      }
    }, SystemPrincipal.SystemAuthenticationToken.INSTANCE).run();
  }

  /** Pushes and acknowledges one batch; {@code false} once there is nothing more to push. */
  private boolean pushNextBatch() {

    List<PendingUserActivity> pending = userActivityService.getPendingMasterSync(BATCH_SIZE);
    if (pending.isEmpty()) {
      return false;
    }

    // An activity captured before this slave's own location was established carries no
    // locationId -- attribute it to the (now known) own location rather than never mirroring it.
    Integer ownLocationId = songLibraryService.getOwnLocationId();
    Map<Integer, List<PendingUserActivity>> byLocation = pending.stream()
        .filter(p -> effectiveLocationId(p, ownLocationId) != null)
        .collect(Collectors.groupingBy(p -> effectiveLocationId(p, ownLocationId),
            LinkedHashMap::new, Collectors.toList()));
    if (byLocation.isEmpty()) {
      return false; // nothing attributable yet -- wait for this slave's own location
    }

    for (Map.Entry<Integer, List<PendingUserActivity>> group : byLocation.entrySet()) {
      push(group.getKey(), group.getValue().stream().map(PendingUserActivity::record).toList());
      userActivityService.markSyncedToMaster(group.getValue());
    }
    return pending.size() == BATCH_SIZE;
  }

  private static Integer effectiveLocationId(PendingUserActivity pending, Integer ownLocationId) {
    return pending.record().locationId() != null ? pending.record().locationId() : ownLocationId;
  }

  private void push(Integer locationId, List<UserActivityRecord> records) {

    restClient.post()
        .uri("/api/locations/{locationId}/user-activity/sync", locationId)
        .header("location-id", String.valueOf(locationId))
        .header("location-api-key", appProperties.getLocationApiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(records)
        .retrieve()
        .toBodilessEntity();
  }
}
