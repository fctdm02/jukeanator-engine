package com.djt.jukeanator_engine.domain.user.event;

/** A patron's credit balance at {@code locationId} changed to {@code numCredits}. */
public record UserCreditsChangedEvent(String emailAddress, Integer locationId, int numCredits) {
}
