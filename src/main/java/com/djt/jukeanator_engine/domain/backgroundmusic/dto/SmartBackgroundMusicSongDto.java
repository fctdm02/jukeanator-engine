package com.djt.jukeanator_engine.domain.backgroundmusic.dto;

import java.time.Instant;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartAdditionReason;

/**
 * Plain, human-readable JSON representation of a {@code SmartBackgroundMusicSongEntity}.
 *
 * @author tmyers
 */
public class SmartBackgroundMusicSongDto extends BackgroundMusicSongDto {

  private static final long serialVersionUID = 1L;

  private String sourceSong;
  private Integer sourceSongNumPlays;
  private SmartAdditionReason reason;

  public SmartBackgroundMusicSongDto() {}

  public SmartBackgroundMusicSongDto(Integer persistentIdentity, String songFilePath,
      Instant timeLastPlayed, int numberOfPlays, String sourceSong, Integer sourceSongNumPlays,
      SmartAdditionReason reason) {
    super(persistentIdentity, songFilePath, timeLastPlayed, numberOfPlays);
    this.sourceSong = sourceSong;
    this.sourceSongNumPlays = sourceSongNumPlays;
    this.reason = reason;
  }

  public String getSourceSong() {
    return sourceSong;
  }

  public void setSourceSong(String sourceSong) {
    this.sourceSong = sourceSong;
  }

  public Integer getSourceSongNumPlays() {
    return sourceSongNumPlays;
  }

  public void setSourceSongNumPlays(Integer sourceSongNumPlays) {
    this.sourceSongNumPlays = sourceSongNumPlays;
  }

  public SmartAdditionReason getReason() {
    return reason;
  }

  public void setReason(SmartAdditionReason reason) {
    this.reason = reason;
  }

  @Override
  public String toString() {
    return "SmartBackgroundMusicSongDto [sourceSong=" + sourceSong + ", sourceSongNumPlays="
        + sourceSongNumPlays + ", reason=" + reason + ", persistentIdentity="
        + getPersistentIdentity() + ", songFilePath=" + getSongFilePath() + ", timeLastPlayed="
        + getTimeLastPlayed() + ", numberOfPlays=" + getNumberOfPlays() + "]";
  }
}
