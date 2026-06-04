package com.djt.jukeanator_engine.domain.songlibrary.event;

public sealed interface SongLibraryEvent permits ResetSongStatisticsEvent, ScanFileSystemForSongsEvent {
}