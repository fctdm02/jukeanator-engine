package com.djt.jukeanator_engine.domain.user.dto;

import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * Payload returned by GET /api/users/home.
 *
 * <p>Drives the home-page content area (sections a–d) and pre-populates the search page history
 * list so a single round-trip covers both panels.
 *
 * <p>All list fields default to empty lists until the underlying feature is implemented.
 *
 * @param myRecentPlays  (a) Songs the authenticated user has recently played.
 * @param myPlaylists    (b) Placeholder — no playlist domain yet; will hold playlist names.
 * @param artistsHotHere (c) Artists currently trending at this venue.
 * @param songsHotHere   (d) Songs currently trending at this venue.
 * @param searchHistory  Recent search queries for the authenticated user.
 */
public record UserHomePageDto(
    List<SongDto>    myRecentPlays,
    List<String>     myPlaylists,
    List<ArtistDto>  artistsHotHere,
    List<SongDto>    songsHotHere,
    List<String>     searchHistory) {
}
