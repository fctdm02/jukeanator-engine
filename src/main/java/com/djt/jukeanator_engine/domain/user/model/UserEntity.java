package com.djt.jukeanator_engine.domain.user.model;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

public class UserEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

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
    return playlists;
  }

  public void setPlaylists(List<PlaylistEntity> playlists) {
    this.playlists = playlists;
  }

  public boolean addPlaylist(PlaylistEntity playlist) {
    return this.playlists.add(playlist);
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
