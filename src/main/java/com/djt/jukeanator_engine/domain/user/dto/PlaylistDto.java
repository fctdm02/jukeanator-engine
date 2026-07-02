package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

public class PlaylistDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private String owner;
  private String name;
  private List<SongIdentifier> songs;

  public PlaylistDto(String owner, String name, List<SongIdentifier> songs) {
    this.owner = owner;
    this.name = name;
    this.owner = owner;
    this.songs = songs;
  }

  public String getOwner() {
    return owner;
  }

  public String getName() {
    return name;
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
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    PlaylistDto other = (PlaylistDto) obj;
    return Objects.equals(name, other.name) && Objects.equals(owner, other.owner);
  }

  @Override
  public String toString() {
    return "PlaylistDto [owner=" + owner + ", name=" + name + ", songs=" + songs + "]";
  }

}
