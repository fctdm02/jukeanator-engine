package com.djt.jukeanator_engine.domain.songplayer.audio;

/**
 * Cross-platform abstraction for reading and adjusting the operating system's master (system-wide)
 * output volume. A platform-specific implementation is selected at startup based on os.name - see
 * {@link com.jukebox.audio.config.AudioPlatformConfig}.
 */
public interface MasterVolumeService {

  /**
   * @return current master volume as a percentage, 0-100.
   */
  int getMasterVolume();

  /**
   * @param percent desired master volume, 0-100. Values outside this range are clamped by the
   *        implementation.
   */
  void setMasterVolume(int percent);
}
