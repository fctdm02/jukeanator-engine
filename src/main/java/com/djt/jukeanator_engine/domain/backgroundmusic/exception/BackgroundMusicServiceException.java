package com.djt.jukeanator_engine.domain.backgroundmusic.exception;

public class BackgroundMusicServiceException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public BackgroundMusicServiceException(String message) {
    super(message);
  }

  public BackgroundMusicServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
