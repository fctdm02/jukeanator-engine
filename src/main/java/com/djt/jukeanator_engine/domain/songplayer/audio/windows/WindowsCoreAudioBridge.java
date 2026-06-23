package com.djt.jukeanator_engine.domain.songplayer.audio.windows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.ClassPathResource;

/**
 * Runs the bundled PowerShell helper script (audio/windows-master-volume.ps1) that talks to the
 * Windows Core Audio API (IAudioEndpointVolume) through inline C# (Add-Type). This avoids any extra
 * Maven dependency - no JNA, no native DLL - at the cost of spawning a short-lived powershell.exe
 * process per call (typically 150-400ms; fine for occasional volume get/set calls, not something to
 * call in a tight loop).
 *
 * IMPORTANT: this was written against the documented Core Audio COM vtable layout but could not be
 * exercised against a real Windows machine in this environment. Test get/set on an actual target
 * machine before shipping. If it fails, run the script manually with "powershell -File
 * windows-master-volume.ps1 -Action get" to see the raw error - CoreAudioException below also
 * surfaces stderr.
 */
public final class WindowsCoreAudioBridge {

  private static volatile Path scriptPath;

  private WindowsCoreAudioBridge() {}

  public static synchronized int getMasterVolumeScalarPercent() {
    String output = run("get", null);
    try {
      return Integer.parseInt(output.trim());
    } catch (NumberFormatException e) {
      throw new CoreAudioException(
          "Could not parse volume from PowerShell output: '" + output + "'", e);
    }
  }

  public static synchronized void setMasterVolumeScalarPercent(int percent) {
    run("set", percent);
  }

  private static String run(String action, Integer volume) {
    try {
      Path script = ensureScriptExtracted();
      ProcessBuilder pb =
          new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
              "Bypass", "-File", script.toAbsolutePath().toString(), "-Action", action);
      if (volume != null) {
        pb.command().add("-Volume");
        pb.command().add(String.valueOf(volume));
      }
      Process process = pb.start();

      String stdout = readAll(process.getInputStream());
      String stderr = readAll(process.getErrorStream());

      boolean finished = process.waitFor(10, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new CoreAudioException(
            "powershell.exe timed out while " + action + "ting master volume");
      }
      if (process.exitValue() != 0) {
        throw new CoreAudioException("powershell.exe exited " + process.exitValue() + " while "
            + action + "ting master volume. stderr: " + stderr);
      }
      return stdout;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new CoreAudioException("Failed to invoke Windows Core Audio bridge script", e);
    }
  }

  private static String readAll(InputStream in) throws IOException {
    return new String(in.readAllBytes());
  }

  private static Path ensureScriptExtracted() throws IOException {
    Path local = scriptPath;
    if (local != null && Files.exists(local)) {
      return local;
    }
    synchronized (WindowsCoreAudioBridge.class) {
      if (scriptPath != null && Files.exists(scriptPath)) {
        return scriptPath;
      }
      Path tempDir = Files.createTempDirectory("jukebox-audio");
      Path target = tempDir.resolve("windows-master-volume.ps1");
      try (InputStream in =
          new ClassPathResource("audio/windows-master-volume.ps1").getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
      scriptPath = target;
      return target;
    }
  }

  public static class CoreAudioException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CoreAudioException(String message) {
      super(message);
    }

    public CoreAudioException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
