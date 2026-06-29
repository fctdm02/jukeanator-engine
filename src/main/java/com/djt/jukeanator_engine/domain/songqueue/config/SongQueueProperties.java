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
  
  // BACKGROUND MUSIC (MUTUALLY EXCLUSIVE TO LINE IN MUSIC), WILL BE EMPLOYED TO KEEP QUEUE AT A MIN SIZE
  // ASSUMES PLAYLIST FILE CALLED: "BackgroundMusic.TXT" EXISTS IN rootPath
  private boolean enableBackgroundMusic = false;
  private int minimumNumberSongsToKeepInQueue = 5;
  private boolean enableSmartBackgroundMusicAdditions = true; // will play songs from same artist/album from background music
  private int smartBackgroundMusicAdditionsFactor = 2; // for every song in BackgroundMusic.TXT, supplant with this number of songs by same album/artist, preferring popular songs  
  private int smartBackgroundMusicAdditionsBegin = 19; // start time for enableSmartBackgroundMusicAdditions
  private int smartBackgroundMusicAdditionsEnd = 5; // end time for enableSmartBackgroundMusicAdditions  
  
  // LINE IN MUSIC (MUTUALLY EXCLUSIVE TO BACKGROUND MUSIC), WILL BE ON WHEN NOTHING IN THE QUEUE
  private String preferredMixerName = "Line In";
  private int lineInVolume = 75;
  
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

  public boolean isEnableBackgroundMusic() {
    return enableBackgroundMusic;
  }

  public void setEnableBackgroundMusic(boolean enableBackgroundMusic) {
    this.enableBackgroundMusic = enableBackgroundMusic;
  }

  public boolean isEnableSmartBackgroundMusicAdditions() {
    return enableSmartBackgroundMusicAdditions;
  }

  public int getMinimumNumberSongsToKeepInQueue() {
    return minimumNumberSongsToKeepInQueue;
  }

  public void setMinimumNumberSongsToKeepInQueue(int minimumNumberSongsToKeepInQueue) {
    this.minimumNumberSongsToKeepInQueue = minimumNumberSongsToKeepInQueue;
  }
  
  public void setEnableSmartBackgroundMusicAdditions(boolean enableSmartBackgroundMusicAdditions) {
    this.enableSmartBackgroundMusicAdditions = enableSmartBackgroundMusicAdditions;
  }

  public int getSmartBackgroundMusicAdditionsFactor() {
    return smartBackgroundMusicAdditionsFactor;
  }

  public void setSmartBackgroundMusicAdditionsFactor(int smartBackgroundMusicAdditionsFactor) {
    this.smartBackgroundMusicAdditionsFactor = smartBackgroundMusicAdditionsFactor;
  }

  public int getSmartBackgroundMusicAdditionsBegin() {
    return smartBackgroundMusicAdditionsBegin;
  }

  public void setSmartBackgroundMusicAdditionsBegin(int smartBackgroundMusicAdditionsBegin) {
    this.smartBackgroundMusicAdditionsBegin = smartBackgroundMusicAdditionsBegin;
  }

  public int getSmartBackgroundMusicAdditionsEnd() {
    return smartBackgroundMusicAdditionsEnd;
  }

  public void setSmartBackgroundMusicAdditionsEnd(int smartBackgroundMusicAdditionsEnd) {
    this.smartBackgroundMusicAdditionsEnd = smartBackgroundMusicAdditionsEnd;
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