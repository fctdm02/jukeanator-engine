package com.djt.jukeanator_engine.domain.songplayer.exception;

public class SongPlayerServiceException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SongPlayerServiceException(String message) {
    super(message);
  }
  
  public SongPlayerServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
