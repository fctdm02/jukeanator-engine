package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Plain, human-readable JSON representation of a {@code UserEntity}, nested under the singleton
 * {@link UserRootDto}.
 */
public final class UserDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private Integer persistentIdentity;
  private String firstName;
  private String lastName;
  private String emailAddress;
  private String passwordHash;
  private Integer numCredits = 0;
  private List<SongIdentifier> songPlayHistory = new ArrayList<>();
  private List<String> searchHistory = new ArrayList<>();
  private List<PlaylistDto> playlists = new ArrayList<>();
  private List<CreditTransactionEntryDto> transactions = new ArrayList<>();
  private String role = "ROLE_USER";

  public UserDto() {}

  public UserDto(Integer persistentIdentity, String firstName, String lastName,
      String emailAddress, String passwordHash, Integer numCredits,
      List<SongIdentifier> songPlayHistory, List<String> searchHistory,
      List<PlaylistDto> playlists, List<CreditTransactionEntryDto> transactions, String role) {

    this.persistentIdentity = persistentIdentity;
    this.firstName = firstName;
    this.lastName = lastName;
    this.emailAddress = emailAddress;
    this.passwordHash = passwordHash;
    this.numCredits = numCredits;
    this.songPlayHistory = songPlayHistory;
    this.searchHistory = searchHistory;
    this.playlists = playlists;
    this.transactions = transactions;
    this.role = role;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }

  public void setPersistentIdentity(Integer persistentIdentity) {
    this.persistentIdentity = persistentIdentity;
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

  public List<PlaylistDto> getPlaylists() {
    return playlists;
  }

  public void setPlaylists(List<PlaylistDto> playlists) {
    this.playlists = playlists;
  }

  public List<CreditTransactionEntryDto> getTransactions() {
    return transactions;
  }

  public void setTransactions(List<CreditTransactionEntryDto> transactions) {
    this.transactions = transactions;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  @Override
  public int hashCode() {
    return Objects.hash(emailAddress);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    UserDto other = (UserDto) obj;
    return Objects.equals(emailAddress, other.emailAddress);
  }

  @Override
  public String toString() {
    return "UserDto [persistentIdentity=" + persistentIdentity + ", firstName=" + firstName
        + ", lastName=" + lastName + ", emailAddress=" + emailAddress + ", numCredits="
        + numCredits + ", role=" + role + "]";
  }
}
