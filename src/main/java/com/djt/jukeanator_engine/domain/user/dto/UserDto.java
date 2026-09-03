package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Plain, human-readable JSON representation of a {@code UserEntity}, nested under the singleton
 * {@link UserRootDto}.
 */
public record UserDto(Integer persistentIdentity, String firstName, String lastName,
    String emailAddress, String passwordHash, Integer numCredits,
    List<SongIdentifier> songPlayHistory, List<String> searchHistory, List<PlaylistDto> playlists,
    List<CreditTransactionEntryDto> transactions, String role) implements Serializable {

  public List<String> searchHistory() {
    return searchHistory == null ? List.of() : searchHistory;
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
        + ", lastName=" + lastName + ", emailAddress=" + emailAddress + ", numCredits="
        + numCredits + ", role=" + role + "]";
  }
}
