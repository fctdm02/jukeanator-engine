package com.djt.jukeanator_engine.domain.common.exception;

/**
 * An unchecked "no such thing" -- an album, artist, song or location id that does not exist, e.g.
 * one a mobile app cached before the library was rescanned. Mapped to 404 by
 * {@code GlobalExceptionHandler}, as opposed to the 500 an unexpected failure gets. The unchecked
 * counterpart to {@link EntityDoesNotExistException}, for service methods whose signatures do not
 * declare it.
 */
public class ResourceNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ResourceNotFoundException(String message) {
    super(message);
  }

  public ResourceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
