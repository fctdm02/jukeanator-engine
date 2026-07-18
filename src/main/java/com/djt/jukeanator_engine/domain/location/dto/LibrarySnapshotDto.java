package com.djt.jukeanator_engine.domain.location.dto;

import java.util.List;

/**
 * A flattened, versioned snapshot of a slave's song library metadata, built by the slave from its
 * own {@code RootFolderEntity} and uploaded to the master. Deliberately NOT the slave's raw
 * {@code RootFolderEntity} object graph (Java-serialized) — that would couple master and slave to
 * binary-compatible versions of the same domain classes. Contains metadata + cover-art hashes
 * only; audio files never leave the slave.
 */
public record LibrarySnapshotDto(List<LibrarySnapshotGenreDto> genres,
    List<LibrarySnapshotArtistDto> artists, List<LibrarySnapshotAlbumDto> albums) {
}
