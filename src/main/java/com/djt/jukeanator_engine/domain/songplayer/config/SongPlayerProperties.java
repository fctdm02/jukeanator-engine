package com.djt.jukeanator_engine.domain.songplayer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "song-player")
public class SongPlayerProperties {

  /*
   * VLC: All operating systems
   * Winamp: Windows only
   */
  private String playerType = "vlc";

  public String getPlayerType() {
    return playerType;
  }

  public void setPlayerType(String playerType) {
    this.playerType = playerType;
  }
}
