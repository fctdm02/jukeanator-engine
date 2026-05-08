package com.djt.jukeanator_engine.domain.songplayer.dto;

/**
 * @author tmyers
 */
public class NowPlayingSongDto {

  private String coverArtUrl;
  private String artist;
  private String album;
  private String song;

  public NowPlayingSongDto(String coverArtUrl, String artist, String album, String song) {
    super();
    this.coverArtUrl = coverArtUrl;
    this.artist = artist;
    this.album = album;
    this.song = song;
  }
  
  public String getCoverArtUrl() {
    return coverArtUrl;
  }
  public void setCoverArtUrl(String coverArtUrl) {
    this.coverArtUrl = coverArtUrl;
  }
  public String getArtist() {
    return artist;
  }
  public void setArtist(String artist) {
    this.artist = artist;
  }
  public String getAlbum() {
    return album;
  }
  public void setAlbum(String album) {
    this.album = album;
  }
  public String getSong() {
    return song;
  }
  public void setSong(String song) {
    this.song = song;
  }  
}
