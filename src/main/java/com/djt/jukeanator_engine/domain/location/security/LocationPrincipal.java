package com.djt.jukeanator_engine.domain.location.security;

import java.security.Principal;

/** Minimal {@link Principal} that carries only the locationId, mirroring the browser-facing
 * {@code StompJwtChannelInterceptor}'s email-carrying principal but keyed by locationId instead —
 * this is what lets master route {@code convertAndSendToUser(locationId, ...)} to the right
 * slave's session. {@link #getName()} must return a {@code String} per the {@link Principal}
 * contract, so it's the decimal form of {@link #locationId}. */
public record LocationPrincipal(Integer locationId) implements Principal {
  @Override
  public String getName() {
    return String.valueOf(locationId);
  }
}
