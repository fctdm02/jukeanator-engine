package com.djt.jukeanator_engine.domain.songplayer.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueEmptyEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * Starts/stops line-in monitoring based on jukebox playback state: monitoring runs whenever
 * {@code enableBackgroundMusic} is false AND the song queue is empty AND the song player is not
 * playing and stops the instant when a song gets queued, or background music gets re-enabled.
 */
public class JukeboxAudioCoordinator {

  private static final Logger log = LoggerFactory.getLogger(JukeboxAudioCoordinator.class);

  private final LineInService lineInService;
  private final SongQueueService songQueueService;
  private final SongPlayerService songPlayerService;

  public JukeboxAudioCoordinator(
      LineInService lineInService, 
      SongQueueService songQueueService,
      SongPlayerService songPlayerService) {

    this.lineInService = lineInService;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;
    
    reconcile();
  }

  @EventListener
  public void handleSongQueueEmptyEvent(SongQueueEmptyEvent event) {
    reconcile();
  }

  @EventListener
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {
    reconcile();
  }

  @EventListener
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {
    reconcile();
  }

  public void reconcile() {
    
    if (lineInService.isLineInOnSilenceEnabled()) {

      boolean shouldMonitor =
          songPlayerService.getPlaybackStatus().status().equals(SongPlayerStatus.STOPPED)
              && songQueueService.isQueueEmpty() && !songQueueService.isBackgroundMusicEnabled();

      if (shouldMonitor && !lineInService.isMonitoring()) {
        if (lineInService.isLineInAvailable()) {
          log.info(
              "Queue/Player inactive and background music disabled - starting line-in monitoring");
          lineInService.startMonitoring();
        }
      } else if (!shouldMonitor && lineInService.isMonitoring()) {
        log.info("Queue/Player active or background music enabled - stopping line-in monitoring");
        lineInService.stopMonitoring();
      }
    }
  }
}
