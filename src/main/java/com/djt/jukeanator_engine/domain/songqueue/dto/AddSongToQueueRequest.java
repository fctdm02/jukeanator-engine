package com.djt.jukeanator_engine.domain.songqueue.dto;

/**
 * {@code priority} positions the entry in the queue. A normal play is always priority 1; a priority
 * play is only allowed above a priority-1 song (see {@code SongQueueService.isPriorityPlayAllowed})
 * and so is always priority 2 or higher. {@code priorityPlay} names which button the caller
 * pressed and carries that intent through to eligibility checks and credit charging; see
 * {@code CreditCostCalculator}.
 */
public record AddSongToQueueRequest(String username, Integer albumId, Integer songId,
    Integer priority, boolean priorityPlay) {
}
