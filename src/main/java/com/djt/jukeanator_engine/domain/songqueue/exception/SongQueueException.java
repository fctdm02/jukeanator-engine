package com.djt.jukeanator_engine.domain.songqueue.exception;

public class SongQueueException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SongQueueException(String message) {
    super(message);
  }
  
  public SongQueueException(String message, Throwable cause) {
    super(message, cause);
  }
}
