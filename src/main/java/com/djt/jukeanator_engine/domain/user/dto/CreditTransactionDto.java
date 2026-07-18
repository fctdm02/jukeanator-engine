package com.djt.jukeanator_engine.domain.user.dto;

import java.time.Instant;
import com.djt.jukeanator_engine.domain.user.model.CreditTransactionType;

public record CreditTransactionDto(String userEmail, String locationId, int amount,
    CreditTransactionType type, Instant timestamp, Integer songAlbumId, Integer songId,
    int resultingBalance) {
}
