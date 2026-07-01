package com.djt.jukeanator_engine.domain.user.dto;

import java.util.List;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * Payload returned by GET /api/users/home-public (no authentication required).
 *
 * @param artistsHotHere Artists currently trending at this venue.
 * @param songsHotHere   Songs currently trending at this venue.
 */
public class HomePageDto {

  private final List<ArtistDto> artistsHotHere;
  private final List<SongDto>   songsHotHere;

  public HomePageDto(List<ArtistDto> artistsHotHere, List<SongDto> songsHotHere) {
    this.artistsHotHere = artistsHotHere;
    this.songsHotHere   = songsHotHere;
  }

  public List<ArtistDto> getArtistsHotHere() { return artistsHotHere; }
  public List<SongDto>   getSongsHotHere()   { return songsHotHere; }
}
