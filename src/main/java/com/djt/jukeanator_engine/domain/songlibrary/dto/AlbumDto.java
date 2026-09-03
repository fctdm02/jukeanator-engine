package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;
import java.util.Objects;

public record AlbumDto(Integer genreId, String genreName, Integer artistId, String artistName,
    Integer albumId, String albumName, Boolean hasExplicit, String recordLabel,
    String releaseDate, String coverArtPath, Boolean isCompilation, Integer songNumPlays,
    List<SongDto> songs) {

  public int numSongs() {
    return songs.size();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    AlbumDto other = (AlbumDto) obj;
    return Objects.equals(albumId, other.albumId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(albumId);
  }

  @Override
  public String toString() {
    return "AlbumDto [artistName=" + artistName + ", albumName=" + albumName + "]";
  }
}
