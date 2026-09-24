package com.djt.jukeanator_engine.domain.useractivity.model;

import java.time.Instant;
import java.util.Map;

/**
 * One captured user-activity event, ready to hand to {@link
 * com.djt.jukeanator_engine.domain.useractivity.repository.UserActivityRepository#record}. Not
 * JPA-mapped itself -- {@code UserActivityRepositoryJpaImpl} converts it to/from {@link
 * UserActivityEntity}, and {@code UserActivityRepositoryFileSystemImpl} serializes it directly to
 * JSON.
 *
 * @param locationId the physical jukebox location this activity happened at
 * @param source which UI the activity originated from
 * @param username the acting user -- {@code SongQueueService.LOCAL_USERNAME} for Swing/local
 *        activity, the authenticated user's email for mobile/web activity
 * @param activityType what kind of activity this is
 * @param occurredAt when the activity happened
 * @param details activity-specific attributes (e.g. tab name, search query, artist/album/song id)
 *        -- shape varies by {@code activityType}, never {@code null} (empty map when there's
 *        nothing further to capture)
 * @param activityId a UUID minted by {@code UserActivityServiceImpl} when the activity is
 *        captured -- the slave-to-master mirror's idempotency key. {@code null} only for records
 *        written before it existed (the mirror derives a stable stand-in for those; see {@code
 *        UserActivityRepository#findPendingMasterSync}).
 */
public record UserActivityRecord(Integer locationId, UserActivitySource source, String username,
    UserActivityType activityType, Instant occurredAt, Map<String, Object> details,
    String activityId) {

  /** A record with no {@code activityId} yet. */
  public UserActivityRecord(Integer locationId, UserActivitySource source, String username,
      UserActivityType activityType, Instant occurredAt, Map<String, Object> details) {
    this(locationId, source, username, activityType, occurredAt, details, null);
  }

  /** This same record, attributed to {@code newLocationId} and carrying {@code newActivityId}. */
  public UserActivityRecord with(Integer newLocationId, String newActivityId) {
    return new UserActivityRecord(newLocationId, source, username, activityType, occurredAt,
        details, newActivityId);
  }
}
