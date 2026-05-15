package com.djt.jukeanator_engine.domain.songplayer.service;

import static java.util.Objects.requireNonNull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackFinishedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackNextTrackRequestedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackPausedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackShutdownEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStoppedEvent;
import com.djt.jukeanator_engine.domain.songplayer.mapper.SongPlayerMapper;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.Player;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.VideoVlcMediaPlayer;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.VlcMediaPlayer;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.AddSongToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import jakarta.annotation.PreDestroy;

/**
 * @author tmyers
 */
public final class SongPlayerServiceImpl implements SongPlayerService {

  private static final Logger log = LoggerFactory.getLogger(SongPlayerServiceImpl.class);

  /**
   * ONE AND ONLY ONE queue-processing thread.
   */
  private final ExecutorService queueExecutor =
      Executors.newSingleThreadExecutor(Thread.ofPlatform().name("song-queue-thread").factory());

  private final ApplicationEventPublisher eventPublisher;
  private final Deque<SongQueueEntryDto> playbackHistory = new ArrayDeque<>();
  private final Player player;
  private final String playerType;
  private final String rootPath;
  private final SongLibraryRepository songLibraryRepository;
  private final SongQueueService songQueueService;

  /**
   * Everything below is confined to the queueExecutor thread.
   */
  private RootFolderEntity songLibraryRoot;
  private SongQueueEntryDto nowPlayingSong;
  private List<String> genres = new ArrayList<>();
  private List<String> artists = new ArrayList<>();
  private List<AlbumFolderEntity> albums = new ArrayList<>();

  public SongPlayerServiceImpl(
      String playerType, String rootPath,
      SongLibraryRepository songLibraryRepository, 
      SongQueueService songQueueService,
      ApplicationEventPublisher eventPublisher) {

    requireNonNull(playerType, "playerType cannot be null");
    requireNonNull(rootPath, "rootPath cannot be null");
    requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
    requireNonNull(songQueueService, "songQueueService cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");

    this.playerType = playerType;
    this.rootPath = rootPath;
    this.songLibraryRepository = songLibraryRepository;
    this.songQueueService = songQueueService;
    this.eventPublisher = eventPublisher;

    if (this.playerType.equals("vlc")) {
      this.player = new VlcMediaPlayer();
    } else {
      this.player = new VideoVlcMediaPlayer();
    }

    log.info("Using song library root: " + this.songLibraryRoot);
    log.info("Using : " + this.playerType);

    // Initialize the song library root and song queue
    initialize();

    /*
     * Whenever playback finishes, queue processing is re-submitted onto the SAME single executor
     * thread.
     */
    this.player.setOnFinished(this::submitQueueProcessing);
  }

  @Override
  public NowPlayingSongDto getNowPlayingSong() {

    SongQueueEntryDto current = this.nowPlayingSong;
    if (current != null) {
      
      return new NowPlayingSongDto(current.getCoverArtPath(), current.getArtistName(), current.getAlbumName(), current.getSongName());
    }

    return SongPlayerMapper.EMPTY_NOW_PLAYING_SONG_DTO;
  }

  @Override
  public SongPlaybackStatusDto getPlaybackStatus() {

    Long elapsedSeconds = 0L;
    Long totalSeconds = 0L;
    SongPlayerStatus songPlayerStatus = player.getStatus();

    if (songPlayerStatus != SongPlayerStatus.STOPPED) {

      elapsedSeconds = player.getElapsedSeconds();
      totalSeconds = player.getTotalLengthSeconds();
    }

    return new SongPlaybackStatusDto(songPlayerStatus, elapsedSeconds, totalSeconds);
  }

  @Override
  public void playNextTrack() {

    eventPublisher.publishEvent(new SongPlaybackNextTrackRequestedEvent());

    player.stop();

    submitQueueProcessing();
  }

  @Override
  public void pause() {

    player.pause();

    eventPublisher.publishEvent(new SongPlaybackPausedEvent(nowPlayingSong));
  }

  @Override
  public void stop() {

    player.stop();

    eventPublisher.publishEvent(new SongPlaybackStoppedEvent(nowPlayingSong));
  }

  @PreDestroy
  public void shutdown() {

    log.info("Shutting down SongPlayerService");
    eventPublisher.publishEvent(new SongPlaybackShutdownEvent());
    queueExecutor.shutdownNow();
    player.stop();
    player.release();
  }

  private void initialize() {

    // If we cannot load the song library from disk at startup, then assume a
    // new install and return an empty root folder. The application will
    // automatically ask the user to scan for songs at startup.
    try {
      this.songLibraryRoot = this.songLibraryRepository.loadAggregateRoot(rootPath);
    } catch (EntityDoesNotExistException ednee) {
      throw new IllegalStateException("Could not load song library from: " + rootPath, ednee);
    }

    this.albums = this.songLibraryRoot.getAllAlbums();
    this.artists.clear();
    this.genres.clear();

    for (AlbumFolderEntity album : this.albums) {

      String artist = album.getParentArtist().getName();
      if (!this.artists.contains(artist)) {
        this.artists.add(artist);
      }

      String genre = album.getParentGenre().getName();
      if (!this.genres.contains(genre)) {
        this.genres.add(genre);
      }
    }

    submitQueueProcessing();
  }

  @EventListener
  public void handleAddSongToQueueEvent(AddSongToQueueEvent event) {

    log.info("""
        Received AddSongToQueueEvent:{}
        """, event);

    submitQueueProcessing();
  }

  /**
   * THE ONLY PLACE processQueue() IS EVER INVOKED.
   */
  private void submitQueueProcessing() {

    queueExecutor.submit(() -> {

      try {

        processQueue();

      } catch (Exception e) {

        log.error("Queue processing failed", e);
      }
    });
  }

  /**
   * Runs ONLY on the single queueExecutor thread.
   */
  private void processQueue() {

    try {

      /*
       * If something is already playing or paused, do nothing.
       */
      SongPlayerStatus status = player.getStatus();

      if (status != SongPlayerStatus.STOPPED) {
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
       * Ask the queue service for the next song. The queue service owns: - queue mutation - queue
       * persistence - queue events
       */
      SongQueueEntryDto nextSong = songQueueService.dequeueNextSong();

      if (nextSong == null) {

        nowPlayingSong = null;

        log.debug("No songs remaining in queue");

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