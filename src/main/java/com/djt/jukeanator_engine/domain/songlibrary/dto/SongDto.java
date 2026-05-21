package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.Objects;

public class SongDto {

  private final Integer artistId;
  private final String artistName;
  private final Integer albumId;
  private final String albumName;
  private final String coverArtPath;
  private final Integer songId;
  private final String songName;
  private final Integer numPlays;

  public SongDto(
      Integer artistId, 
      String artistName, 
      Integer albumId, 
      String albumName,
      String coverArtPath, 
      Integer songId, 
      String songName, 
      Integer numPlays) {
    super();
    this.artistId = artistId;
    this.artistName = artistName;
    this.albumId = albumId;
    this.albumName = albumName;
    this.coverArtPath = coverArtPath;
    this.songId = songId;
    this.songName = songName;
    this.numPlays = numPlays;
  }

  public Integer getArtistId() {
    return artistId;
  }

  public String getArtistName() {
    return artistName;
  }

  public Integer getAlbumId() {
    return albumId;
  }

  public String getAlbumName() {
    return albumName;
  }

  public String getCoverArtPath() {
    return coverArtPath;
  }

  public Integer getSongId() {
    return songId;
  }

  public String getSongName() {
    return songName;
  }

  public Integer getNumPlays() {
    return numPlays;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(albumId, songId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SongDto other = (SongDto) obj;
    return Objects.equals(albumId, other.albumId) && Objects.equals(songId, other.songId);
  }
  
  @Override
  public String toString() {
    return "SongDto [artistId=" + artistId + ", artistName=" + artistName + ", albumId=" + albumId
        + ", albumName=" + albumName + ", songId=" + songId + ", songName=" + songName + "]";
  }
}
