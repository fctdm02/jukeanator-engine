package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Plain, human-readable JSON representation of a {@code UserLocationAccountEntity}, nested under
 * its owning {@link UserDto}.
 */
public record UserLocationAccountDto(Integer persistentIdentity, Integer locationId,
    int numCredits, List<SongIdentifier> songPlayHistory, List<PlaylistDto> playlists,
    List<UserSongCreditUsageEntryDto> songCreditUsages,
    List<UserAddFundsTransactionEntryDto> addFundsTransactions) implements Serializable {
}
