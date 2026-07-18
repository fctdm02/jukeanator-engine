package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.io.Serializable;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SongIdentifier implements Serializable {

  private static final long serialVersionUID = 1L;

  private Integer albumId;
  private Integer songId;

  /** See {@link AddSongToQueueRequest} for why this needs an explicit {@code @JsonCreator}. */
  @JsonCreator
  public SongIdentifier(@JsonProperty("albumId") Integer albumId,
      @JsonProperty("songId") Integer songId) {
    this.albumId = albumId;
    this.songId = songId;
  }

  public Integer getAlbumId() {
    return albumId;
  }

  public Integer getSongId() {
    return songId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(albumId, songId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SongIdentifier other = (SongIdentifier) obj;
    return Objects.equals(albumId, other.albumId) && Objects.equals(songId, other.songId);
  }

  @Override
  public String toString() {
    return "SongIdentifier [albumId=" + albumId + ", songId=" + songId + "]";
  }
}
