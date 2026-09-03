package com.djt.jukeanator_engine.domain.user.dto;

import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * Payload returned by GET /api/users/home (authenticated users only).
 *
 * <p>Carries the same trending sections as {@link HomePageDto} plus user-specific sections, and
 * pre-populates the search page history list so a single round-trip covers both panels.
 *
 * @see HomePageDto
 */
public record UserHomePageDto(List<SongDto> myRecentPlays, List<String> myPlaylists,
    List<ArtistDto> artistsHotHere, List<SongDto> songsHotHere, List<String> searchHistory) {
}
