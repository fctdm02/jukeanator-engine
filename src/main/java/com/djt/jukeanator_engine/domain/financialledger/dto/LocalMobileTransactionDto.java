package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Plain JSON shape of a {@code LocalMobileTransactionEntity} (a Web/Mobile Add Funds purchase for
 * one location). Used both on disk, by the filesystem ledger repository, and on the wire, when
 * master mirrors a purchase to the location's slave -- where master's {@code persistentIdentity}
 * becomes the slave copy's {@code sourceTransactionId}.
 */
public record LocalMobileTransactionDto(Integer persistentIdentity, BigDecimal amount,
    Instant timestamp, Integer locationId, Integer sourceTransactionId, String userEmail,
    String paymentReference, String packageCode, Integer creditsAwarded, Integer bonusCredits)
    implements Serializable {
}
