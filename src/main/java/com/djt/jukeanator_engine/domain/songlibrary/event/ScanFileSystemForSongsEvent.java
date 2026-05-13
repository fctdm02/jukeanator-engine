package com.djt.jukeanator_engine.domain.songlibrary.event;

import java.time.Instant;

public record ScanFileSystemForSongsEvent(
    String scanPath, 
    int albumCount, 
    Instant occurredAt) {
}
