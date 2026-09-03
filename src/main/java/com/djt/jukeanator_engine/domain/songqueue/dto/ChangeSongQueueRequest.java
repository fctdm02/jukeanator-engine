package com.djt.jukeanator_engine.domain.songqueue.dto;

public record ChangeSongQueueRequest(Integer albumId, Integer songId, Integer queuePosition) {

  public ChangeSongQueueRequest(Integer albumId, Integer songId) {
    this(albumId, songId, null);
  }
}
