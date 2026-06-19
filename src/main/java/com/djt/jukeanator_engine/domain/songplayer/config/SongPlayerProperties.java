package com.djt.jukeanator_engine.domain.songplayer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "song-player")
public class SongPlayerProperties {

  /*
   * VLC: All operating systems Winamp: Windows only
   */
  private String playerType = "vlc";
  
  private String winampExePath = "C:\\\\Program Files (x86)\\\\Winamp\\\\winamp.exe";

  /*
   * 0: Completely muted 100: 100% of the normal volume 200: 200% volume (up to 2x software
   * amplification)
   */
  private int volume = 100;

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

  public int getVolume() {
    return volume;
  }

  public void setVolume(int volume) {
    this.volume = volume;
  }
}
