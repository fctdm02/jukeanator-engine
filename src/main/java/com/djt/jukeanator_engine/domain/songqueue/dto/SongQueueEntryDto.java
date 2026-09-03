package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.io.Serializable;
import java.util.Objects;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

public record SongQueueEntryDto(String username, SongDto song, Integer priority,
    String songPath) implements Serializable {

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    SongQueueEntryDto other = (SongQueueEntryDto) obj;
    return Objects.equals(songPath, other.songPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(songPath);
  }
}
