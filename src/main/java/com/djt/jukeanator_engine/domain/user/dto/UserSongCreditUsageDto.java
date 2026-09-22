package com.djt.jukeanator_engine.domain.user.dto;

import java.time.Instant;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;

public record UserSongCreditUsageDto(String userEmail, Integer locationId, int amount,
    UserSongCreditUsageType type, Instant timestamp, Integer songAlbumId, Integer songId,
    int resultingBalance) {
}
