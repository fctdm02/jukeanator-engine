package com.djt.jukeanator_engine.domain.songlibrary.dto;

/**
 * True, unpaginated totals for the current search -- how many artists/albums/songs actually
 * match {@code searchFor}, regardless of how much has been fetched/paged through so far. Used by
 * the Search screen's column headers to show the user how much there is to scroll through.
 */
public record SearchTotalsDto(int numArtists, int numAlbums, int numSongs) {
}
