package com.djt.jukeanator_engine.domain.songlibrary.exception;

public class SongLibraryException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SongLibraryException(String message) {
    super(message);
  }
  
  public SongLibraryException(String message, Throwable cause) {
    super(message, cause);
  }
}
