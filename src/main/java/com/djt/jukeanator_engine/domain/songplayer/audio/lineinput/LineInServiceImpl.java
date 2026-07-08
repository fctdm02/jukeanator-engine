package com.djt.jukeanator_engine.domain.songplayer.audio.lineinput;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.songplayer.audio.LineInService;

public class LineInServiceImpl implements LineInService {

  private static final Logger log = LoggerFactory.getLogger(LineInServiceImpl.class);

  private final boolean enableLineInOnSilence;
  private final String preferredMixerName;
  private final AtomicInteger volumePercent;
  private final AtomicBoolean signalPresent = new AtomicBoolean(false);

  private final Object lock = new Object();
  private Thread monitorThread;
  private LineInMonitorTask monitorTask;

  public LineInServiceImpl(boolean enableLineInOnSilence, String preferredMixerName,
      int lineInVolume) {

    this.enableLineInOnSilence = enableLineInOnSilence;
    this.preferredMixerName = preferredMixerName;
    this.volumePercent = new AtomicInteger(lineInVolume);

    log.info("preferredMixerName: " + this.preferredMixerName);
    log.info("preferredMixerName: " + this.preferredMixerName);
    log.info("lineInVolume: " + lineInVolume);
  }
  
  @Override
  public boolean isLineInOnSilenceEnabled() {
    return enableLineInOnSilence;
  }

  @Override
  public boolean isLineInAvailable() {
    return findLineInMixer() != null;
  }

  @Override
  public boolean isLineInReceivingSignal() {
    return signalPresent.get();
  }

  @Override
  public int getLineInVolume() {
    return volumePercent.get();
  }

  @Override
  public void setLineInVolume(int percent) {
    volumePercent.set(Math.max(0, Math.min(100, percent)));
  }

  @Override
  public void startMonitoring() {

    if (this.enableLineInOnSilence) {

      synchronized (lock) {
        if (monitorThread != null && monitorThread.isAlive()) {
          return;
        }

        Mixer.Info mixerInfo = findLineInMixer();
        if (mixerInfo == null) {
          log.warn("Cannot start line-in monitoring: no capture-capable mixer found");
          return;
        }

        monitorTask = new LineInMonitorTask(mixerInfo, volumePercent, signalPresent);
        monitorThread = new Thread(monitorTask, "line-in-monitor");
        monitorThread.setDaemon(true);
        monitorThread.setPriority(Thread.MAX_PRIORITY);
        monitorThread.start();
      }
    }
  }

  @Override
  public void stopMonitoring() {

    synchronized (lock) {

      if (monitorTask != null) {
        monitorTask.stop();
      }

      if (monitorThread != null) {

        try {
          monitorThread.join(2000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }

      monitorThread = null;
      monitorTask = null;
    }
  }

  @Override
  public boolean isMonitoring() {

    synchronized (lock) {
      return monitorThread != null && monitorThread.isAlive();
    }
  }

  /**
   * Best-effort discovery of a usable line-in capture mixer.
   *
   * Java Sound has no single universal "this is the line-in jack" concept across OSes/drivers, so
   * this: 1. Honors jukebox.audio.line-in.preferred-mixer-name if set - run MixerDiagnostics.main()
   * on the target machine to see exact mixer names and pick the right one. 2. Otherwise prefers any
   * mixer whose name suggests a line/aux input, since that's usually right on Windows and Linux. 3.
   * Falls back to the first capture-capable mixer found, since many machines only expose one
   * recording device anyway.
   */
  Mixer.Info findLineInMixer() {

    Mixer.Info fallback = null;

    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      Mixer mixer = AudioSystem.getMixer(info);
      if (!supportsCapture(mixer)) {
        continue;
      }

      if (preferredMixerName != null && !preferredMixerName.isBlank()
          && info.getName().toLowerCase().contains(preferredMixerName.toLowerCase())) {
        return info;
      }

      String name = info.getName().toLowerCase();
      if (name.contains("line in") || name.contains("line-in") || name.contains("linein")
          || name.contains("external") || name.contains("aux")) {
        return info;
      }

      if (fallback == null) {
        fallback = info;
      }
    }
    return fallback;
  }

  private boolean supportsCapture(Mixer mixer) {

    for (Line.Info info : mixer.getTargetLineInfo()) {
      if (info instanceof DataLine.Info dataLineInfo
          && TargetDataLine.class.isAssignableFrom(dataLineInfo.getLineClass())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "LineInServiceImpl [preferredMixerName=" + preferredMixerName + ", volumePercent="
        + volumePercent + "]";
  }
}
