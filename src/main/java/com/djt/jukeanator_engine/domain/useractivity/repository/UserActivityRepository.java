package com.djt.jukeanator_engine.domain.useractivity.repository;

import java.time.Instant;
import java.util.List;
import com.djt.jukeanator_engine.domain.useractivity.model.PendingUserActivity;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;

/**
 * Persists and retrieves {@link UserActivityRecord}s, selected between filesystem and JPA backends
 * the same way every other domain's repository is (see {@code UserActivityConfig}).
 */
public interface UserActivityRepository {

  void record(UserActivityRecord record);

  /**
   * Returns up to {@code limit} of the most recent activity records for {@code locationId},
   * newest first -- backs the Admin Panel's "View Activity" screen.
   */
  List<UserActivityRecord> findRecentActivity(Integer locationId, int limit);

  /**
   * Permanently deletes every activity record recorded strictly before {@code cutoff}, across
   * every location -- backs the daily retention purge (see {@code UserActivityPurgeScheduler})
   * that keeps activity data bounded to a rolling window instead of growing forever.
   */
  void purgeOlderThan(Instant cutoff);

  /**
   * Re-tags every activity record stored under {@code oldLocationId} with {@code newLocationId}
   * -- called (via {@code UserActivityEventListener}) when this instance's own location id is
   * corrected post-handshake. A no-op by default -- under JPA the {@code user_activity} rows are
   * already re-pointed by {@code LocationRepositoryJpaImpl.changeLocationId}.
   */
  default void changeLocationId(Integer oldLocationId, Integer newLocationId) {
    // no-op by default
  }

  /**
   * Slave-only. Up to {@code limit} of this instance's activity records master has not yet
   * acknowledged, oldest first -- the user-activity outbox {@code UserActivitySyncService} drains.
   * Records attributed to {@code SystemPrincipal.SYSTEM_USERNAME} are never returned: they are this
   * slave's own log of a queue command master relayed on a mobile/web user's behalf, which master
   * already recorded itself under that user's email. A record with no {@code activityId} (written
   * before activity ids existed) is returned with a stable, position-derived one.
   */
  List<PendingUserActivity> findPendingMasterSync(int limit);

  /** Slave-only. Marks {@code acknowledged} (from {@link #findPendingMasterSync}) as synced. */
  void markSyncedToMaster(List<PendingUserActivity> acknowledged);

  /**
   * Master-only. Stores one mirrored slave activity record unless one with the same {@code
   * (locationId, activityId)} is already stored -- a retried push is a safe no-op.
   *
   * @return {@code true} if the record was stored, {@code false} if it was already present
   */
  boolean recordIfAbsent(UserActivityRecord record);
}
