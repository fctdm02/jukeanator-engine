package com.djt.jukeanator_engine.domain.location.event;

/**
 * Published by {@code LocationServiceImpl.reconcileOwnLocationId} once this instance's own
 * location has been re-keyed from {@code previousLocationId} to {@code confirmedLocationId} (see
 * {@code LocationRepository#changeLocationId}, which re-points the persisted rows). Every service
 * holding location-tagged state in memory listens for it and re-tags that state to match, so its
 * next store doesn't write the previous id back over the re-pointed rows.
 */
public record OwnLocationIdChangedEvent(Integer previousLocationId, Integer confirmedLocationId) {
}
