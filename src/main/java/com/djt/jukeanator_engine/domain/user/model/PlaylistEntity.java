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

/**
 * A patron's playlist at one location -- owned by a {@link UserLocationAccountEntity}, so every
 * song in it is from that location's library. Names are unique within the account.
 */
@Entity
@Table(name = "user_playlist")
public class PlaylistEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  public static final String MY_FAVORITES_PLAYLIST_NAME = "My Favorites";

  // Persistence-only back-reference -- UserLocationAccountEntity.createPlaylist()/
  // restorePlaylist() keep it in sync.
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_location_account_id", nullable = false)
  private UserLocationAccountEntity locationAccount;

  @Column(nullable = false)
  private String name;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_playlist_song", joinColumns = @JoinColumn(name = "playlist_id"))
  @OrderColumn(name = "song_order")
  private List<SongIdentifier> songs;

  protected PlaylistEntity() {} // for JPA

  public PlaylistEntity(Integer persistentIdentity, String name) {
    this(persistentIdentity, name, new ArrayList<>());
  }

  public PlaylistEntity(Integer persistentIdentity, String name, List<SongIdentifier> songs) {
    super(persistentIdentity);
    this.name = name;
    this.songs = songs == null ? new ArrayList<>() : songs;
  }

  void setLocationAccount(UserLocationAccountEntity locationAccount) {
    this.locationAccount = locationAccount;
  }

  public Integer getLocationId() {
    return locationAccount != null ? locationAccount.getLocationId() : null;
  }

  public String getOwner() {
    return locationAccount != null ? locationAccount.getUserEmail() : null;
  }

  public String getName() {
    return name;
  }

  // Package-private: only UserLocationAccountEntity.renamePlaylist renames a playlist (it
  // enforces uniqueness).
  void setName(String name) {
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

  public boolean removeSong(SongIdentifier songIdentifier) {
    return this.songs.remove(songIdentifier);
  }

  // Package-private: only UserLocationAccountEntity.changeLocationId re-tags a playlist.
  void changeLocationId(Integer oldLocationId, Integer newLocationId) {
    if (this.songs != null) {
      this.songs.replaceAll(s -> s.withLocationIdChanged(oldLocationId, newLocationId));
    }
  }

  @Override
  public String getNaturalIdentity() {
    return getOwner() + "@" + getLocationId() + "/" + this.name;
  }
}
