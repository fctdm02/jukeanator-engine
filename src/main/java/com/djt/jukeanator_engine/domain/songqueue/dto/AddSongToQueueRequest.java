package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.Objects;

public class AddSongToQueueRequest {

  private String username;
  private Integer albumId;
  private Integer songId;
  private Integer priority;

  public AddSongToQueueRequest(String username, Integer albumId, Integer songId, Integer priority) {
    this.username = username;
    this.albumId = albumId;
    this.songId = songId;
    this.priority = priority;
  }

  public String getUsername() {
    return username;
  }

  public Integer getAlbumId() {
    return albumId;
  }

  public Integer getSongId() {
    return songId;
  }

  public Integer getPriority() {
    return priority;
  }

  @Override
  public int hashCode() {
    return Objects.hash(albumId, priority, songId, username);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AddSongToQueueRequest other = (AddSongToQueueRequest) obj;
    return Objects.equals(albumId, other.albumId) && Objects.equals(priority, other.priority)
        && Objects.equals(songId, other.songId) && Objects.equals(username, other.username);
  }

  @Override
  public String toString() {
    return "AddSongToQueueRequest [username=" + username + ", albumId=" + albumId + ", songId="
        + songId + ", priority=" + priority + "]";
  }

}
