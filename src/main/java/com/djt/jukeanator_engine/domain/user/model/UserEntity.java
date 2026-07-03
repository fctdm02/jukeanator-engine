package com.djt.jukeanator_engine.domain.user.model;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

public class UserEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  private static final Logger log = LoggerFactory.getLogger(UserEntity.class);

  private String firstName;
  private String lastName;
  private String emailAddress;
  private String passwordHash;
  private Integer numCredits = 0;

  private List<SongIdentifier> songPlayHistory = new ArrayList<>();
  private List<String> searchHistory = new ArrayList<>();
  private List<PlaylistEntity> playlists = new ArrayList<>();

  private String role = "ROLE_USER";

  public UserEntity(Integer persistentIdentity, String firstName, String lastName,
      String emailAddress, String passwordHash, Integer numCredits, String role) {
    super(persistentIdentity);
    this.firstName = firstName;
    this.lastName = lastName;
    this.emailAddress = emailAddress;
    this.passwordHash = passwordHash;
    this.numCredits = numCredits;
    this.role = role;

    try {
      createPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    } catch (EntityAlreadyExistsException eaee) {
      log.error("My favorites playlist somehow already exists for new user: {" + this.emailAddress
          + "].");
    }
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Integer getNumCredits() {
    return numCredits;
  }

  public void setNumCredits(Integer numCredits) {
    this.numCredits = numCredits;
  }

  public List<SongIdentifier> getSongPlayHistory() {
    return songPlayHistory;
  }

  public void setSongPlayHistory(List<SongIdentifier> songPlayHistory) {
    this.songPlayHistory = songPlayHistory;
  }

  public boolean addSongToSongPlayHistory(SongIdentifier songIdentifier) {
    this.songPlayHistory.remove(songIdentifier);
    return this.songPlayHistory.add(songIdentifier);
  }

  public List<String> getSearchHistory() {
    if (searchHistory == null)
      searchHistory = new ArrayList<>();
    return searchHistory;
  }

  public void setSearchHistory(List<String> searchHistory) {
    this.searchHistory = searchHistory;
  }

  public void addToSearchHistory(String query, int maxSize) {
    if (searchHistory == null)
      searchHistory = new ArrayList<>();
    searchHistory.remove(query);
    searchHistory.add(0, query);
    if (searchHistory.size() > maxSize) {
      searchHistory = new ArrayList<>(searchHistory.subList(0, maxSize));
    }
  }

  public void removeFromSearchHistory(int index) {
    if (searchHistory == null)
      return;
    if (index >= 0 && index < searchHistory.size()) {
      searchHistory.remove(index);
    }
  }

  public List<PlaylistEntity> getPlaylists() {

    if (playlists == null) {
      playlists = new ArrayList<>();
    }

    return playlists;
  }

  public PlaylistEntity getPlaylistByName(String playlistName) throws EntityDoesNotExistException {

    PlaylistEntity playlist = getPlaylistByNameNullIfNotExists(playlistName);
    if (playlist != null) {
      return playlist;
    }

    createMyFavoritesPlaylist();

    throw new EntityDoesNotExistException(
        "Cannot find playlist: [" + playlistName + "] for user: [" + this.emailAddress + "].");
  }

  public PlaylistEntity getPlaylistByNameNullIfNotExists(String playlistName) {

    for (PlaylistEntity playlist : this.playlists) {

      if (playlist.getName().equals(playlistName)) {
        return playlist;
      }
    }
    return null;
  }

  public PlaylistEntity createMyFavoritesPlaylist() {

    // Every user should have a "My favorites" playlist
    try {
      return createPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    } catch (EntityAlreadyExistsException eaee) {
      log.error("My favorites playlist somehow already exists for new user: {" + this.emailAddress
          + "].");
    }

    // This should never occur.
    return this.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
  }

  public PlaylistEntity createPlaylist(String playlistName) throws EntityAlreadyExistsException {

    PlaylistEntity check = getPlaylistByNameNullIfNotExists(playlistName);
    if (check != null) {
      throw new EntityAlreadyExistsException("Cannot create playlist: [" + playlistName
          + "] for user: [" + this.emailAddress + "] because it already exists.");
    }

    if (this.playlists == null) {
      this.playlists = new ArrayList<>();
    }

    int index = this.playlists.size();
    PlaylistEntity playlist = new PlaylistEntity(index, this.emailAddress, playlistName);
    this.playlists.add(playlist);
    return playlist;
  }

  public boolean deletePlaylist(String playlistName) throws EntityDoesNotExistException {

    if (PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME.equals(playlistName)) {
      throw new IllegalArgumentException("Cannot delete the My Favorites playlist.");
    }

    PlaylistEntity playlist = getPlaylistByName(playlistName);
    return this.playlists.remove(playlist);
  }

  public boolean addSongToPlaylist(String playlistName, SongFileEntity song)
      throws EntityDoesNotExistException {

    PlaylistEntity playlist = getPlaylistByName(playlistName);
    SongIdentifier songIdentifier =
        new SongIdentifier(song.getAlbum().getPersistentIdentity(), song.getPersistentIdentity());

    return playlist.addSong(songIdentifier);
  }

  public boolean removeSongFromPlaylist(String playlistName, SongFileEntity song)
      throws EntityDoesNotExistException {

    PlaylistEntity playlist = getPlaylistByName(playlistName);
    SongIdentifier songIdentifier =
        new SongIdentifier(song.getAlbum().getPersistentIdentity(), song.getPersistentIdentity());

    return playlist.removeSong(songIdentifier);
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  @Override
  public String getNaturalIdentity() {
    return this.emailAddress;
  }
}
