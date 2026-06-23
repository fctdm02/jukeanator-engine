package com.djt.jukeanator_engine.domain.songplayer.audio;

/**
 * Cross-platform line-in (auxiliary input) control. Volume here is applied entirely in software as
 * a digital gain on the monitored signal, which is what makes it work identically on Windows, Linux
 * and macOS regardless of whether the underlying sound driver exposes a separate hardware "line-in
 * level" control (many don't, especially on macOS).
 */
public interface LineInService {

  /**
   * @return true if a capture device that looks like a line-in/aux input was found on this machine.
   *         Detection is best-effort - see {@code LineInServiceImpl.findLineInMixer()} and the
   *         {@code MixerDiagnostics} utility for how to pin it down exactly on real hardware.
   */
  boolean isLineInAvailable();

  /**
   * @return true if monitoring is currently running AND the most recently captured audio block had
   *         non-trivial amplitude (i.e. something is actually plugged in and playing, not just
   *         silence). Always false while monitoring is stopped.
   */
  boolean isLineInReceivingSignal();

  /**
   * @return current line-in monitoring volume, 0-100. Default is 75.
   */
  int getLineInVolume();

  /**
   * @param percent desired line-in monitoring volume, 0-100. Clamped. Takes effect immediately,
   *        even mid-stream.
   */
  void setLineInVolume(int percent);

  /**
   * Begin capturing from the line-in device and playing it through the default speakers. Safe to
   * call if already monitoring (no-op).
   */
  void startMonitoring();

  /**
   * Stop capturing/playing line-in audio. Safe to call if not currently monitoring (no-op). Blocks
   * briefly until the monitoring thread has actually shut down.
   */
  void stopMonitoring();

  boolean isMonitoring();
}
