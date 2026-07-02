package com.djt.jukeanator_engine.domain.user.dto;

import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * Payload returned by GET /api/users/home (authenticated users only).
 *
 * <p>Extends {@link HomePageDto} with user-specific sections and pre-populates the search page
 * history list so a single round-trip covers both panels.
 *
 * @see HomePageDto
 */
public class UserHomePageDto extends HomePageDto {

  private final List<SongDto> myRecentPlays;
  private final List<String> myPlaylists;
  private final List<String> searchHistory;

  public UserHomePageDto(
      List<SongDto> myRecentPlays, 
      List<String> myPlaylists,
      List<ArtistDto> artistsHotHere, 
      List<SongDto> songsHotHere, 
      List<String> searchHistory) {
    
    super(artistsHotHere, songsHotHere);
    this.myRecentPlays = myRecentPlays;
    this.myPlaylists   = myPlaylists;
    this.searchHistory = searchHistory;
  }

  public List<SongDto> getMyRecentPlays() { return myRecentPlays; }
  public List<String>  getMyPlaylists()   { return myPlaylists; }
  public List<String>  getSearchHistory() { return searchHistory; }
}
