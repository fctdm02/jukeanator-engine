package com.djt.jukeanator_engine.domain.user.exception;

/**
 * Thrown when Change Password is given the wrong current password. Deliberately a 400 rather than
 * a 401 (see {@code GlobalExceptionHandler}): the Web/Mobile UI treats any 401 as an expired
 * session and signs the patron out. The message is written for the patron.
 */
public class IncorrectPasswordException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IncorrectPasswordException(String message) {
    super(message);
  }
}
