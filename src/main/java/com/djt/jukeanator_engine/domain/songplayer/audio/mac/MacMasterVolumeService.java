package com.djt.jukeanator_engine.domain.songplayer.audio.mac;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.songplayer.audio.MasterVolumeService;

/**
 * Uses AppleScript (osascript) to read/write the macOS output volume. This is the standard,
 * reliable approach for master volume on macOS - native CoreAudio access from Java would require
 * JNI/JNA and buys little extra reliability for this use case.
 */
public class MacMasterVolumeService implements MasterVolumeService {

  private static final Logger log = LoggerFactory.getLogger(MacMasterVolumeService.class);

  @Override
  public int getMasterVolume() {
    try {
      String out = exec("osascript", "-e", "output volume of (get volume settings)").trim();
      return Integer.parseInt(out);
    } catch (Exception e) {
      log.warn("Unable to read macOS master volume, defaulting to 100", e);
      return 100;
    }
  }

  @Override
  public void setMasterVolume(int percent) {
    int clamped = Math.max(0, Math.min(100, percent));
    try {
      exec("osascript", "-e", "set volume output volume " + clamped);
    } catch (Exception e) {
      log.error("Unable to set macOS master volume to {}", clamped, e);
      throw new RuntimeException(e);
    }
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
      throw new IOException("Command failed (" + p.exitValue() + "): " + output);
    }
    return output;
  }
}
