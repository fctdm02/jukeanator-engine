package com.djt.jukeanator_engine.domain.backgroundmusic.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Plain, human-readable JSON representation of a {@code BackgroundMusicSongEntity}. Kept free of
 * any {@code AbstractEntity} machinery so the persisted file only ever contains the fields below.
 *
 * @author tmyers
 */
public class BackgroundMusicSongDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private Integer persistentIdentity;
  private String songFilePath;
  private Instant timeLastPlayed;
  private int numberOfPlays;

  public BackgroundMusicSongDto() {}

  public BackgroundMusicSongDto(Integer persistentIdentity, String songFilePath,
      Instant timeLastPlayed, int numberOfPlays) {
    super();
    this.persistentIdentity = persistentIdentity;
    this.songFilePath = songFilePath;
    this.timeLastPlayed = timeLastPlayed;
    this.numberOfPlays = numberOfPlays;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }

  public void setPersistentIdentity(Integer persistentIdentity) {
    this.persistentIdentity = persistentIdentity;
  }

  public String getSongFilePath() {
    return songFilePath;
  }

  public void setSongFilePath(String songFilePath) {
    this.songFilePath = songFilePath;
  }

  public Instant getTimeLastPlayed() {
    return timeLastPlayed;
  }

  public void setTimeLastPlayed(Instant timeLastPlayed) {
    this.timeLastPlayed = timeLastPlayed;
  }

  public int getNumberOfPlays() {
    return numberOfPlays;
  }

  public void setNumberOfPlays(int numberOfPlays) {
    this.numberOfPlays = numberOfPlays;
  }

  @Override
  public int hashCode() {
    return Objects.hash(persistentIdentity);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null || getClass() != obj.getClass())
      return false;
    BackgroundMusicSongDto other = (BackgroundMusicSongDto) obj;
    return Objects.equals(persistentIdentity, other.persistentIdentity);
  }

  @Override
  public String toString() {
    return "BackgroundMusicSongDto [persistentIdentity=" + persistentIdentity + ", songFilePath="
        + songFilePath + ", timeLastPlayed=" + timeLastPlayed + ", numberOfPlays=" + numberOfPlays
        + "]";
  }
}
