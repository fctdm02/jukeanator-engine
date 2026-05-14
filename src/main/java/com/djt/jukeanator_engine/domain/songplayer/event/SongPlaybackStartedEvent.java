package com.djt.jukeanator_engine.domain.songplayer.event;

import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;

public record SongPlaybackStartedEvent(SongQueueEntryEntity song) implements SongPlayerEvent {
}
