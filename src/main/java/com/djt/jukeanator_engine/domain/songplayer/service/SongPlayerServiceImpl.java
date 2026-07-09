package com.djt.jukeanator_engine.domain.songplayer.service;

import static java.util.Objects.requireNonNull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.common.security.SecurityContextPropagatingRunnable;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector.OSType;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.audio.LineInService;
import com.djt.jukeanator_engine.domain.songplayer.audio.MasterVolumeService;
import com.djt.jukeanator_engine.domain.songplayer.config.SongPlayerProperties;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;
import com.djt.jukeanator_engine.domain.songplayer.event.AllSongsDonePlayingEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackFinishedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackNextTrackRequestedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackPausedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackShutdownEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStoppedEvent;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.Player;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.VideoVlcMediaPlayer;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.VlcMediaPlayer;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.WinampMediaPlayer;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import jakarta.annotation.PreDestroy;

/**
 * @author tmyers
 */
public class SongPlayerServiceImpl implements SongPlayerService {

  private static final Logger log = LoggerFactory.getLogger(SongPlayerServiceImpl.class);

  /**
   * ONE AND ONLY ONE queue-processing thread.
   */
  private final ExecutorService queueExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = Thread.ofPlatform().name("song-queue-thread").unstarted(r);
    t.setPriority(Thread.MAX_PRIORITY);
    return t;
  });

  private final String playerType;
  private final int playerVolume;
  private final int masterVolume;

  private final SongQueueService songQueueService;
  private final MasterVolumeService masterVolumeService;
  private final LineInService lineInService;
  private final ApplicationEventPublisher eventPublisher;

  private final Deque<SongQueueEntryDto> playbackHistory = new ArrayDeque<>();
  private final Player player;

  /**
   * Everything below is confined to the queueExecutor thread, with the exception of
   * {@code queueLocked}, which is written from external callers (e.g. the hibernation timer on the
   * Swing EDT) and read on the queue executor thread, so it must be volatile.
   */
  private SongQueueEntryDto nowPlayingSong;
  private SongPlayerStatus songPlayerStatus;

  /**
   * When {@code true}, {@link #processQueue()} will not dequeue or start any new song. Set via
   * {@link #lockQueue()} / {@link #unlockQueue()}.
   */
  private volatile boolean queueLocked = false;

  public SongPlayerServiceImpl(SongPlayerProperties songPlayerProperties,
      SongQueueService songQueueService, MasterVolumeService masterVolumeService,
      LineInService lineInService, ApplicationEventPublisher eventPublisher) {

    requireNonNull(songPlayerProperties, "songPlayerProperties cannot be null");
    requireNonNull(songQueueService, "songQueueService cannot be null");
    requireNonNull(masterVolumeService, "masterVolumeService cannot be null");
    requireNonNull(lineInService, "lineInService cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");

    this.playerType = songPlayerProperties.getPlayerType();
    this.playerVolume = songPlayerProperties.getPlayerVolume();
    this.masterVolume = songPlayerProperties.getMasterVolume();
    
    this.songQueueService = songQueueService;
    this.masterVolumeService = masterVolumeService;
    this.lineInService = lineInService;
    this.eventPublisher = eventPublisher;

    OSType osType = OperatingSystemDetector.getOperatingSystem();
    if (this.playerType.equals("winamp") && osType == OSType.WINDOWS) {

      String winampPath = songPlayerProperties.getWinampExePath();
      this.player = new WinampMediaPlayer(winampPath, this.playerVolume);

    } else if (this.playerType.equals("video-vlc")) {

      this.player = new VideoVlcMediaPlayer(this.playerVolume);

    } else {

      this.player = new VlcMediaPlayer(this.playerVolume);

    }

    initialize();

    log.info("masterVolumeService : " + this.masterVolumeService);
    log.info("lineInService : " + this.lineInService);
    log.info("playerType : " + this.playerType);
    log.info("playerVolume : " + this.playerVolume);
    log.info("masterVolume : " + this.masterVolume);

    /*
     * Whenever playback finishes, queue processing is re-submitted onto the SAME single executor
     * thread.
     */
    this.player.setOnFinished(this::submitQueueProcessing);
  }

  private void initialize() {

    submitQueueProcessing();
  }

  @Override
  public SongDto getNowPlayingSong() {

    SongQueueEntryDto current = this.nowPlayingSong;
    if (current != null) {

      return current.getSong();
    }
    return null;
  }

  @Override
  public SongPlaybackStatusDto getPlaybackStatus() {

    Long elapsedSeconds = 0L;
    Long totalSeconds = 0L;
    songPlayerStatus = player.getStatus();

    if (songPlayerStatus != SongPlayerStatus.STOPPED) {

      elapsedSeconds = player.getElapsedSeconds();
      totalSeconds = player.getTotalLengthSeconds();
    }

    return new SongPlaybackStatusDto(songPlayerStatus, elapsedSeconds, totalSeconds);
  }

  @Override
  public void playNextTrack() {

    songPlayerStatus = player.getStatus();
    if (songPlayerStatus != SongPlayerStatus.STOPPED) {

      player.stop();
    }

    eventPublisher.publishEvent(new SongPlaybackNextTrackRequestedEvent());

    submitQueueProcessing();
  }

  @Override
  public void pause() {

    songPlayerStatus = player.getStatus();
    if (songPlayerStatus == SongPlayerStatus.PLAYING
        || songPlayerStatus == SongPlayerStatus.PAUSED) {

      player.pause();

      eventPublisher.publishEvent(new SongPlaybackPausedEvent(nowPlayingSong));
    }
  }

  @Override
  public void stop() {

    songPlayerStatus = player.getStatus();
    if (songPlayerStatus == SongPlayerStatus.PLAYING
        || songPlayerStatus == SongPlayerStatus.PAUSED) {

      player.stop();
      songPlayerStatus = SongPlayerStatus.STOPPED;
      eventPublisher.publishEvent(new SongPlaybackStoppedEvent(nowPlayingSong));

      submitQueueProcessing();
    }
  }

  @Override
  public void lockQueue() {

    queueLocked = true;
    log.info("Queue locked — no further songs will be dequeued until unlocked");

    // Stop whatever is currently playing so music does not continue
    // unattended while the lock is held.
    songPlayerStatus = player.getStatus();
    if (songPlayerStatus == SongPlayerStatus.PLAYING
        || songPlayerStatus == SongPlayerStatus.PAUSED) {

      player.stop();
      songPlayerStatus = SongPlayerStatus.STOPPED;
      eventPublisher.publishEvent(new SongPlaybackStoppedEvent(nowPlayingSong));
    }

    // Clear nowPlayingSong so that when unlockQueue() re-kicks processQueue(),
    // it does not mistake the hibernation-interrupted song for one that finished
    // naturally — which would push it into songPlayHistory and incorrectly trip
    // the "played in last N minutes" rule on the very next auto-populate cycle.
    nowPlayingSong = null;
  }

  @Override
  public void unlockQueue() {

    queueLocked = false;
    log.info("Queue unlocked — resuming normal queue processing");

    // Re-kick queue processing so the next song starts automatically if the
    // queue is non-empty and nothing else is playing.
    submitQueueProcessing();
  }

  @PreDestroy
  public void shutdown() {

    log.info("Shutting down SongPlayerService");
    eventPublisher.publishEvent(new SongPlaybackShutdownEvent());
    queueExecutor.shutdownNow();
    player.stop();
    player.release();
  }

  @EventListener
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {

    log.info("""
        Received SongAddedToQueueEvent:{}
        """, event.queueEntry());

    submitQueueProcessing();
  }

  @EventListener
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {

    log.info("""
        Received MultipleSongsAddedToQueueEvent:{}
        """, event.queueEntries());

    submitQueueProcessing();
  }

  /**
   * THE ONLY PLACE processQueue() IS EVER INVOKED.
   */
  private void submitQueueProcessing() {

    // Capture whatever authentication the calling thread currently holds.
    // If there is none (background callback, timer, etc.) fall back to the
    // internal SYSTEM principal so the security aspect is satisfied.
    Authentication callerAuth = SecurityContextHolder.getContext().getAuthentication();
    Authentication effectiveAuth = (callerAuth != null && callerAuth.isAuthenticated()) ? callerAuth
        : SystemPrincipal.SystemAuthenticationToken.INSTANCE;

    queueExecutor.submit(new SecurityContextPropagatingRunnable(() -> {
      try {
        processQueue();
      } catch (Exception e) {
        log.error("Queue processing failed", e);
      }
    }, effectiveAuth));
  }

  /**
   * Runs ONLY on the single queueExecutor thread.
   */
  private void processQueue() {

    try {

      /*
       * If something is already playing or paused, do nothing.
       */
      SongPlayerStatus previousSongPlayerStatus = songPlayerStatus;
      songPlayerStatus = player.getStatus();
      if (previousSongPlayerStatus != songPlayerStatus
          && songPlayerStatus == SongPlayerStatus.STOPPED) {
        eventPublisher.publishEvent(new AllSongsDonePlayingEvent());
      }
      if (songPlayerStatus != SongPlayerStatus.STOPPED) {
        return;
      }

      /*
       * If a previous song had been playing, move it into playback history and publish playback
       * finished event before advancing to the next song.
       */
      if (nowPlayingSong != null) {

        playbackHistory.push(nowPlayingSong);
        eventPublisher.publishEvent(new SongPlaybackFinishedEvent(nowPlayingSong));
      }

      /*
       * If the queue has been locked (e.g. during hibernation), do not dequeue or start any new
       * song. The player has already been stopped by lockQueue(), so we simply return and wait for
       * unlockQueue() to re-submit processing.
       */
      if (queueLocked) {
        log.debug("Queue is locked — skipping dequeue");
        return;
      }

      /*
       * Ask the queue service for the next song. The queue service owns: - queue mutation - queue
       * persistence - queue events
       */
      SongQueueEntryDto nextSong = songQueueService.dequeueNextSong();
      if (nextSong == null) {

        nowPlayingSong = null;
        log.debug("No songs remaining in queue");
        eventPublisher.publishEvent(new AllSongsDonePlayingEvent());
        return;
      }

      nowPlayingSong = nextSong;
      String songPath = nextSong.getSongPath();
      log.info("Playing song: {}", songPath);
      player.playSongMedia(songPath);
      eventPublisher.publishEvent(new SongPlaybackStartedEvent(nextSong));

    } catch (Exception e) {
      log.error("Queue processing failed", e);
    }
  }
}
