package com.djt.jukeanator_engine.domain.songlibrary.exception;

public class SongLibraryServiceException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SongLibraryServiceException(String message) {
    super(message);
  }
  
  public SongLibraryServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
