package com.djt.jukeanator_engine.domain.songqueue.event;

import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

public record SongQueueChangedEvent(List<SongQueueEntryDto> queuedSongs) implements SongQueueEvent {
}
