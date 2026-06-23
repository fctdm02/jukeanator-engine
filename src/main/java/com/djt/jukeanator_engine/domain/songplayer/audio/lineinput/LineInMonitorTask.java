package com.djt.jukeanator_engine.domain.songplayer.audio.lineinput;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Continuously reads PCM audio from a line-in capture line and writes it back out to the default
 * playback line, scaling sample amplitude by a configurable gain along the way. Doing the gain in
 * software (rather than relying on a hardware/driver mixer control) means the volume behavior is
 * identical on Windows, Linux and macOS regardless of what controls the underlying driver happens
 * to expose for the capture device - macOS in particular often exposes no input gain control at all
 * for line-in.
 *
 * Typical round-trip latency with this buffer size is roughly 10-40ms, which is unnoticeable for
 * ambient background audio.
 */
public class LineInMonitorTask implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(LineInMonitorTask.class);

  private static final AudioFormat[] FORMAT_ATTEMPTS = {new AudioFormat(44100f, 16, 2, true, false),
      new AudioFormat(44100f, 16, 1, true, false), new AudioFormat(22050f, 16, 2, true, false),
      new AudioFormat(22050f, 16, 1, true, false),};

  private static final int BUFFER_FRAMES = 2048;
  // RMS threshold for 16-bit PCM (full scale = 32767); roughly 1% of
  // full scale, tuned to ignore electrical noise floor on a silent
  // line-in jack while still catching quiet audio.
  private static final double SIGNAL_RMS_THRESHOLD = 327.0;

  private final Mixer.Info mixerInfo;
  private final AtomicInteger volumePercent;
  private final AtomicBoolean signalPresent;
  private final AtomicBoolean running = new AtomicBoolean(true);

  public LineInMonitorTask(Mixer.Info mixerInfo, AtomicInteger volumePercent,
      AtomicBoolean signalPresent) {
    this.mixerInfo = mixerInfo;
    this.volumePercent = volumePercent;
    this.signalPresent = signalPresent;
  }

  public void stop() {
    running.set(false);
  }

  @Override
  public void run() {
    TargetDataLine lineIn = null;
    SourceDataLine speakers = null;
    try {
      Mixer mixer = AudioSystem.getMixer(mixerInfo);
      AudioFormat format = pickSupportedFormat(mixer);
      if (format == null) {
        log.warn("No supported capture format found on mixer '{}', line-in monitoring disabled",
            mixerInfo.getName());
        return;
      }

      DataLine.Info lineInInfo = new DataLine.Info(TargetDataLine.class, format);
      lineIn = (TargetDataLine) mixer.getLine(lineInInfo);
      lineIn.open(format, format.getFrameSize() * BUFFER_FRAMES);
      lineIn.start();

      DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
      speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
      speakers.open(format);
      speakers.start();

      int bufferBytes = format.getFrameSize() * BUFFER_FRAMES;
      byte[] buffer = new byte[bufferBytes];
      boolean is16BitPcm = format.getSampleSizeInBits() == 16;

      log.info("Line-in monitoring started on mixer '{}' ({})", mixerInfo.getName(), format);

      while (running.get()) {
        int bytesRead = lineIn.read(buffer, 0, buffer.length);
        if (bytesRead <= 0) {
          continue;
        }

        if (is16BitPcm) {
          applyGainAndDetectSignal(buffer, bytesRead);
        }

        speakers.write(buffer, 0, bytesRead);
      }
    } catch (LineUnavailableException e) {
      log.error("Line-in monitoring could not open an audio line", e);
    } finally {
      signalPresent.set(false);
      closeQuietly(lineIn);
      closeQuietly(speakers);
      log.info("Line-in monitoring stopped");
    }
  }

  /**
   * Scales 16-bit little-endian PCM samples in place by the current gain, and updates the
   * signal-present flag from the block's RMS.
   */
  private void applyGainAndDetectSignal(byte[] buffer, int length) {
    float gain = clampVolume(volumePercent.get()) / 100f;
    long sumSquares = 0;
    int sampleCount = 0;

    for (int i = 0; i + 1 < length; i += 2) {
      short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
      sumSquares += (long) sample * (long) sample;
      sampleCount++;

      int scaled = Math.round(sample * gain);
      if (scaled > Short.MAX_VALUE)
        scaled = Short.MAX_VALUE;
      if (scaled < Short.MIN_VALUE)
        scaled = Short.MIN_VALUE;

      buffer[i] = (byte) scaled;
      buffer[i + 1] = (byte) (scaled >> 8);
    }

    if (sampleCount > 0) {
      double rms = Math.sqrt((double) sumSquares / sampleCount);
      signalPresent.set(rms > SIGNAL_RMS_THRESHOLD);
    }
  }

  private int clampVolume(int percent) {
    return Math.max(0, Math.min(100, percent));
  }

  private AudioFormat pickSupportedFormat(Mixer mixer) {
    for (AudioFormat format : FORMAT_ATTEMPTS) {
      DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
      if (mixer.isLineSupported(info)) {
        return format;
      }
    }
    return null;
  }

  private void closeQuietly(Line line) {
    if (line != null) {
      try {
        if (line instanceof TargetDataLine targetDataLine) {
          targetDataLine.stop();
        }
        if (line instanceof SourceDataLine sourceDataLine) {
          sourceDataLine.drain();
          sourceDataLine.stop();
        }
        line.close();
      } catch (Exception e) {
        log.debug("Error closing audio line", e);
      }
    }
  }
}
