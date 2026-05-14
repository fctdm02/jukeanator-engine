package com.djt.jukeanator_engine.domain.songplayer.service;

import static java.util.Objects.requireNonNull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
import com.djt.jukeanator_engine.domain.songplayer.event.SongQueueChangedEvent;
import com.djt.jukeanator_engine.domain.songplayer.mapper.SongPlayerMapper;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.Player;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.VideoVlcMediaPlayer;
import com.djt.jukeanator_engine.domain.songplayer.service.utils.VlcMediaPlayer;
import com.djt.jukeanator_engine.domain.songqueue.event.AddSongToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.mapper.SongQueueMapper;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;
import jakarta.annotation.PreDestroy;

/**
 * @author tmyers
 */
public final class SongPlayerServiceImpl implements SongPlayerService {

  private static final Logger log = LoggerFactory.getLogger(SongPlayerServiceImpl.class);

  private final ApplicationEventPublisher eventPublisher;
  private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
  private final Deque<SongQueueEntryEntity> playbackHistory = new ArrayDeque<>();  
  private String playerType;
  private final Player player;

  private String rootPath;
  private SongLibraryRepository songLibraryRepository;
  private RootFolderEntity songLibraryRoot;

  private SongQueueRepository songQueueRepository;
  private SongQueueRootEntity songQueueRoot;
  
  private SongQueueEntryEntity nowPlayingSong;
  private List<String> queuedSongNames = new ArrayList<>();

  private List<String> genres = new ArrayList<>();
  private List<String> artists = new ArrayList<>();
  private List<AlbumFolderEntity> albums = new ArrayList<>();

  public SongPlayerServiceImpl(
      String playerType, 
      String rootPath,
      SongLibraryRepository songLibraryRepository, 
      SongQueueRepository songQueueRepository,
      ApplicationEventPublisher eventPublisher) {

    requireNonNull(playerType, "playerType cannot be null");
    requireNonNull(rootPath, "rootPath cannot be null");
    requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
    requireNonNull(songQueueRepository, "songQueueRepository cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");

    this.playerType = playerType;
    this.rootPath = rootPath;
    this.songLibraryRepository = songLibraryRepository;
    this.songQueueRepository = songQueueRepository;
    this.eventPublisher = eventPublisher;
    
    if (this.playerType.equals("vlc")) {
      player = new VlcMediaPlayer();
    } else {
      player = new VideoVlcMediaPlayer();
    }

    log.info("Using song library root: " + this.songLibraryRoot);
    log.info("Using song queue root: " + this.songQueueRoot);
    log.info("Using : " + this.playerType);
    
    // Initialize the song library root and song queue
    initialize();    
  }

  @Override
  public NowPlayingSongDto getNowPlayingSong() {

    if (nowPlayingSong != null) {
      return SongPlayerMapper.toDto(nowPlayingSong);
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

    return new SongPlaybackStatusDto(
        songPlayerStatus, 
        elapsedSeconds,
        totalSeconds);
  }

  @Override
  public void playNextTrack() {

    eventPublisher.publishEvent(new SongPlaybackNextTrackRequestedEvent());

    player.stop();

    processQueue();
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
    executorService.shutdownNow();
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
      log.error("Could not load song library from: " + rootPath
          + ", using empty song library root for now, error: " + ednee.getMessage());
      this.songQueueRoot = new SongQueueRootEntity(rootPath);
    }

    try {
      this.songQueueRoot =
          this.songQueueRepository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    } catch (EntityDoesNotExistException ednee) {
      log.error("Could not load song queue from: " + rootPath
          + ", using empty song library root for now, error: " + ednee.getMessage());
      this.songQueueRoot = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
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

    startQueueMonitor();
  }

  private void startQueueMonitor() {

    executorService.scheduleWithFixedDelay(this::processQueue, 0, 1, TimeUnit.SECONDS);
  }
  
  @EventListener
  public void handleAddSongToQueueEvent(AddSongToQueueEvent event) {

    log.info("""
        Received AddSongToQueueEvent:{}
        """,
        event
    );
    
    try {
      this.songQueueRoot = this.songQueueRepository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    } catch (EntityDoesNotExistException ednee) {
      log.error("Could not load song queue from: " + rootPath + ", using empty song queue root for now, error: " + ednee.getMessage());
      this.songQueueRoot = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    }    
    
    processQueue();
  }  
  

  private void processQueue() {

    try {

      // If the queue has changed in any way, then publish
      List<String> latestQueuedSongNames = getQueuedSongNames();
      if (!latestQueuedSongNames.equals(queuedSongNames)) {
        
        eventPublisher.publishEvent(new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));
        this.queuedSongNames = latestQueuedSongNames;
      }

      
      // If a song is playing/paused, then do nothing (until it has finished and the player status is STOPPED)
      SongPlayerStatus status = player.getStatus();
      if (status != SongPlayerStatus.STOPPED) {
        return;
      }

      if (nowPlayingSong != null) {

        eventPublisher.publishEvent(new SongPlaybackFinishedEvent(nowPlayingSong));
      }

      List<SongQueueEntryEntity> queue = songQueueRoot.getSongs();
      if (queue.isEmpty()) {

        nowPlayingSong = null;
        return;
      }

      SongQueueEntryEntity nextSong = queue.getFirst();

      songQueueRoot.removeSongFromQueue(nextSong);

      songQueueRepository.storeAggregateRoot(songQueueRoot);

      if (nowPlayingSong != null) {

        playbackHistory.push(nowPlayingSong);
      }

      nowPlayingSong = nextSong;

      String songPath = nextSong.getSong().getNaturalIdentity();

      log.info("Playing song: {}", songPath);

      player.playSongMedia(songPath);

      eventPublisher.publishEvent(new SongPlaybackStartedEvent(nextSong));

    } catch (Exception e) {

      log.error("Queue processing failed", e);
    }
  }
  
  private List<String> getQueuedSongNames() {
    
    List<String> list = new ArrayList<>();
    List<SongQueueEntryEntity> songs = songQueueRoot.getSongs();
    if (!songs.isEmpty()) {

      for (SongQueueEntryEntity song: songs) {
       
        list.add(song.getSong().getNaturalIdentity());        
      }      
    }    
    return list;
  }
}
