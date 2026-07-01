package com.djt.jukeanator_engine.domain.common.security;

/**
 * Thrown when a request carries a structurally valid JWT (correct signature, not expired) whose
 * subject no longer resolves to an existing user, e.g. a token left over in the browser from
 * before the user store was reset. Callers should treat this the same as "not authenticated"
 * rather than a server error.
 */
public class InvalidPrincipalException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InvalidPrincipalException(String message) {
    super(message);
  }
}
