package com.djt.jukeanator_engine.domain.user.dto;

import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * A Web/Mobile UI account. Carries no dollar balance: the Web/Mobile UI shows credits
 * instead -- see docs/pricing.md.
 */
public record UserProfileDto(Integer id, String firstName, String lastName, String emailAddress,
    Integer numCredits, List<SongIdentifier> songPlayHistory) {
}
