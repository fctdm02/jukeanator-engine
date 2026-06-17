package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.Objects;

public class LoadPlaylistIntoQueueRequest {

  private String username;
  private String filename;

  public LoadPlaylistIntoQueueRequest() {}

  public LoadPlaylistIntoQueueRequest(String username, String filename) {
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
