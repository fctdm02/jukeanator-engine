package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddSongToQueueRequest {

  private String username;
  private Integer albumId;
  private Integer songId;
  private Integer priority;

  /**
   * Explicit {@code @JsonCreator}/{@code @JsonProperty} rather than relying on Jackson's implicit
   * single-constructor binding — that requires the {@code jackson-module-parameter-names} module
   * (registers constructor parameter names via reflection), which is on Spring MVC's
   * auto-configured {@code ObjectMapper} but not this app's own shared bean (see
   * {@code ObjectMappers}), so a manually-invoked {@code ObjectMapper.readValue(...)} (e.g.
   * {@code SlaveConnectionManager} decoding a relayed command) would otherwise fail.
   */
  @JsonCreator
  public AddSongToQueueRequest(@JsonProperty("username") String username,
      @JsonProperty("albumId") Integer albumId, @JsonProperty("songId") Integer songId,
      @JsonProperty("priority") Integer priority) {
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
