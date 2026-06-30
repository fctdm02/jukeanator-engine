package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

public final class UserDto implements Serializable {
  
  private static final long serialVersionUID = 1L;

  public UserDto(String firstName, String lastName, String emailAddress, String passwordHash,
      Integer numCredits, List<SongIdentifier> songPlayHistory, String role) {
    super();
    this.firstName = firstName;
    this.lastName = lastName;
    this.emailAddress = emailAddress;
    this.passwordHash = passwordHash;
    this.numCredits = numCredits;
    this.songPlayHistory = songPlayHistory;
    this.role = role;
  }

  private String firstName;
  private String lastName;
  private String emailAddress;
  private String passwordHash;
  private Integer numCredits = 0;
  private List<SongIdentifier> songPlayHistory = new ArrayList<>();
  private List<String> searchHistory = new ArrayList<>();
  private String role = "ROLE_USER";

  public String getFirstName() {
    return firstName;
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
    return this.songPlayHistory.add(songIdentifier);
  }

  public List<String> getSearchHistory() {
    if (searchHistory == null) searchHistory = new ArrayList<>();
    return searchHistory;
  }

  public void setSearchHistory(List<String> searchHistory) {
    this.searchHistory = searchHistory;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }
}
