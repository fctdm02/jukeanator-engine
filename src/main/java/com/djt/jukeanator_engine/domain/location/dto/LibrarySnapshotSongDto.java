package com.djt.jukeanator_engine.domain.location.dto;

/**
 * One song within {@link LibrarySnapshotAlbumDto}, keyed by the slave's own local song id.
 *
 * <p>
 * {@code artistName} is this song's own embedded artist credit (e.g. {@code SongDto.artistName()}
 * / {@code SongFileEntity.getArtistName()}), not the album's -- a compilation album can hold songs
 * credited to different artists, and master's synced tree needs each song's real credit to derive
 * {@code RootFolderEntity.artistsFromSongs} correctly (see
 * {@code LocationServiceImpl#persistSnapshotToJpa}); collapsing every song onto the album-level
 * artist silently breaks browse/search for any artist that only appears via a song credit.
 */
public record LibrarySnapshotSongDto(Integer sourceSongId, String title, String artistName,
    Integer trackNumber, Integer numPlays) {
}
