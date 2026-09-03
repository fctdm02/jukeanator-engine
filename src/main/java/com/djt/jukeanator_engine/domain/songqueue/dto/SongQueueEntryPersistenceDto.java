package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Plain, human-readable JSON representation of a {@code SongQueueEntryEntity}, nested under the
 * singleton {@link SongQueueRootDto}. Stores {@code albumId}/{@code songId} rather than the full
 * {@code SongFileEntity}, since the song is resolved back against the live song library on load.
 * {@code songPath} is a read-convenience mirror of the resolved song's full path only, so a human
 * can see what's in the playlist JSON at a glance; it is not used to resolve the song on load.
 */
public record SongQueueEntryPersistenceDto(String username, Integer albumId, Integer songId,
    String songPath, Integer priority, Instant queuedAtTime) implements Serializable {
}
