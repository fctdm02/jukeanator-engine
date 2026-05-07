package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;

public class AlbumDto {
  
  private Integer albumId;
  private String title;
  private String genre;
  private String artist;
  private Boolean hasExplicit;
  private String recordLabel;
  private String releaseDate;
  private String coverArtPath;
  private List<SongDto> songs;

  public AlbumDto(
      Integer albumId, 
      String title,
      String genre,
      String artist, 
      Boolean hasExplicit, 
      String recordLabel,
      String releaseDate, 
      String coverArtPath, 
      List<SongDto> songs) {
    super();
    this.albumId = albumId;
    this.title = title;
    this.genre = genre;
    this.artist = artist;
    this.hasExplicit = hasExplicit;
    this.recordLabel = recordLabel;
    this.releaseDate = releaseDate;
    this.coverArtPath = coverArtPath;
    this.songs = songs;
  }

  public Integer getAlbumId() {
    return albumId;
  }

  public String getTitle() {
    return title;
  }

  public String getGenre() {
    return genre;
  }
  
  public String getArtist() {
    return artist;
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