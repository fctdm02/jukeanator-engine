package com.djt.jukeanator_engine.web.event;

/** A web user's new credit balance, sent to {@code /user/queue/credits}. */
public record CreditsMessage(int numCredits) {
}
