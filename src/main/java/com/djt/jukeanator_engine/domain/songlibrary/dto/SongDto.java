package com.djt.jukeanator_engine.domain.songlibrary.dto;

public class SongDto {
  private String name;
  private Integer songPlays;

  public SongDto(String name, Integer songPlays) {
    this.name = name;
    this.songPlays = songPlays;
  }

  public String getName() {
    return name;
  }

  public Integer getSongPlays() {
    return songPlays;
  }
}
