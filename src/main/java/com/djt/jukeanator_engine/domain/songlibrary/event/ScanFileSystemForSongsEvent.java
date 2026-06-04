package com.djt.jukeanator_engine.domain.songlibrary.event;

public record ScanFileSystemForSongsEvent(String scanPath,Integer albumCount) implements SongLibraryEvent {
}
