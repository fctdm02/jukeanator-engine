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
  
  // BACKGROUND MUSIC (THROUGH LINE IN AUDIO JACK)
  private boolean enableBackgroundMusic = false;
  private String preferredMixerName = "Line In";
  private int lineInVolume = 75;
  
  // SONG QUEUE CONSTRAINTS
  private int minimumNumberSongsToKeepInQueue = 5;
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

  public boolean isEnableBackgroundMusic() {
    return enableBackgroundMusic;
  }

  public void setEnableBackgroundMusic(boolean enableBackgroundMusic) {
    this.enableBackgroundMusic = enableBackgroundMusic;
  }

  public String getPreferredMixerName() {
    return preferredMixerName;
  }

  public void setPreferredMixerName(String preferredMixerName) {
    this.preferredMixerName = preferredMixerName;
  }

  public int getLineInVolume() {
    return lineInVolume;
  }

  public void setLineInVolume(int lineInVolume) {
    this.lineInVolume = lineInVolume;
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