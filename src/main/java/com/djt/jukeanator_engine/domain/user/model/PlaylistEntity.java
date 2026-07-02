package com.djt.jukeanator_engine.domain.user.model;

import java.util.List;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

public class PlaylistEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  private String owner;
  private String name;
  private List<SongIdentifier> songs;

  public PlaylistEntity(Integer persistentIdentity, String owner, String name,
      List<SongIdentifier> songs) {
    super(persistentIdentity);
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
  public String getNaturalIdentity() {
    return new StringBuilder().append(this.owner).append(this.name).toString();
  }
}
