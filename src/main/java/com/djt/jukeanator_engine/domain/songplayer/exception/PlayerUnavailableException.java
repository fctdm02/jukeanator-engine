package com.djt.jukeanator_engine.domain.songplayer.exception;

/**
 * Thrown by a player when it cannot play anything right now for a reason unrelated to the song
 * itself (e.g. Winamp could not be relaunched). The caller should retry the same song later rather
 * than discarding it.
 */
public class PlayerUnavailableException extends SongPlayerServiceException {
  private static final long serialVersionUID = 1L;

  public PlayerUnavailableException(String message) {
    super(message);
  }

  public PlayerUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
