package com.djt.jukeanator_engine.domain.songplayer.audio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jukebox.audio.line-in")
public class LineInProperties {

  /** 0-100, applied as software gain to the monitored line-in signal. */
  private int defaultVolume = 75;

  /**
   * Optional substring to match a preferred mixer name, e.g. "Line In". Case-insensitive. Leave
   * blank to let LineInServiceImpl auto-detect - use MixerDiagnostics to find the exact name on
   * real hardware if auto-detection picks the wrong device.
   */
  private String preferredMixerName = "";

  public int getDefaultVolume() {
    return defaultVolume;
  }

  public void setDefaultVolume(int defaultVolume) {
    this.defaultVolume = defaultVolume;
  }

  public String getPreferredMixerName() {
    return preferredMixerName;
  }

  public void setPreferredMixerName(String preferredMixerName) {
    this.preferredMixerName = preferredMixerName;
  }
}
