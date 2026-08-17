package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Plain, human-readable JSON representation of a {@code SongQueueEntryEntity}, nested under the
 * singleton {@link SongQueueRootDto}. Stores {@code albumId}/{@code songId} rather than the full
 * {@code SongFileEntity}, since the song is resolved back against the live song library on load.
 * {@code songPath} is a read-convenience mirror of the resolved song's full path only, so a human
 * can see what's in the playlist JSON at a glance; it is not used to resolve the song on load.
 */
public class SongQueueEntryPersistenceDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private String username;
  private Integer albumId;
  private Integer songId;
  private String songPath;
  private Integer priority;
  private Instant queuedAtTime;

  public SongQueueEntryPersistenceDto() {}

  public SongQueueEntryPersistenceDto(String username, Integer albumId, Integer songId,
      String songPath, Integer priority, Instant queuedAtTime) {
    this.username = username;
    this.albumId = albumId;
    this.songId = songId;
    this.songPath = songPath;
    this.priority = priority;
    this.queuedAtTime = queuedAtTime;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
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

  public String getSongPath() {
    return songPath;
  }

  public void setSongPath(String songPath) {
    this.songPath = songPath;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public Instant getQueuedAtTime() {
    return queuedAtTime;
  }

  public void setQueuedAtTime(Instant queuedAtTime) {
    this.queuedAtTime = queuedAtTime;
  }

  @Override
  public String toString() {
    return "SongQueueEntryPersistenceDto [username=" + username + ", albumId=" + albumId
        + ", songId=" + songId + ", songPath=" + songPath + ", priority=" + priority
        + ", queuedAtTime=" + queuedAtTime + "]";
  }
}
