package com.djt.jukeanator_engine.domain.songqueue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties bound to the {@code song-queue:} YAML prefix.
 *
 * <p>Note: the filesystem root path previously held here has been moved to
 * {@code app.root-path} / {@code app.root-path-windows} and is resolved via
 * {@link com.djt.jukeanator_engine.config.AppProperties#getEffectiveRootPath()}.
 */
@Validated
@ConfigurationProperties(prefix = "song-queue")
public class SongQueueProperties {

  private String repositoryType; // "filesystem" or "postgres"
  
  private boolean resetQueueAtStartup = true; // Whether or not to start with an empty song queue

  // Minimum number of songs to keep queued; only relevant when background music is enabled (see
  // com.djt.jukeanator_engine.domain.backgroundmusic.config.BackgroundMusicProperties)
  private int minimumNumberSongsToKeepInQueue = 5;

  // SONG QUEUE CONSTRAINTS
  private int minimumMinutesBetweenSongPlays = 60;
  private int maximumConsecutiveSongPlaysByArtist = 3;
  private boolean allowExplicitSongsAtAllTimes = false;
  private int allowExplicitSongsBegin = 21; // # In 24 hour/military time (e.g. 21:00 hours is (9:00PM))
  private int allowExplicitSongsEnd = 5; // # In 24 hour/military time (e.g. 5:00 hours is (5:00AM))

  public String getRepositoryType() {
    return repositoryType;
  }

  public void setRepositoryType(String repositoryType) {
    this.repositoryType = repositoryType;
  }

  public boolean isResetQueueAtStartup() {
    return resetQueueAtStartup;
  }

  public void setResetQueueAtStartup(boolean resetQueueAtStartup) {
    this.resetQueueAtStartup = resetQueueAtStartup;
  }

  public int getMinimumNumberSongsToKeepInQueue() {
    return minimumNumberSongsToKeepInQueue;
  }

  public void setMinimumNumberSongsToKeepInQueue(int minimumNumberSongsToKeepInQueue) {
    this.minimumNumberSongsToKeepInQueue = minimumNumberSongsToKeepInQueue;
  }

  public int getMinimumMinutesBetweenSongPlays() {
    return minimumMinutesBetweenSongPlays;
  }

  public void setMinimumMinutesBetweenSongPlays(int minimumMinutesBetweenSongPlays) {
    this.minimumMinutesBetweenSongPlays = minimumMinutesBetweenSongPlays;
  }

  public int getMaximumConsecutiveSongPlaysByArtist() {
    return maximumConsecutiveSongPlaysByArtist;
  }

  public void setMaximumConsecutiveSongPlaysByArtist(int maximumConsecutiveSongPlaysByArtist) {
    this.maximumConsecutiveSongPlaysByArtist = maximumConsecutiveSongPlaysByArtist;
  }

  public boolean isAllowExplicitSongsAtAllTimes() {
    return allowExplicitSongsAtAllTimes;
  }

  public void setAllowExplicitSongsAtAllTimes(boolean allowExplicitSongsAtAllTimes) {
    this.allowExplicitSongsAtAllTimes = allowExplicitSongsAtAllTimes;
  }

  public int getAllowExplicitSongsBegin() {
    return allowExplicitSongsBegin;
  }

  public void setAllowExplicitSongsBegin(int allowExplicitSongsBegin) {
    this.allowExplicitSongsBegin = allowExplicitSongsBegin;
  }

  public int getAllowExplicitSongsEnd() {
    return allowExplicitSongsEnd;
  }

  public void setAllowExplicitSongsEnd(int allowExplicitSongsEnd) {
    this.allowExplicitSongsEnd = allowExplicitSongsEnd;
  }
}