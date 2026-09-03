package com.djt.jukeanator_engine.domain.user.exception;

/**
 * Thrown by {@link com.djt.jukeanator_engine.domain.user.service.UserService#login} when the
 * supplied email address is not registered, or the password does not match. Deliberately a
 * checked exception logged at INFO rather than WARN/ERROR — a fat-fingered login attempt is
 * routine client behavior, not an application fault.
 */
public class InvalidCredentialsException extends Exception {

  private static final long serialVersionUID = 1L;

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
