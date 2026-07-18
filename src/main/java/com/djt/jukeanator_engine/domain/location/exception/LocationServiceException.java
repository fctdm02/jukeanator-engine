package com.djt.jukeanator_engine.domain.location.exception;

public class LocationServiceException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public LocationServiceException(String message) {
    super(message);
  }

  public LocationServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
