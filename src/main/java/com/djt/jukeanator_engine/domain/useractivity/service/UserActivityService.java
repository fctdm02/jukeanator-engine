package com.djt.jukeanator_engine.domain.useractivity.service;

import java.util.List;
import java.util.Map;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.useractivity.model.PendingUserActivity;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;

/**
 * Captures a user-activity event and hands it off for asynchronous persistence -- see {@code
 * UserActivityServiceImpl} and {@code UserActivityEventListener}. Every {@code record*} call
 * returns immediately; it never performs the actual file/DB write on the calling thread.
 */
public interface UserActivityService {

  /**
   * Records an activity event.
   *
   * @param locationId the physical jukebox location this activity happened at
   * @param source which UI the activity originated from
   * @param username the acting user
   * @param activityType what kind of activity this is
   * @param details activity-specific attributes; may be {@code null} (recorded as an empty map)
   */
  void record(Integer locationId, UserActivitySource source, String username,
      UserActivityType activityType, Map<String, Object> details);

  /**
   * Convenience for the Swing/JFC desktop UI, which always records as {@link
   * UserActivitySource#SWING_UI} under the {@link SongQueueService#LOCAL_USERNAME} sentinel --
   * saves every call site from repeating those two constants.
   */
  default void recordSwingActivity(Integer locationId, UserActivityType activityType,
      Map<String, Object> details) {

    record(locationId, UserActivitySource.SWING_UI, SongQueueService.LOCAL_USERNAME, activityType,
        details);
  }

  /**
   * Returns up to {@code limit} of the most recent activity records for {@code locationId},
   * newest first -- backs the Admin Panel's "View Activity" screen. Unlike {@code record}, this is
   * a synchronous read straight through to the repository (nothing to decouple from a caller
   * thread for a one-shot, UI-triggered query).
   */
  List<UserActivityRecord> findRecentActivity(Integer locationId, int limit);

  /**
   * Permanently deletes activity records older than {@code retentionDays} days, across every
   * location -- called daily by {@code UserActivityPurgeScheduler} using {@code
   * UserActivityProperties#getRetentionDays()} (30 by default).
   */
  void purgeActivityOlderThan(int retentionDays);

  /**
   * Slave-only. Up to {@code limit} activity records master has not yet acknowledged, oldest first
   * -- the outbox {@code UserActivitySyncService} drains. See {@code
   * UserActivityRepository#findPendingMasterSync}.
   */
  List<PendingUserActivity> getPendingMasterSync(int limit);

  /** Slave-only. Marks {@code acknowledged} (from {@link #getPendingMasterSync}) as synced. */
  void markSyncedToMaster(List<PendingUserActivity> acknowledged);

  /**
   * Master-only. Stores a batch of activity records mirrored from {@code locationId}'s slave,
   * attributed to {@code locationId} regardless of what each record carries. Idempotent: a record
   * whose {@code (locationId, activityId)} is already stored is skipped, so a retried batch is a
   * safe no-op. Stored synchronously (unlike {@link #record}), so a successful return really does
   * mean every record is durable before the slave marks the batch acknowledged.
   *
   * @return how many records were newly stored
   * @throws LocationServiceException if {@code locationId}/{@code apiKey} don't verify
   */
  int receiveUserActivitySync(Integer locationId, String apiKey, List<UserActivityRecord> records)
      throws LocationServiceException;
}
