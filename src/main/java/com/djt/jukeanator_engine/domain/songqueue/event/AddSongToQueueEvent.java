package com.djt.jukeanator_engine.domain.songqueue.event;

import java.time.Instant;
import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

public record AddSongToQueueEvent(
    List<SongQueueEntryDto> queuedSongs,    
    Integer albumId, 
    Integer songId, 
    Integer priority,
    Integer songQueueIndex, 
    Instant occurredAt) {
}
