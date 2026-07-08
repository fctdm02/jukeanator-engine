package com.djt.jukeanator_engine.domain.songplayer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "song-player")
public class SongPlayerProperties {

  private String playerType = "vlc";
  private String winampExePath = "C:\\\\Program Files (x86)\\\\Winamp\\\\winamp.exe";
  private int playerVolume = 100;
  private int masterVolume = 100;
  
  // LINE IN MUSIC (MUTUALLY EXCLUSIVE TO BACKGROUND MUSIC), WILL BE ON WHEN NOTHING IN THE QUEUE
  private boolean enableLineInOnSilence = false; // mutually exclusive with enable-background-music
  private String preferredMixerName = "Line In";
  private int lineInVolume = 75;

  public String getPlayerType() {
    return playerType;
  }

  public void setPlayerType(String playerType) {
    this.playerType = playerType;
  }

  public String getWinampExePath() {
    return winampExePath;
  }

  public void setWinampExePath(String winampExePath) {
    this.winampExePath = winampExePath;
  }

  public int getPlayerVolume() {
    return playerVolume;
  }

  public void setPlayerVolume(int playerVolume) {
    this.playerVolume = playerVolume;
  }

  public int getMasterVolume() {
    return masterVolume;
  }

  public void setMasterVolume(int masterVolume) {
    this.masterVolume = masterVolume;
  }

  public boolean isEnableLineInOnSilence() {
    return enableLineInOnSilence;
  }

  public void setEnableLineInOnSilence(boolean enableLineInOnSilence) {
    this.enableLineInOnSilence = enableLineInOnSilence;
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
}
