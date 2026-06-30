package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.io.Serializable;
import java.util.Objects;

public class SongIdentifier implements Serializable {

  private static final long serialVersionUID = 1L;
  
  private Integer albumId;
  private Integer songId;

  public SongIdentifier(Integer albumId, Integer songId) {
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
