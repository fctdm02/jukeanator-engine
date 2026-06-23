package com.djt.jukeanator_engine.domain.songplayer.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueEmptyEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * Starts/stops line-in monitoring based on jukebox playback state: monitoring runs whenever
 * {@code enableBackgroundMusic} is false AND the song queue is empty, and stops the instant either
 * condition changes (a song gets queued, or background music gets re-enabled).
 *
 * A 1-second poll keeps this decoupled from your queue's internal event model. If you want a
 * faster, event-driven reaction instead of waiting up to a second, call {@link #reconcile()}
 * directly from SongQueueServiceImpl whenever the queue changes or enableBackgroundMusic is toggled
 * - it's idempotent and cheap to call often.
 *
 * NOTE: remember to add {@code @EnableScheduling} to your main application class for the
 * {@code @Scheduled} poll to run.
 */
@Component
public class JukeboxAudioCoordinator {

  private static final Logger log = LoggerFactory.getLogger(JukeboxAudioCoordinator.class);

  private final LineInService lineInService;
  private final SongQueueService songQueueService;

  public JukeboxAudioCoordinator(LineInService lineInService, SongQueueService songQueueService) {

    this.lineInService = lineInService;
    this.songQueueService = songQueueService;
  }

  @EventListener
  public void handleSongQueueEmptyEvent(SongQueueEmptyEvent event) {

    // Instead of polling, we let the song queue service tell us when the queue is empty
    reconcile();
  }

  public void reconcile() {

    boolean shouldMonitor =
        songQueueService.isQueueEmpty() && !songQueueService.isBackgroundMusicEnabled();

    if (shouldMonitor && !lineInService.isMonitoring()) {
      if (lineInService.isLineInAvailable()) {
        log.info("Queue empty and background music disabled - starting line-in monitoring");
        lineInService.startMonitoring();
      }
    } else if (!shouldMonitor && lineInService.isMonitoring()) {
      log.info("Queue active or background music enabled - stopping line-in monitoring");
      lineInService.stopMonitoring();
    }
  }
}
