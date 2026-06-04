package com.djt.jukeanator_engine.domain.songqueue.event;

import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

public record SongAddedToQueueEvent(SongQueueEntryDto queueEntry) implements SongQueueEvent {
}
