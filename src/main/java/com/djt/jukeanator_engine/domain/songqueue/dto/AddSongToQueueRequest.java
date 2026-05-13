package com.djt.jukeanator_engine.domain.songqueue.dto;

public class AddSongToQueueRequest {

  private Integer albumId;
  private Integer songId;
  private Integer priority;

  public AddSongToQueueRequest() {}

  public AddSongToQueueRequest(Integer albumId, Integer songId, Integer priority) {
    this.albumId = albumId;
    this.songId = songId;
    this.priority = priority;
  }

  public Integer getAlbumId() {
    return albumId;
  }

  public void setAlbumId(Integer albumId) {
    this.albumId = albumId;
  }

  public Integer getSongId() {
    return songId;
  }

  public void setSongId(Integer songId) {
    this.songId = songId;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }
}
