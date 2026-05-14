package com.djt.jukeanator_engine.domain.songplayer.event;

import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;

public record SongPlaybackStoppedEvent(SongQueueEntryEntity song) implements SongPlayerEvent {
}
