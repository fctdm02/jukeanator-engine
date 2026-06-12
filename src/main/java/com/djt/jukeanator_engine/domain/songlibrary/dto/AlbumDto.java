package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;
import java.util.Objects;

public class AlbumDto {

  private Integer genreId;
  private String genreName;
  private Integer artistId;
  private String artistName;  
  private Integer albumId;
  private String albumName;
  private Boolean hasExplicit;
  private String recordLabel;
  private String releaseDate;
  private String coverArtPath;
  private Boolean isCompilation;
  private List<SongDto> songs;

  public AlbumDto(
      Integer genreId,
      String genreName,      
      Integer artistId,
      String artistName,
      Integer albumId, 
      String albumName,       
      Boolean hasExplicit, 
      String recordLabel,
      String releaseDate, 
      String coverArtPath,
      Boolean isCompilation,
      List<SongDto> songs) {
    super();
    this.genreId = genreId;
    this.genreName = genreName;    
    this.artistId = artistId;
    this.albumId = albumId;
    this.albumName = albumName;    
    this.artistName = artistName;
    this.hasExplicit = hasExplicit;
    this.recordLabel = recordLabel;
    this.releaseDate = releaseDate;
    this.coverArtPath = coverArtPath;
    this.isCompilation = isCompilation;
    this.songs = songs;
  }

  public Integer getGenreId() {
    return genreId;
  }
  
  public String getGenreName() {
    return genreName;
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

  public Boolean getHasExplicit() {
    return hasExplicit;
  }

  public String getRecordLabel() {
    return recordLabel;
  }

  public String getReleaseDate() {
    return releaseDate;
  }

  public String getCoverArtPath() {
    return coverArtPath;
  }
  
  public Boolean isCompilation() {
    return isCompilation;
  }

  public List<SongDto> getSongs() {
    return songs;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(albumId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AlbumDto other = (AlbumDto) obj;
    return Objects.equals(albumId, other.albumId);
  }
  
  @Override
  public String toString() {
    return "AlbumDto [artistName=" + artistName + ", albumName=" + albumName + "]";
  }  
  
  public Integer getNumPlays() {
    
    int numPlays = 0;
    for (SongDto song: songs) {

      numPlays = numPlays + song.getNumPlays();
    }
    return Integer.valueOf(numPlays);
  }    
}