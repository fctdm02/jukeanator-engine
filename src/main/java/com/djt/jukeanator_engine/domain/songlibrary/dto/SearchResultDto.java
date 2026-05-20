package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;

public class SearchResultDto {
  
  private List<SongDto> songs;
  private List<ArtistDto> artists;
  private List<AlbumDto> albums;
  
  public SearchResultDto(List<SongDto> songs, List<ArtistDto> artists, List<AlbumDto> albums) {
    this.songs = songs;
    this.artists = artists;
    this.albums = albums;    
  }

  public List<SongDto> getSongs() {
    return songs;
  }

  public void setSongs(List<SongDto> songs) {
    this.songs = songs;
  }

  public List<ArtistDto> getArtists() {
    return artists;
  }

  public void setArtists(List<ArtistDto> artists) {
    this.artists = artists;
  }

  public List<AlbumDto> getAlbums() {
    return albums;
  }

  public void setAlbums(List<AlbumDto> albums) {
    this.albums = albums;
  }
  
}