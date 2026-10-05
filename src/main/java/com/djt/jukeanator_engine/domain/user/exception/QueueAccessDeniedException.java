package com.djt.jukeanator_engine.domain.user.exception;

/**
 * Thrown when a Web/Mobile UI patron tries to queue (or reorder/remove) a song directly on a slave
 * instance. Credits are owned by master, so a slave can never charge a web user -- patrons must
 * queue songs through master, which charges them and relays the play to the slave. Always thrown
 * before the queue changes. The message is written for the patron.
 */
public class QueueAccessDeniedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public QueueAccessDeniedException(String message) {
    super(message);
  }
}
