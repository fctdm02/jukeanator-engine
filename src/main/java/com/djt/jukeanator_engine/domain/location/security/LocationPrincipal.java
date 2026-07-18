package com.djt.jukeanator_engine.domain.location.security;

import java.security.Principal;

/** Minimal {@link Principal} that carries only the locationId, mirroring the browser-facing
 * {@code StompJwtChannelInterceptor}'s email-carrying principal but keyed by locationId instead —
 * this is what lets master route {@code convertAndSendToUser(locationId, ...)} to the right
 * slave's session. */
public record LocationPrincipal(String name) implements Principal {
  @Override
  public String getName() {
    return name;
  }
}
