package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;

/**
 * Plain, human-readable JSON representation of a {@code UserSongCreditUsageEntity}, nested under
 * its owning {@link UserDto} (unlike {@link UserSongCreditUsageDto}, which is the API-facing shape
 * for the credit ledger endpoint and carries the owning user's email instead).
 */
public record UserSongCreditUsageEntryDto(Integer persistentIdentity, Integer locationId,
    int amount, UserSongCreditUsageType type, Instant timestamp, Integer songAlbumId,
    Integer songId, int resultingBalance, String syncId) implements Serializable {

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    UserSongCreditUsageEntryDto other = (UserSongCreditUsageEntryDto) obj;
    return Objects.equals(persistentIdentity, other.persistentIdentity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(persistentIdentity);
  }
}
