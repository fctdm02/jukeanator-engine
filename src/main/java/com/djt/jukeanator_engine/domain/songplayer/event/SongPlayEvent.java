package com.djt.jukeanator_engine.domain.songplayer.event;

import java.time.Instant;

public record SongPlayEvent(
    Integer albumId, 
    Integer songId, 
    Instant occurredAt) {
}
