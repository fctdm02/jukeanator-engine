package com.djt.jukeanator_engine.domain.location.event;

public record LocationLibrarySyncedEvent(String locationId, int albumCount) {
}
