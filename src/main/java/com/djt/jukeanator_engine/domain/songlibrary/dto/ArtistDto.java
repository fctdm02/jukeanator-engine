package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;
import java.util.Objects;

public class ArtistDto {

  private final Integer artistId;
  private final String artistName;
  private final String coverArtPath;
  private final Integer albumCount;
  private final Integer songCount;
  private final Integer numPlays;
  private final List<AlbumDto> albums;

  public ArtistDto(Integer artistId, String artistName, String coverArtPath, Integer albumCount,
      Integer songCount, Integer numPlays, List<AlbumDto> albums) {
    super();
    this.artistId = artistId;
    this.artistName = artistName;
    this.coverArtPath = coverArtPath;
    this.albumCount = albumCount;
    this.songCount = songCount;
    this.numPlays = numPlays;
    this.albums = albums;
  }

  public Integer getArtistId() {
    return artistId;
  }

  public String getArtistName() {
    return artistName;
  }

  public String getCoverArtPath() {
    return coverArtPath;
  }

  public Integer getAlbumCount() {
    return albumCount;
  }

  public Integer getSongCount() {
    return songCount;
  }

  public Integer getNumPlays() {
    return numPlays;
  }

  public List<AlbumDto> getAlbums() {
    return albums;
  }

  public int getNumAlbums() {
    return this.albums.size();
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(artistId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ArtistDto other = (ArtistDto) obj;
    return Objects.equals(artistId, other.artistId);
  }

  @Override
  public String toString() {
    return "ArtistDto [" + this.artistName + "]";
  }
}