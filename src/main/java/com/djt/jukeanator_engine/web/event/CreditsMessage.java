package com.djt.jukeanator_engine.web.event;

/** A patron's new credit balance at {@code locationId} (credits are per location). */
public record CreditsMessage(Integer locationId, int numCredits) {
}
