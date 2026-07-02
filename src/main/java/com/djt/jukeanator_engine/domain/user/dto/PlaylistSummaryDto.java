package com.djt.jukeanator_engine.domain.user.dto;

public class PlaylistSummaryDto {

  private final String name;
  private final int songCount;
  private final Integer firstSongAlbumId;

  public PlaylistSummaryDto(String name, int songCount, Integer firstSongAlbumId) {
    this.name = name;
    this.songCount = songCount;
    this.firstSongAlbumId = firstSongAlbumId;
  }

  public String getName() {
    return name;
  }

  public int getSongCount() {
    return songCount;
  }

  public Integer getFirstSongAlbumId() {
    return firstSongAlbumId;
  }
}
