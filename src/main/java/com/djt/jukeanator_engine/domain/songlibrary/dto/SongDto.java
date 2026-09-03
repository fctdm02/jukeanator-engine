package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.io.Serializable;
import java.util.Objects;

public record SongDto(Integer genreId, String genreName, Integer artistId, String artistName,
    Integer albumId, String albumName, String coverArtPath, Integer songId, String songName,
    Integer trackNumber, Integer numPlays) implements Serializable {

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    SongDto other = (SongDto) obj;
    return Objects.equals(albumId, other.albumId) && Objects.equals(songId, other.songId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(albumId, songId);
  }

  @Override
  public String toString() {
    return "SongDto [artistId=" + artistId + ", artistName=" + artistName + ", albumId=" + albumId
        + ", albumName=" + albumName + ", songId=" + songId + ", songName=" + songName
        + ", trackNumber=" + trackNumber + "]";
  }
}
