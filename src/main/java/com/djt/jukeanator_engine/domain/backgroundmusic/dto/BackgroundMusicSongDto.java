package com.djt.jukeanator_engine.domain.backgroundmusic.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Plain, human-readable JSON representation of a {@code BackgroundMusicSongEntity}. Kept free of
 * any {@code AbstractEntity} machinery so the persisted file only ever contains the fields below.
 *
 * @author tmyers
 */
public record BackgroundMusicSongDto(Integer persistentIdentity, String songFilePath,
    Instant timeLastPlayed, int numberOfPlays) implements Serializable {

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    BackgroundMusicSongDto other = (BackgroundMusicSongDto) obj;
    return Objects.equals(persistentIdentity, other.persistentIdentity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(persistentIdentity);
  }
}
