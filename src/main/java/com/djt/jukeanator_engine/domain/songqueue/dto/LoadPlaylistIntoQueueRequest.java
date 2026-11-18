package com.djt.jukeanator_engine.domain.songqueue.dto;

/**
 * @param priority the queue priority for the loaded songs; null means priority 0 (the same as
 *        background music)
 */
public record LoadPlaylistIntoQueueRequest(String username, String filename, Integer priority) {

  public LoadPlaylistIntoQueueRequest(String username, String filename) {
    this(username, filename, null);
  }
}
