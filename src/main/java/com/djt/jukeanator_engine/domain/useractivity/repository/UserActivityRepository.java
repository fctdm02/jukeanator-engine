package com.djt.jukeanator_engine.domain.useractivity.repository;

import java.time.Instant;
import java.util.List;
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
}
