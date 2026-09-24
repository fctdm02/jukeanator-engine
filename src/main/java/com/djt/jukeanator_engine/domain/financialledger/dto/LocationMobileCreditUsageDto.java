package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.io.Serializable;
import java.time.Instant;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;

/**
 * Filesystem-repository JSON shape of a {@code LocationMobileCreditUsageEntity}.
 */
public record LocationMobileCreditUsageDto(Integer persistentIdentity, Integer locationId,
    String sourceSyncId, String userEmail, int amount, UserSongCreditUsageType type,
    Instant timestamp, Integer songAlbumId, Integer songId) implements Serializable {
}
