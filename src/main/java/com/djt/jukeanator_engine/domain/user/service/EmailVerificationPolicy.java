package com.djt.jukeanator_engine.domain.user.service;

import java.time.Duration;
import java.util.Objects;

/**
 * How {@link UserServiceImpl} handles Web/Mobile UI registrations: when {@code required}, a new
 * account starts PENDING and cannot log in until its owner opens the link emailed to them.
 *
 * @param required {@code email.verification-required}
 * @param baseUrl {@code email.verification-base-url}: the public web UI URL the link points at;
 *        blank to use the URL the registration request itself arrived on
 * @param tokenTtl {@code email.verification-token-ttl}: how long a link stays valid
 */
public record EmailVerificationPolicy(boolean required, String baseUrl, Duration tokenTtl) {

  /** Registration logs the new user straight in, as it did before verification existed. */
  public static final EmailVerificationPolicy DISABLED =
      new EmailVerificationPolicy(false, null, Duration.ofHours(24));

  public EmailVerificationPolicy {
    Objects.requireNonNull(tokenTtl, "tokenTtl cannot be null");
  }
}
