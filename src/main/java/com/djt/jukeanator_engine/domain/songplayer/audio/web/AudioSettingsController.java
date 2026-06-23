package com.djt.jukeanator_engine.domain.songplayer.audio.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.songplayer.audio.LineInService;
import com.djt.jukeanator_engine.domain.songplayer.audio.MasterVolumeService;

/**
 * Optional - wire this up if you want your jukebox UI to show/adjust master volume and line-in
 * volume/status. Delete it if you'd rather call the services directly from existing controllers.
 */
@RestController
@RequestMapping("/api/audio")
public class AudioSettingsController {

  private final MasterVolumeService masterVolumeService;
  private final LineInService lineInService;

  public AudioSettingsController(MasterVolumeService masterVolumeService,
      LineInService lineInService) {
    this.masterVolumeService = masterVolumeService;
    this.lineInService = lineInService;
  }

  @GetMapping("/master-volume")
  public int getMasterVolume() {
    return masterVolumeService.getMasterVolume();
  }

  @PutMapping("/master-volume/{percent}")
  public void setMasterVolume(@PathVariable int percent) {
    masterVolumeService.setMasterVolume(percent);
  }

  @GetMapping("/line-in/volume")
  public int getLineInVolume() {
    return lineInService.getLineInVolume();
  }

  @PutMapping("/line-in/volume/{percent}")
  public void setLineInVolume(@PathVariable int percent) {
    lineInService.setLineInVolume(percent);
  }

  @GetMapping("/line-in/status")
  public LineInStatus lineInStatus() {
    return new LineInStatus(lineInService.isLineInAvailable(),
        lineInService.isLineInReceivingSignal(), lineInService.isMonitoring(),
        lineInService.getLineInVolume());
  }

  public record LineInStatus(boolean available, boolean receivingSignal, boolean monitoring,
      int volume) {
  }
}
