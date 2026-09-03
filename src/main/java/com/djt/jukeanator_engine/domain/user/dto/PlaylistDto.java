package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Plain, human-readable JSON representation of a {@code PlaylistEntity}, nested under its owning
 * {@link UserDto}.
 */
public record PlaylistDto(Integer persistentIdentity, String owner, String name,
    List<SongIdentifier> songs) implements Serializable {

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    PlaylistDto other = (PlaylistDto) obj;
    return Objects.equals(name, other.name) && Objects.equals(owner, other.owner);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, owner);
  }
}
