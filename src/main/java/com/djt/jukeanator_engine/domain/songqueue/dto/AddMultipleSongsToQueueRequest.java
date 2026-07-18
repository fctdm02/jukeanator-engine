package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddMultipleSongsToQueueRequest {

  private String username;
  private List<SongIdentifier> songIdentifiers;
  private Integer priority;

  /** See {@link AddSongToQueueRequest} for why this needs an explicit {@code @JsonCreator}. */
  @JsonCreator
  public AddMultipleSongsToQueueRequest(@JsonProperty("username") String username,
      @JsonProperty("songIdentifiers") List<SongIdentifier> songIdentifiers,
      @JsonProperty("priority") Integer priority) {
    this.username = username;
    this.songIdentifiers = songIdentifiers;
    this.priority = priority;
  }

  public String getUsername() {
    return username;
  }

  public List<SongIdentifier> getSongIdentifiers() {
    return songIdentifiers;
  }

  public Integer getPriority() {
    return priority;
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, priority, songIdentifiers);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AddMultipleSongsToQueueRequest other = (AddMultipleSongsToQueueRequest) obj;
    return Objects.equals(priority, other.priority) && Objects.equals(username, other.username)
        && Objects.equals(songIdentifiers, other.songIdentifiers);
  }

  @Override
  public String toString() {
    return "AddMultipleSongsToQueueRequest [username=" + username + ", songIdentifiers="
        + songIdentifiers + ", priority=" + priority + "]";
  }

}
