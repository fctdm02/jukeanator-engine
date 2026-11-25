package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Plain, human-readable JSON representation of a {@code UserEntity}, nested under the singleton
 * {@link UserRootDto}. Credits, play history, playlists and the credit ledgers are per location,
 * under {@link #locationAccounts()}.
 */
public record UserDto(Integer persistentIdentity, String firstName, String lastName,
    String emailAddress, String passwordHash, List<String> searchHistory,
    List<UserLocationAccountDto> locationAccounts, String role, String status,
    String verificationTokenHash, Instant verificationTokenExpiresAt) implements Serializable {

  public List<String> searchHistory() {
    return searchHistory == null ? List.of() : searchHistory;
  }

  public List<UserLocationAccountDto> locationAccounts() {
    return locationAccounts == null ? List.of() : locationAccounts;
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
  public int hashCode() {
    return Objects.hash(emailAddress);
  }

  @Override
  public String toString() {
    return "UserDto [persistentIdentity=" + persistentIdentity + ", firstName=" + firstName
        + ", lastName=" + lastName + ", emailAddress=" + emailAddress + ", role=" + role
        + ", status=" + status + "]";
  }
}
