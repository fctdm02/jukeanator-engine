package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;
import java.util.Objects;

public record ArtistDto(Integer artistId, String artistName, String coverArtPath,
    Integer albumCount, Integer songCount, Integer numPlays, List<AlbumDto> albums) {

  public int numAlbums() {
    return albums.size();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    ArtistDto other = (ArtistDto) obj;
    return Objects.equals(artistId, other.artistId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(artistId);
  }

  @Override
  public String toString() {
    return "ArtistDto [" + artistName + "]";
  }
}
