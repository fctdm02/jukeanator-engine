package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;

public class AlbumDto {

  private Integer artistId;
  private String artistName;  
  private Integer albumId;
  private String albumName;
  private Boolean hasExplicit;
  private String recordLabel;
  private String releaseDate;
  private String coverArtPath;
  private List<SongDto> songs;

  public AlbumDto(
      Integer artistId,
      String artistName,
      Integer albumId, 
      String albumName,       
      Boolean hasExplicit, 
      String recordLabel,
      String releaseDate, 
      String coverArtPath, 
      List<SongDto> songs) {
    super();
    this.artistId = artistId;
    this.albumId = albumId;
    this.albumName = albumName;    
    this.artistName = artistName;
    this.hasExplicit = hasExplicit;
    this.recordLabel = recordLabel;
    this.releaseDate = releaseDate;
    this.coverArtPath = coverArtPath;
    this.songs = songs;
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

  public List<SongDto> getSongs() {
    return songs;
  }  
}