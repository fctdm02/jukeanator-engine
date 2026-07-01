package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class SmartBackgroundMusicSongDto implements Serializable {

  private static final long serialVersionUID = 1L;

  /* This will be the song from which this smart song was used as a source */
  private String coreSongPathName;

  /*
   * This will be the reason why this smart song was picked (e.g. same album, same artist, popular
   * song from same genre, popular song from library, song from playlist
   */
  private String coreSongReason;

  /* For windows, this will have the drive letter stripped, as it is stored in the root */
  private String songPathName;

  /* When the song was last played */
  private LocalDate lastPlayedOnDate;

  /* Total number of song plays for this song */
  private Integer numPlays;

  public SmartBackgroundMusicSongDto(String coreSongPathName, String coreSongReason,
      String songPathName, LocalDate lastPlayedOnDate, Integer numPlays) {
    super();
    this.coreSongPathName = coreSongPathName;
    this.coreSongReason = coreSongReason;
    this.songPathName = songPathName;
    this.lastPlayedOnDate = lastPlayedOnDate;
    this.numPlays = numPlays;
  }

  public String getCoreSongPathName() {
    return coreSongPathName;
  }

  public void setCoreSongPathName(String coreSongPathName) {
    this.coreSongPathName = coreSongPathName;
  }

  public String getCoreSongReason() {
    return coreSongReason;
  }

  public void setCoreSongReason(String coreSongReason) {
    this.coreSongReason = coreSongReason;
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
    return Objects.hash(coreSongPathName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SmartBackgroundMusicSongDto other = (SmartBackgroundMusicSongDto) obj;
    return Objects.equals(coreSongPathName, other.coreSongPathName);
  }

  @Override
  public String toString() {
    return "SmartBackgroundMusicSongDto [coreSongPathName=" + coreSongPathName + ", coreSongReason="
        + coreSongReason + ", songPathName=" + songPathName + ", lastPlayedOnDate="
        + lastPlayedOnDate + ", numPlays=" + numPlays + "]";
  }

}
