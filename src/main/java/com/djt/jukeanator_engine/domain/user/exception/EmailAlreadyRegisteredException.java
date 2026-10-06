package com.djt.jukeanator_engine.domain.user.exception;

/**
 * Thrown when registering an email address that already has an account. The message is written for
 * the patron.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public EmailAlreadyRegisteredException(String message) {
    super(message);
  }
}
