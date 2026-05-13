package com.djt.jukeanator_engine.domain.songlibrary.event;

import java.time.Instant;
import java.util.Set;

public record ScanFileSystemForSongsEvent(
    String scanPath, 
    Set<String> acceptedSongFileExtensions,
    int albumCount, 
    Instant occurredAt) {
}
