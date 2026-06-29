package com.djt.jukeanator_engine.domain.songqueue.exception;

public class SongQueueServiceException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SongQueueServiceException(String message) {
    super(message);
  }
  
  public SongQueueServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
