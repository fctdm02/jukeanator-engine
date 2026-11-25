package com.djt.jukeanator_engine.domain.user.dto;

/**
 * A patron's song-credit balance at one location -- credits bought for a location are usable only
 * there. {@code locationName} is null where this instance does not know the location's name.
 */
public record LocationBalanceDto(Integer locationId, String locationName, int numCredits) {
}
