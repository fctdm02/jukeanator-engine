package com.djt.jukeanator_engine.domain.songqueue.dto;

/**
 * {@code priority} positions the entry in the queue and can legitimately be {@code 1} for either
 * a normal play or a priority play (e.g. when the queue's current top entry is a priority-0
 * background song, {@code getHighestPriority()} returns {@code 1}) -- so it cannot by itself say
 * which button the caller pressed. {@code priorityPlay} carries that intent explicitly through to
 * credit charging; see {@code CreditCostCalculator}.
 */
public record AddSongToQueueRequest(String username, Integer albumId, Integer songId,
    Integer priority, boolean priorityPlay) {
}
