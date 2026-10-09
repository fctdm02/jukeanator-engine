package com.djt.jukeanator_engine.domain.user.event;

import java.time.Instant;

/**
 * Published by {@code UserServiceImpl} when a PENDING account needs its verification link emailed
 * -- on registration and on a resend request. {@code verificationUrl} carries the raw token, so
 * this event must never be logged or persisted.
 */
public record VerificationEmailRequestedEvent(String emailAddress, String firstName,
    String verificationUrl, Instant expiresAt) {
}
