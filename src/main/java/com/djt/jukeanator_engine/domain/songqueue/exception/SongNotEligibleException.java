package com.djt.jukeanator_engine.domain.songqueue.exception;

/**
 * Thrown when a Web/Mobile UI patron tries to queue a song the queue's rules currently refuse --
 * already played or queued too recently, too many in a row by one artist, or explicit outside the
 * allowed hours. Always thrown before the queue changes, so nothing is charged. The message is
 * written for the patron.
 */
public class SongNotEligibleException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SongNotEligibleException(String message) {
    super(message);
  }
}
