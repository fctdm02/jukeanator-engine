package com.djt.jukeanator_engine.domain.location.dto;

import java.util.List;

/**
 * One album synced from a slave, addressed by its slave-local (not globally unique) album id —
 * every slave's own scan starts at albumId 0, so master always keys this
 * {@code (locationId, sourceAlbumId)}, never by {@code sourceAlbumId} alone.
 *
 * <p>
 * Carries {@code artistName}/{@code genreName} directly rather than requiring a lookup by
 * {@code sourceArtistId}/{@code sourceGenreId} against the top-level artist/genre lists — the
 * slave's own {@code AlbumDto.getArtistId()} does not necessarily correspond to the same id space
 * as {@code ArtistDto.getArtistId()} (a pre-existing characteristic of the original scan-order id
 * scheme), so cross-referencing by id here would silently produce {@code null} names.
 */
public record LibrarySnapshotAlbumDto(Integer sourceAlbumId, String name, Integer sourceArtistId,
    String artistName, Integer sourceGenreId, String genreName, String coverArtHash,
    Boolean hasExplicit, String recordLabel, String releaseDate, Boolean isCompilation,
    List<LibrarySnapshotSongDto> songs) {
}
