package com.djt.jukeanator_engine.domain.songplayer.audio.windows;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.songplayer.audio.MasterVolumeService;

public class WindowsMasterVolumeService implements MasterVolumeService {

  private static final Logger log = LoggerFactory.getLogger(WindowsMasterVolumeService.class);

  @Override
  public int getMasterVolume() {
    try {
      return WindowsCoreAudioBridge.getMasterVolumeScalarPercent();
    } catch (WindowsCoreAudioBridge.CoreAudioException e) {
      log.warn("Unable to read Windows master volume, defaulting to 100", e);
      return 100;
    }
  }

  @Override
  public void setMasterVolume(int percent) {
    int clamped = clamp(percent);
    try {
      WindowsCoreAudioBridge.setMasterVolumeScalarPercent(clamped);
    } catch (WindowsCoreAudioBridge.CoreAudioException e) {
      log.error("Unable to set Windows master volume to {}", clamped, e);
      throw e;
    }
  }

  private int clamp(int percent) {
    return Math.max(0, Math.min(100, percent));
  }
  
  @Override
  public String toString() {
    return "WindowsMasterVolumeService";
  }
}
