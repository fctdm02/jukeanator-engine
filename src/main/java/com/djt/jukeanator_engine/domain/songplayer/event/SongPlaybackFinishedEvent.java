package com.djt.jukeanator_engine.domain.songplayer.event;

import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

public record SongPlaybackFinishedEvent(SongQueueEntryDto song) implements SongPlayerEvent {
}
