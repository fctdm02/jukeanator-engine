package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Plain, human-readable JSON representation of a {@code UserAddFundsTransactionEntity}, nested
 * under its owning {@link UserDto} -- the filesystem-repository counterpart to
 * {@link UserSongCreditUsageEntryDto} for the Add-Funds side of the ledger.
 */
public record UserAddFundsTransactionEntryDto(Integer persistentIdentity, String packageId,
    int creditsAwarded, int bonusCredits, BigDecimal amountUsd, String paymentSource,
    String paymentTransactionId, Instant timestamp, int resultingBalance)
    implements Serializable {

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    UserAddFundsTransactionEntryDto other = (UserAddFundsTransactionEntryDto) obj;
    return Objects.equals(persistentIdentity, other.persistentIdentity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(persistentIdentity);
  }
}
