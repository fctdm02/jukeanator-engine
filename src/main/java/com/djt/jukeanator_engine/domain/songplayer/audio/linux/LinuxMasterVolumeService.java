package com.djt.jukeanator_engine.domain.songplayer.audio.linux;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.songplayer.audio.MasterVolumeService;

/**
 * Uses PulseAudio's `pactl` CLI to read/write the default sink volume. Works unchanged under
 * PipeWire on most modern distros, since PipeWire ships a pactl-compatible shim (pipewire-pulse).
 * If a target system only has raw PipeWire without that shim, swap in `wpctl
 * get-volume @DEFAULT_AUDIO_SINK@` / `wpctl set-volume` - the parsing logic here is easy to adapt.
 */
public class LinuxMasterVolumeService implements MasterVolumeService {

  private static final Logger log = LoggerFactory.getLogger(LinuxMasterVolumeService.class);
  private static final Pattern VOLUME_PATTERN = Pattern.compile("(\\d+)%");

  @Override
  public int getMasterVolume() {
    try {
      String sink = defaultSink();
      String out = exec("pactl", "get-sink-volume", sink);
      Matcher m = VOLUME_PATTERN.matcher(out);
      if (m.find()) {
        return Integer.parseInt(m.group(1));
      }
      log.warn("Could not parse pactl output: {}", out);
      return 100;
    } catch (Exception e) {
      log.warn("Unable to read Linux master volume, defaulting to 100", e);
      return 100;
    }
  }

  @Override
  public void setMasterVolume(int percent) {
    int clamped = Math.max(0, Math.min(100, percent));
    try {
      String sink = defaultSink();
      exec("pactl", "set-sink-volume", sink, clamped + "%");
    } catch (Exception e) {
      log.error("Unable to set Linux master volume to {}", clamped, e);
      throw new RuntimeException(e);
    }
  }

  private String defaultSink() throws IOException, InterruptedException {
    String sink = exec("pactl", "get-default-sink").trim();
    return sink.isEmpty() ? "@DEFAULT_SINK@" : sink;
  }

  private String exec(String... command) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    Process p = pb.start();
    String output = new String(p.getInputStream().readAllBytes());
    if (!p.waitFor(5, TimeUnit.SECONDS)) {
      p.destroyForcibly();
      throw new IOException("Command timed out: " + String.join(" ", command));
    }
    if (p.exitValue() != 0) {
      throw new IOException(
          "Command failed (" + p.exitValue() + "): " + String.join(" ", command) + " -> " + output);
    }
    return output;
  }
}
