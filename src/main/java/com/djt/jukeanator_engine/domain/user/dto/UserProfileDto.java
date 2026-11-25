package com.djt.jukeanator_engine.domain.user.dto;

import java.util.List;

/**
 * A Web/Mobile UI account. Credits are per location ({@code balances}: one entry per location the
 * patron has used). Carries no dollar balance: the Web/Mobile UI shows credits instead -- see
 * docs/pricing.md.
 */
public record UserProfileDto(Integer id, String firstName, String lastName, String emailAddress,
    List<LocationBalanceDto> balances) {
}
