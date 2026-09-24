package com.djt.jukeanator_engine.domain.user.dto;

import java.time.Instant;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;

/**
 * API-facing shape of one song-credit spend: the bar-owner credit-ledger endpoint's row, and the
 * master-to-slave mobile mirror's wire shape (see {@code FinancialLedgerService}). {@code syncId}
 * is the spend's master-minted cross-instance id -- {@code null} for spends recorded before it
 * existed.
 */
public record UserSongCreditUsageDto(String userEmail, Integer locationId, int amount,
    UserSongCreditUsageType type, Instant timestamp, Integer songAlbumId, Integer songId,
    int resultingBalance, String syncId) {
}
