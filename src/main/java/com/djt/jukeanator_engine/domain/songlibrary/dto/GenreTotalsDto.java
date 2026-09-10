package com.djt.jukeanator_engine.domain.songlibrary.dto;

/**
 * True, unpaginated totals for one genre -- how many artists/albums/songs it actually contains,
 * regardless of how much of that has been fetched/paged through so far. Used by the Genres screen
 * header to show the user how much there is to scroll through.
 */
public record GenreTotalsDto(int numArtists, int numAlbums, int numSongs) {
}
