package com.djt.jukeanator_engine.domain.useractivity.model;

/**
 * One activity record this slave captured that master has not yet acknowledged, as returned by
 * {@code UserActivityRepository.findPendingMasterSync}. {@code record.activityId()} is always set
 * here (a stable stand-in is derived for records written before activity ids existed). {@code
 * cursor} is an opaque, repository-specific position handed back to {@code
 * UserActivityRepository.markSyncedToMaster} -- the row id under JPA, the day-file and byte offset
 * under the filesystem repository.
 */
public record PendingUserActivity(String cursor, UserActivityRecord record) {
}
