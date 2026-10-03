package com.djt.jukeanator_engine.domain.location.exception;

import static java.util.Objects.requireNonNull;

/**
 * Thrown when a Web/Mobile UI patron attempts a queue operation at a geo-fenced location without
 * a usable device position inside the fence. Mapped to HTTP 403 by {@code GlobalExceptionHandler};
 * the message is written for display to the patron.
 */
public class GeoFenceViolationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final GeoFenceViolationReason reason;

  public GeoFenceViolationException(GeoFenceViolationReason reason, String message) {
    super(message);
    requireNonNull(reason, "reason cannot be null");
    this.reason = reason;
  }

  public GeoFenceViolationReason getReason() {
    return reason;
  }
}
