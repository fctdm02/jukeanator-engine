package com.djt.jukeanator_engine.domain.songqueue.dto;

public class ChangeSongQueueRequest {

  private Integer albumId;
  private Integer songId;
  /** Optional: caller's known queue position, used to disambiguate duplicate songs. */
  private Integer queuePosition;

  public ChangeSongQueueRequest() {}

  public ChangeSongQueueRequest(Integer albumId, Integer songId) {
    this.albumId = albumId;
    this.songId = songId;
  }

  public ChangeSongQueueRequest(Integer albumId, Integer songId, Integer queuePosition) {
    this.albumId = albumId;
    this.songId = songId;
    this.queuePosition = queuePosition;
  }

  public Integer getAlbumId() {
    return albumId;
  }

  public Integer getSongId() {
    return songId;
  }

  public Integer getQueuePosition() {
    return queuePosition;
  }
}
