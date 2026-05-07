package com.djt.jukeanator_engine.domain.songlibrary.dto;

public class SongDto {
  private Integer songId;
  private String name;
  private Integer songPlays;

  public SongDto(Integer songId, String name, Integer songPlays) {
    this.songId = songId;
    this.name = name;
    this.songPlays = songPlays;
  }
  
  public Integer getSongId() {
    return songId;
  }

  public String getName() {
    return name;
  }

  public Integer getSongPlays() {
    return songPlays;
  }
}
