package com.djt.jukeanator_engine.domain.songplayer.dto;

/**
 * @author tmyers
 */
public class NowPlayingSongDto {

  private String coverArtPath;
  private String artistName;
  private String albumName;
  private String songName;

  public NowPlayingSongDto(String coverArtPath, String artistName, String albumName, String songName) {
    super();
    this.coverArtPath = coverArtPath;
    this.artistName = artistName;
    this.albumName = albumName;
    this.songName = songName;
  }
  
  public String getCoverArtPath() {
    return coverArtPath;
  }
  public String getArtistName() {
    return artistName;
  }
  public String getAlbumName() {
    return albumName;
  }
  public String getSongName() {
    return songName;
  }
}
