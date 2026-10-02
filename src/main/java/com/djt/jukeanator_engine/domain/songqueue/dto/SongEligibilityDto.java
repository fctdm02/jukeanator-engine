package com.djt.jukeanator_engine.domain.songqueue.dto;

/**
 * The eligibility of one song for the queue; {@code ineligibleReason} is {@code null} when the
 * song can be queued.
 */
public record SongEligibilityDto(Integer locationId, Integer albumId, Integer songId,
    String ineligibleReason) {
}
