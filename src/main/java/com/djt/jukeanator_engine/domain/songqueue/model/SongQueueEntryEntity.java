package com.djt.jukeanator_engine.domain.songqueue.model;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import com.djt.jukeanator_engine.domain.common.model.AbstractEntity;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

public class SongQueueEntryEntity extends AbstractPersistentEntity {
  private static final long serialVersionUID = 2L;

  private SongFileEntity song;
  private Integer priority;
  private Integer indexOverride;
  private String username; // Either LOCAL for Swing UI, or a remote user's email address
  private Instant queuedAtTime;

  public SongQueueEntryEntity(String username, SongFileEntity song, Integer priority) {
    super();
    requireNonNull(username, "username cannot be null");
    requireNonNull(song, "song cannot be null");
    requireNonNull(priority, "priority cannot be null");
    this.username = username;
    this.song = song;
    this.priority = priority;
    this.queuedAtTime = Instant.now();
  }

  public SongFileEntity getSong() {
    return song;
  }

  public Integer getPriority() {
    return priority;
  }

  public Integer getIndexOverride() {
    return indexOverride;
  }

  public void setIndexOverride(Integer indexOverride) {
    this.indexOverride = indexOverride;
  }

  public String getUsername() {
    return this.username;
  }

  public Instant getQueuedAtTime() {
    return this.queuedAtTime;
  }

  @Override
  public int compareTo(AbstractEntity obj) {

    SongQueueEntryEntity that = (SongQueueEntryEntity) obj;

    int priorityCompare = this.priority.compareTo(that.priority);
    if (priorityCompare != 0) {
      return priorityCompare;
    }

    return this.song.getNaturalIdentity().compareTo(that.song.getNaturalIdentity());
  }

  @Override
  public String getNaturalIdentity() {

    return new StringBuilder().append("priority: ").append(priority).append(", username: ")
        .append(username).append(", queuedAtTime: ").append(queuedAtTime).append(", song: ")
        .append(song.getNaturalIdentity()).toString();
  }
}
