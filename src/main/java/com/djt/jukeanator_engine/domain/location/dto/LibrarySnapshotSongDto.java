package com.djt.jukeanator_engine.domain.location.dto;

/** One song within {@link LibrarySnapshotAlbumDto}, keyed by the slave's own local song id. */
public record LibrarySnapshotSongDto(Integer sourceSongId, String title, Integer trackNumber,
    Integer numPlays) {
}
