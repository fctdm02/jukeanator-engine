package com.djt.jukeanator_engine.domain.songlibrary.event;

/**
 * Published after an album's metadata (record label, release date, explicit flag, etc.) is edited,
 * e.g. from the Admin Panel's Edit Album screen, so views holding album data can refresh.
 */
public record AlbumMetadataChangedEvent(Integer locationId, Integer albumId)
    implements SongLibraryEvent {
}
