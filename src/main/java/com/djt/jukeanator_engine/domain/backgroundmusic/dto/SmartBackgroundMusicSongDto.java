package com.djt.jukeanator_engine.domain.backgroundmusic.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartAdditionReason;

/**
 * Plain, human-readable JSON representation of a {@code SmartBackgroundMusicSongEntity}.
 *
 * @author tmyers
 */
public record SmartBackgroundMusicSongDto(Integer persistentIdentity, String songFilePath,
    Instant timeLastPlayed, int numberOfPlays, String sourceSong, Integer sourceSongNumPlays,
    SmartAdditionReason reason) implements Serializable {

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    SmartBackgroundMusicSongDto other = (SmartBackgroundMusicSongDto) obj;
    return Objects.equals(persistentIdentity, other.persistentIdentity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(persistentIdentity);
  }
}
