package com.djt.jukeanator_engine.domain.songqueue.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

  /** See {@code AddSongToQueueRequest} for why this needs an explicit {@code @JsonCreator} — there
   * are no setters, so the no-arg constructor above alone isn't enough for Jackson to populate
   * this from JSON without it. */
  @JsonCreator
  public ChangeSongQueueRequest(@JsonProperty("albumId") Integer albumId,
      @JsonProperty("songId") Integer songId,
      @JsonProperty("queuePosition") Integer queuePosition) {
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
