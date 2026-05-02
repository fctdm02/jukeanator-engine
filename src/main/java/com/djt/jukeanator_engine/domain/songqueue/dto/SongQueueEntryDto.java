package com.djt.jukeanator_engine.domain.songqueue.dto;

public class SongQueueEntryDto {
  private String name;
  private Integer songPlays;
  private Integer priority;

  public SongQueueEntryDto(String name, Integer songPlays, Integer priority) {
    this.name = name;
    this.songPlays = songPlays;
    this.priority = priority;
  }

  public String getName() {
    return name;
  }

  public Integer getSongPlays() {
    return songPlays;
  }
  
  public Integer getPriority() {
    return priority;
  }
}
