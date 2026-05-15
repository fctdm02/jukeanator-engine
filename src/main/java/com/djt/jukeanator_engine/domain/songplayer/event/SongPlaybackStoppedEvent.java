package com.djt.jukeanator_engine.domain.songplayer.event;

import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

public record SongPlaybackStoppedEvent(SongQueueEntryDto song) implements SongPlayerEvent {
}
