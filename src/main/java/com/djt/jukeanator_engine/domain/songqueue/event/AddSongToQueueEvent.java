package com.djt.jukeanator_engine.domain.songqueue.event;

import java.time.Instant;

public record AddSongToQueueEvent(
    Integer albumId, 
    Integer songId, 
    Integer priority,
    Integer songQueueIndex, 
    Instant occurredAt) {
}
