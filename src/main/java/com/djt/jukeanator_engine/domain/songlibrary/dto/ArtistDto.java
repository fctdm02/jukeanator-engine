package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;

public class ArtistDto {
  
  private String artistName;
  private List<AlbumDto> albums;
  
  public ArtistDto(String artistName, List<AlbumDto> albums) {
    this.artistName = artistName;
    this.albums = albums;
  }

  public String getArtistName() {
    return artistName;
  }

  public void setArtistName(String artistName) {
    this.artistName = artistName;
  }

  public List<AlbumDto> getAlbums() {
    return albums;
  }

  public void setAlbums(List<AlbumDto> albums) {
    this.albums = albums;
  }  
}