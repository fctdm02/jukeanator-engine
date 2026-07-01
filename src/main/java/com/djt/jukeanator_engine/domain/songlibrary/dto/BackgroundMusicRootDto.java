package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.io.Serializable;
import java.util.List;

public class BackgroundMusicRootDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String windowsDriveLetter;
  private final List<BackgroundMusicSongDto> songs;

  public BackgroundMusicRootDto(String windowsDriveLetter, List<BackgroundMusicSongDto> songs) {
    super();
    this.windowsDriveLetter = windowsDriveLetter;
    this.songs = songs;
  }

  public String getWindowsDriveLetter() {
    return windowsDriveLetter;
  }

  public List<BackgroundMusicSongDto> getSongs() {
    return songs;
  }
}
