package com.djt.jukeanator_engine.domain.location.event;

/**
 * Published by {@code LocationServiceImpl.updateOwnLocationInfo} once this instance's own location
 * info (name, coordinates, logo, geo-fencing) has been saved. A slave's
 * {@code SlaveConnectionManager} listens for it and pushes the new info to master right away.
 */
public record OwnLocationInfoUpdatedEvent(Integer locationId) {
}
