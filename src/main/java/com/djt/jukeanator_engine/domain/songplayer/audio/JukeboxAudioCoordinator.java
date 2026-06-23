package com.djt.jukeanator_engine.domain.songplayer.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
  private final SongQueueStateProvider queueStateProvider;

  public JukeboxAudioCoordinator(LineInService lineInService,
      SongQueueStateProvider queueStateProvider) {
    this.lineInService = lineInService;
    this.queueStateProvider = queueStateProvider;
  }

  @Scheduled(fixedDelay = 1000)
  public void reconcile() {
    boolean shouldMonitor =
        queueStateProvider.isQueueEmpty() && !queueStateProvider.isBackgroundMusicEnabled();

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
