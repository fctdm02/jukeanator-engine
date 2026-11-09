package com.djt.jukeanator_engine.domain.license.service;

/** A licensing operation failed; the message is suitable for showing to the operator. */
public class LicenseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LicenseException(String message) {
    super(message);
  }

  public LicenseException(String message, Throwable cause) {
    super(message, cause);
  }
}
