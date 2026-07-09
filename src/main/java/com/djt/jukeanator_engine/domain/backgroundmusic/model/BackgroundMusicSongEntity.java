package com.djt.jukeanator_engine.domain.backgroundmusic.model;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * @author tmyers
 */
public class BackgroundMusicSongEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  private String songFilePath;
  private Instant timeLastPlayed; // null means the song has not yet been played this cycle
  private int numberOfPlays;

  public BackgroundMusicSongEntity() {}

  public BackgroundMusicSongEntity(Integer persistentIdentity, String songFilePath) {
    super(persistentIdentity);
    requireNonNull(songFilePath, "songFilePath cannot be null");
    this.songFilePath = songFilePath;
    this.timeLastPlayed = null;
    this.numberOfPlays = 0;
  }

  public BackgroundMusicSongEntity(Integer persistentIdentity, String songFilePath,
      Instant timeLastPlayed, int numberOfPlays) {
    super(persistentIdentity);
    requireNonNull(songFilePath, "songFilePath cannot be null");
    this.songFilePath = songFilePath;
    this.timeLastPlayed = timeLastPlayed;
    this.numberOfPlays = numberOfPlays;
  }

  @Override
  public String getNaturalIdentity() {
    return this.songFilePath;
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

  public boolean isNotYetPlayed() {
    return this.timeLastPlayed == null;
  }

  public void markPlayed(Instant when) {
    this.timeLastPlayed = when;
    this.numberOfPlays = this.numberOfPlays + 1;
  }
}
