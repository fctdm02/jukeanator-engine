package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LoadPlaylistIntoQueueRequest {

  private String username;
  private String filename;

  public LoadPlaylistIntoQueueRequest() {}

  /** See {@link AddSongToQueueRequest} for why this needs an explicit {@code @JsonCreator} — there
   * are no setters, so the no-arg constructor above alone isn't enough for Jackson to populate
   * this from JSON without it. */
  @JsonCreator
  public LoadPlaylistIntoQueueRequest(@JsonProperty("username") String username,
      @JsonProperty("filename") String filename) {
    this.username = username;
    this.filename = filename;
  }

  public String getUsername() {
    return username;
  }

  public String getFilename() {
    return filename;
  }

  @Override
  public int hashCode() {
    return Objects.hash(filename, username);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    LoadPlaylistIntoQueueRequest other = (LoadPlaylistIntoQueueRequest) obj;
    return Objects.equals(filename, other.filename) && Objects.equals(username, other.username);
  }

  @Override
  public String toString() {
    return "LoadPlaylistIntoQueueRequest [username=" + username + ", filename=" + filename + "]";
  }
}
