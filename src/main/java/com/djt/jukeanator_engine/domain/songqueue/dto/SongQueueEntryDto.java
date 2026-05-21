package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.Objects;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

public class SongQueueEntryDto {

  private SongDto song;
  private Integer priority;
  private String songPath;

  public SongQueueEntryDto(
      SongDto song,
      Integer priority,
      String songPath) {
    super();
    this.song = song;
    this.priority = priority;
    this.songPath = songPath;
  }

  public SongDto getSong() {
    return song;
  }

  public Integer getPriority() {
    return priority;
  }
  
  public String getSongPath() {
    return songPath;
  }

  @Override
  public int hashCode() {
    return Objects.hash(songPath);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SongQueueEntryDto other = (SongQueueEntryDto) obj;
    return Objects.equals(songPath, other.songPath);
  }

  @Override
  public String toString() {
    return "SongQueueEntryDto [song=" + song + ", priority=" + priority + ", songPath=" + songPath
        + "]";
  }  
}
