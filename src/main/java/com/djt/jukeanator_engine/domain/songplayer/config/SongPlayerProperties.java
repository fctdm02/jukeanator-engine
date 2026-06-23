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
}
