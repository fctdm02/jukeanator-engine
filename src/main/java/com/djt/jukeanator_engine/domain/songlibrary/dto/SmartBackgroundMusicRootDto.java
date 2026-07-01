package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.io.Serializable;
import java.util.List;

public class SmartBackgroundMusicRootDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String windowsDriveLetter;
  private final List<SmartBackgroundMusicSongDto> songs;

  public SmartBackgroundMusicRootDto(String windowsDriveLetter,
      List<SmartBackgroundMusicSongDto> songs) {
    super();
    this.windowsDriveLetter = windowsDriveLetter;
    this.songs = songs;
  }

  public String getWindowsDriveLetter() {
    return windowsDriveLetter;
  }

  public List<SmartBackgroundMusicSongDto> getSongs() {
    return songs;
  }

  @Override
  public String toString() {
    return "SmartBackgroundMusicRootDto [windowsDriveLetter=" + windowsDriveLetter + ", songs="
        + songs + "]";
  }
}
