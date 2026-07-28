package com.djt.jukeanator_engine.domain.user.model;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

@Entity
@Table(name = "playlists")
public class PlaylistEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  public static final String MY_FAVORITES_PLAYLIST_NAME = "My Favorites";

  // Persistence-only back-reference -- the FK column JPA needs to own the UserEntity <-> playlist
  // relationship. Domain logic identifies ownership via the `owner` (email) column below, exactly
  // as it did before this class was JPA-mapped; UserEntity.createPlaylist() is the single place
  // that keeps this back-reference in sync (see setUser()).
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id")
  private UserEntity user;

  @Column(nullable = false)
  private String owner;

  @Column(nullable = false)
  private String name;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "playlist_songs", joinColumns = @JoinColumn(name = "playlist_id"))
  @OrderColumn(name = "song_order")
  private List<SongIdentifier> songs;

  protected PlaylistEntity() {} // for JPA

  public PlaylistEntity(Integer persistentIdentity, String owner, String name) {
    this(persistentIdentity, owner, name, new ArrayList<>());
  }

  public PlaylistEntity(Integer persistentIdentity, String owner, String name,
      List<SongIdentifier> songs) {
    super(persistentIdentity);
    this.owner = owner;
    this.name = name;
    this.owner = owner;
    this.songs = songs;
  }

  void setUser(UserEntity user) {
    this.user = user;
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

  public boolean removeSong(SongIdentifier songIdentifier) {
    return this.songs.remove(songIdentifier);
  }

  @Override
  public String getNaturalIdentity() {
    return new StringBuilder().append(this.owner).append(this.name).toString();
  }
}
