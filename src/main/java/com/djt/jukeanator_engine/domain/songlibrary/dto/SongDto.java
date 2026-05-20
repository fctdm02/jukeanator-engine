package com.djt.jukeanator_engine.domain.songlibrary.dto;

public class SongDto {
  
  private Integer songId;
  private String songName;
  private Integer songPlays;

  public SongDto(Integer songId, String songName, Integer songPlays) {
    this.songId = songId;
    this.songName = songName;
    this.songPlays = songPlays;
  }
  
  public Integer getSongId() {
    return songId;
  }

  public String getSongName() {
    return songName;
  }

  public Integer getSongPlays() {
    return songPlays;
  }
}
