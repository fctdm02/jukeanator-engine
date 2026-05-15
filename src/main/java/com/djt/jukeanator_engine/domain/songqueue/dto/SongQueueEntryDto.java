package com.djt.jukeanator_engine.domain.songqueue.dto;

public class SongQueueEntryDto {
  
  private String coverArtPath;
  private String albumName;
  private String artistName;
  private String songName;
  private Integer songPlays;
  private Integer priority;
  private String songPath;

  public SongQueueEntryDto(
      String coverArtPath,
      String albumName,
      String artistName,
      String songName,
      Integer songPlays,
      Integer priority,
      String songPath) {
    
    this.coverArtPath = coverArtPath;
    this.albumName = albumName;
    this.artistName = artistName;
    this.songName = songName;
    this.songPlays = songPlays;
    this.priority = priority;
    this.songPath = songPath;
  }

  public String getCoverArtPath() {
    return coverArtPath;
  }

  public String getAlbumName() {
    return albumName;
  }

  public String getArtistName() {
    return artistName;
  }

  public String getSongName() {
    return songName;
  }

  public Integer getSongPlays() {
    return songPlays;
  }

  public Integer getPriority() {
    return priority;
  }
  
  public String getSongPath() {
    return songPath;
  }  
}
