package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * One song that was dequeued for playback, and when it started playing.
 *
 * @param entry the queue entry as it was when dequeued (song, priority, requesting user)
 * @param startedAt when the song was dequeued to start playing
 */
public record SongPlayHistoryEntryDto(SongQueueEntryDto entry, Instant startedAt)
    implements Serializable {
}
