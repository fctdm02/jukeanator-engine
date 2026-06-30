package com.djt.jukeanator_engine.domain.user.dto;

import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

public record UserProfileDto(Integer id, String firstName, String lastName, String emailAddress,
    Integer numCredits, java.math.BigDecimal balanceUsd, List<SongIdentifier> songPlayHistory) {
}
