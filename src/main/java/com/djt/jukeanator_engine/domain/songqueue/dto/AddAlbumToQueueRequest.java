package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddAlbumToQueueRequest {

  private String username;
  private Integer albumId;
  private Integer priority;

  /** See {@link AddSongToQueueRequest} for why this needs an explicit {@code @JsonCreator}. */
  @JsonCreator
  public AddAlbumToQueueRequest(@JsonProperty("username") String username,
      @JsonProperty("albumId") Integer albumId, @JsonProperty("priority") Integer priority) {
    this.username = username;
    this.albumId = albumId;
    this.priority = priority;
  }

  public String getUsername() {
    return username;
  }

  public Integer getAlbumId() {
    return albumId;
  }

  public Integer getPriority() {
    return priority;
  }

  @Override
  public int hashCode() {
    return Objects.hash(albumId, priority, username);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AddAlbumToQueueRequest other = (AddAlbumToQueueRequest) obj;
    return Objects.equals(albumId, other.albumId) && Objects.equals(priority, other.priority)
        && Objects.equals(username, other.username);
  }

  @Override
  public String toString() {
    return "AddAlbumToQueueRequest [username=" + username + ", albumId=" + albumId + ", priority="
        + priority + "]";
  }
}
