package com.djt.jukeanator_engine.domain.songplayer.audio.lineinput;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Standalone diagnostic - run this ON THE ACTUAL JUKEBOX HARDWARE to see what Java Sound exposes
 * for capture devices. Sound card/driver naming varies a lot, so this is the fastest way to find
 * out whether auto-detection in LineInServiceImpl will pick the right device, or whether you need
 * to set jukebox.audio.line-in.preferred-mixer-name to the exact (or partial, case-insensitive)
 * name of your line-in jack - e.g. if it's auto-picking an onboard microphone instead.
 *
 * Run with: mvn -q exec:java -Dexec.mainClass=com.jukebox.audio.lineinput.MixerDiagnostics
 * (requires the exec-maven-plugin, or just run the main method from your IDE)
 */
public final class MixerDiagnostics {

  private MixerDiagnostics() {}

  public static void main(String[] args) {
    System.out.println("Capture-capable audio mixers on this machine:");
    System.out.println("----------------------------------------------");
    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      Mixer mixer = AudioSystem.getMixer(info);
      boolean canCapture = false;
      for (Line.Info lineInfo : mixer.getTargetLineInfo()) {
        if (lineInfo instanceof DataLine.Info dataLineInfo
            && TargetDataLine.class.isAssignableFrom(dataLineInfo.getLineClass())) {
          canCapture = true;
          break;
        }
      }
      System.out.printf("name=\"%s\" | description=\"%s\" | capture-capable=%s%n", info.getName(),
          info.getDescription(), canCapture);
    }
  }
}
