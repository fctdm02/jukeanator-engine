package com.djt.jukeanator_engine.domain.user.event;

public record UserCreditsChangedEvent(String emailAddress, int numCredits) {
}
