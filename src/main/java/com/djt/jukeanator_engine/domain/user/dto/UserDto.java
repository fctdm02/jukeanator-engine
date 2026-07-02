package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

public final class UserDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private String firstName;
  private String lastName;
  private String emailAddress;
  private String passwordHash;
  private Integer numCredits = 0;
  private List<SongIdentifier> songPlayHistory;
  private List<String> searchHistory;
  private List<PlaylistDto> playlists;

  private String role = "ROLE_USER";

  public UserDto(String firstName, String lastName, String emailAddress, String passwordHash,
      Integer numCredits, List<SongIdentifier> songPlayHistory, List<String> searchHistory,
      List<PlaylistDto> playlists, String role) {

    super();
    this.firstName = firstName;
    this.lastName = lastName;
    this.emailAddress = emailAddress;
    this.passwordHash = passwordHash;
    this.numCredits = numCredits;
    this.songPlayHistory = songPlayHistory;
    this.searchHistory = searchHistory;
    this.playlists = playlists;
    this.role = role;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Integer getNumCredits() {
    return numCredits;
  }

  public List<SongIdentifier> getSongPlayHistory() {
    return songPlayHistory;
  }

  public boolean addSongToSongPlayHistory(SongIdentifier songIdentifier) {
    return this.songPlayHistory.add(songIdentifier);
  }

  public List<String> getSearchHistory() {
    if (searchHistory == null)
      searchHistory = new ArrayList<>();
    return searchHistory;
  }

  public List<PlaylistDto> getPlaylists() {
    return playlists;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }
}
