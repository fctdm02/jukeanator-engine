package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Plain, human-readable JSON representation of a {@code PlaylistEntity}, nested under its owning
 * {@link UserDto}.
 */
public class PlaylistDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private Integer persistentIdentity;
  private String owner;
  private String name;
  private List<SongIdentifier> songs = new ArrayList<>();

  public PlaylistDto() {}

  public PlaylistDto(Integer persistentIdentity, String owner, String name,
      List<SongIdentifier> songs) {
    this.persistentIdentity = persistentIdentity;
    this.owner = owner;
    this.name = name;
    this.songs = songs;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }

  public void setPersistentIdentity(Integer persistentIdentity) {
    this.persistentIdentity = persistentIdentity;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<SongIdentifier> getSongs() {
    return songs;
  }

  public void setSongs(List<SongIdentifier> songs) {
    this.songs = songs;
  }

  public boolean addSong(SongIdentifier songIdentifier) {
    return this.songs.add(songIdentifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, owner);
  }

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
  public String toString() {
    return "PlaylistDto [persistentIdentity=" + persistentIdentity + ", owner=" + owner
        + ", name=" + name + ", songs=" + songs + "]";
  }
}
