package com.djt.jukeanator_engine.domain.location.exception;

/** Thrown when a command needs to reach a slave that has no live {@code /ws-slave} session, or
 * that never replied within {@code location.command-timeout-ms}. Mapped to HTTP 503 by
 * {@code GlobalExceptionHandler}. */
public class LocationOfflineException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public LocationOfflineException(String locationId) {
    super("Location is offline: " + locationId);
  }
}
