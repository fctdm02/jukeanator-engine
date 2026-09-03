package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Plain, human-readable JSON representation of the singleton {@code SongQueueRootEntity}. This is
 * the top-level shape written to and read from {@code SongQueueRootEntity.SONG_QUEUE_FILENAME}.
 */
public record SongQueueRootDto(String rootPath, List<SongQueueEntryPersistenceDto> entries)
    implements Serializable {
}
