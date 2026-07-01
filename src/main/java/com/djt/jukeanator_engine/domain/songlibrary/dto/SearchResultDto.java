package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.ArrayList;
import java.util.List;

public class SearchResultDto {
  
  private List<SongDto> songs = new ArrayList<>();
  private List<ArtistDto> artists = new ArrayList<>();
  private List<AlbumDto> albums = new ArrayList<>();
  
  public SearchResultDto() {
  }
  
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
  
  public int getNumArtists() {
    return this.artists.size();
  }

  public int getNumAlbums() {
    return this.albums.size();
  }

  public int getNumSongs() {
    return this.songs.size();
  }  
}