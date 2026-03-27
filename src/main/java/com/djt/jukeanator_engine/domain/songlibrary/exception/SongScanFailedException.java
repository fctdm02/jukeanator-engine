package com.djt.jukeanator_engine.domain.songlibrary.exception;

public class SongScanFailedException extends SongLibraryException {
  private static final long serialVersionUID = 1L;

  public SongScanFailedException(String message, Throwable cause) {
    super(message);
    initCause(cause);
  }
}
