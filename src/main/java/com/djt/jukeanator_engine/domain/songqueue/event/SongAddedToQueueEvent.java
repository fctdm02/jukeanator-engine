package com.djt.jukeanator_engine.domain.songqueue.event;

import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

/**
 * {@code priorityPlay} carries the caller's original "which button did they press" intent through
 * to credit charging -- see {@code AddSongToQueueRequest} for why {@code queueEntry.priority()}
 * alone can't be trusted to reconstruct it.
 */
public record SongAddedToQueueEvent(SongQueueEntryDto queueEntry, boolean priorityPlay)
    implements SongQueueEvent {
}
