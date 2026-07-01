package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class BackgroundMusicSongDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private String songPathName; // For windows, this will have the drive letter stripped, as it is
                               // stored in the root
  private LocalDate lastPlayedOnDate;
  private Integer numPlays;

  public BackgroundMusicSongDto(String songPathName, LocalDate lastPlayedOnDate, Integer numPlays) {
    super();
    this.songPathName = songPathName;
    this.lastPlayedOnDate = lastPlayedOnDate;
    this.numPlays = numPlays;
  }

  public String getSongPathName() {
    return songPathName;
  }

  public void setSongPathName(String songPathName) {
    this.songPathName = songPathName;
  }

  public LocalDate getLastPlayedOnDate() {
    return lastPlayedOnDate;
  }

  public void setLastPlayedOnDate(LocalDate lastPlayedOnDate) {
    this.lastPlayedOnDate = lastPlayedOnDate;
  }

  public Integer getNumPlays() {
    return numPlays;
  }

  public void setNumPlays(Integer numPlays) {
    this.numPlays = numPlays;
  }

  @Override
  public int hashCode() {
    return Objects.hash(songPathName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    BackgroundMusicSongDto other = (BackgroundMusicSongDto) obj;
    return Objects.equals(songPathName, other.songPathName);
  }

  @Override
  public String toString() {
    return "BackgroundMusicSongDto [songPathName=" + songPathName + ", lastPlayedOnDate="
        + lastPlayedOnDate + ", numPlays=" + numPlays + "]";
  }
}
