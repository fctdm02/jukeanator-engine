package com.djt.jukeanator_engine.domain.songqueue.service;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songqueue.config.SongQueueProperties;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueChangedEvent;
import com.djt.jukeanator_engine.domain.songqueue.exception.SongQueueException;
import com.djt.jukeanator_engine.domain.songqueue.mapper.SongQueueMapper;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;
import com.djt.jukeanator_engine.domain.songqueue.service.utils.PlaylistManager;

/**
 * @author tmyers
 */
public final class SongQueueServiceImpl
    implements SongQueueService, AggregateRootService<SongQueueRootEntity> {

  private static final Logger log = LoggerFactory.getLogger(SongQueueServiceImpl.class);

  private final ApplicationEventPublisher eventPublisher;
  private final SongLibraryRepository songLibraryRepository;
  private final SongQueueRepository songQueueRepository;
  private final boolean enableBackgroundMusic;
  private final int minimumNumberSongsToKeepInQueue;
  private final int minimumMinutesBetweenSongPlays;
  private final int maximumConsecutiveSongPlaysByArtist;
  private final boolean allowExplicitSongsAtAllTimes;
  private final int allowExplicitSongsBegin; // # In 24 hour/military time (e.g. 21:00 hours is
                                             // 9:00PM) Only used when allowExplicitSongsAtAllTimes
                                             // is false
  private final int allowExplicitSongsEnd; // # In 24 hour/military time (e.g. 5:00 hours is 5:00AM)
                                           // Only used when allowExplicitSongsAtAllTimes is false

  private String rootPath;
  private RootFolderEntity songLibraryRoot;
  private SongQueueRootEntity songQueueRoot;

  public SongQueueServiceImpl(SongQueueProperties songQueueProperties,
      SongLibraryRepository songLibraryRepository, SongQueueRepository songQueueRepository,
      ApplicationEventPublisher eventPublisher) {

    requireNonNull(songQueueProperties, "songQueueProperties cannot be null");
    requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
    requireNonNull(songQueueRepository, "songQueueRepository cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");

    this.rootPath = songQueueProperties.getRootPath();
    this.songLibraryRepository = songLibraryRepository;
    this.songQueueRepository = songQueueRepository;
    this.eventPublisher = eventPublisher;

    this.enableBackgroundMusic = songQueueProperties.isEnableBackgroundMusic();
    this.minimumNumberSongsToKeepInQueue = songQueueProperties.getMinimumNumberSongsToKeepInQueue();
    this.minimumMinutesBetweenSongPlays = songQueueProperties.getMinimumMinutesBetweenSongPlays();
    this.maximumConsecutiveSongPlaysByArtist =
        songQueueProperties.getMaximumConsecutiveSongPlaysByArtist();
    this.allowExplicitSongsAtAllTimes = songQueueProperties.isAllowExplicitSongsAtAllTimes();
    this.allowExplicitSongsBegin = songQueueProperties.getAllowExplicitSongsBegin();
    this.allowExplicitSongsEnd = songQueueProperties.getAllowExplicitSongsEnd();

    // Initialize the song library root and song queue
    initialize();

    log.info("Using song library root: " + this.songLibraryRoot);
  }

  // Service methods
  @Override
  public synchronized SongQueueEntryDto dequeueNextSong() {

    List<SongQueueEntryEntity> songs = songQueueRoot.getSongs();

    if (songs.isEmpty()) {
      return null;
    }

    SongQueueEntryEntity nextSong = songs.getFirst();

    songQueueRoot.removeSongFromQueue(nextSong);

    songQueueRepository.storeAggregateRoot(songQueueRoot);

    eventPublisher
        .publishEvent(new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));

    return SongQueueMapper.toDto(nextSong);
  }

  @Override
  public Integer getHighestPriority() {

    // Return one higher than the current maximum priority in the queue.
    // The queue is maintained in descending priority order, so the first entry always
    // holds the highest priority — no need to scan the whole list.
    // Base value of 2 is returned when the queue is empty (priority 0 is reserved for
    // randomly selected songs, priority 1 for normal user-selected songs).
    List<SongQueueEntryEntity> songs = songQueueRoot.getSongs();
    if (songs.isEmpty()) {
      return Integer.valueOf(2);
    }
    return Integer.valueOf(songs.getFirst().getPriority().intValue() + 1);
  }

  @Override
  public List<SongQueueEntryDto> getQueuedSongs() {

    return SongQueueMapper.toDto(songQueueRoot.getSongs());
  }

  @Override
  public boolean isSongEligibleForQueue(Integer albumId, Integer songId, Integer priority) {

    // TODO: Implement
    throw new RuntimeException("Not implemented yet!");
  }

  @Override
  public SongQueueEntryDto addSongToQueue(AddSongToQueueRequest addSongToQueueRequest) {

    SongQueueEntryDto queueEntryDto =
        addSongToQueue(addSongToQueueRequest.getUsername(), addSongToQueueRequest.getAlbumId(),
            addSongToQueueRequest.getSongId(), addSongToQueueRequest.getPriority());

    // Publish the event
    eventPublisher.publishEvent(new SongAddedToQueueEvent(queueEntryDto)); // to increment num plays
    eventPublisher.publishEvent(new SongQueueChangedEvent(getQueuedSongs())); // On UI, to update
                                                                              // queue view

    return queueEntryDto;
  }

  @Override
  public List<SongQueueEntryDto> addAlbumToQueue(AddAlbumToQueueRequest addAlbumToQueueRequest) {

    if (addAlbumToQueueRequest == null) {
      return List.of();
    }

    String username = addAlbumToQueueRequest.getUsername();
    Integer albumId = addAlbumToQueueRequest.getAlbumId();
    Integer priority = addAlbumToQueueRequest.getPriority();

    List<SongIdentifier> songIdentifiers = new ArrayList<>();
    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {
        for (SongFileEntity song : album.getChildSongs()) {

          songIdentifiers.add(new SongIdentifier(albumId, song.getPersistentIdentity()));
        }
      }
    } catch (EntityDoesNotExistException e) {
      throw new SongQueueException("Could not add album to queue: username: " + username
          + ", albumId: " + albumId + ", priority: " + priority);
    }

    return addMultipleSongsToQueue(
        new AddMultipleSongsToQueueRequest(username, songIdentifiers, priority));
  }

  @Override
  public List<SongQueueEntryDto> addMultipleSongsToQueue(
      AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest) {

    if (addMultipleSongsToQueueRequest == null
        || addMultipleSongsToQueueRequest.getSongIdentifiers().isEmpty()) {
      return List.of();
    }

    List<SongQueueEntryDto> queueEntries = new ArrayList<>();

    for (SongIdentifier songIdentifier : addMultipleSongsToQueueRequest.getSongIdentifiers()) {

      queueEntries.add(
          addSongToQueue(addMultipleSongsToQueueRequest.getUsername(), songIdentifier.getAlbumId(),
              songIdentifier.getSongId(), addMultipleSongsToQueueRequest.getPriority()));
    }

    // Publish the events
    eventPublisher.publishEvent(new MultipleSongsAddedToQueueEvent(queueEntries)); // to increment
                                                                                   // num plays
    eventPublisher.publishEvent(new SongQueueChangedEvent(getQueuedSongs())); // On UI, to update
                                                                              // queue view

    return queueEntries;
  }

  @Override
  public Integer flushQueue() {

    Integer numSongsFlushed = songQueueRoot.flushQueue();

    songQueueRepository.storeAggregateRoot(songQueueRoot);

    eventPublisher
        .publishEvent(new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));

    return numSongsFlushed;
  }

  @Override
  public Integer randomizeQueue() {

    Integer numSongsRandomized = songQueueRoot.randomizeQueue();

    songQueueRepository.storeAggregateRoot(songQueueRoot);

    eventPublisher
        .publishEvent(new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));

    return numSongsRandomized;
  }

  @Override
  public Integer moveSongUpInQueue(ChangeSongQueueRequest changeSongQueueRequest) {

    int albumId = changeSongQueueRequest.getAlbumId();
    int songId = changeSongQueueRequest.getSongId();

    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {

        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {

          Integer numSongsInQueue = songQueueRoot.moveSongUpInQueue(song);
          if (numSongsInQueue.intValue() > 0) {

            songQueueRepository.storeAggregateRoot(songQueueRoot);

            eventPublisher.publishEvent(
                new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));
          }
          return numSongsInQueue;
        }
      }
    } catch (EntityDoesNotExistException e) {
    }

    throw new SongQueueException(
        "Could not add move song up in queue, albumId: " + albumId + ", songId: " + songId);
  }

  @Override
  public Integer moveSongDownInQueue(ChangeSongQueueRequest changeSongQueueRequest) {

    int albumId = changeSongQueueRequest.getAlbumId();
    int songId = changeSongQueueRequest.getSongId();

    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {

        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {

          Integer numSongsInQueue = songQueueRoot.moveSongDownInQueue(song);
          if (numSongsInQueue.intValue() > 0) {

            songQueueRepository.storeAggregateRoot(songQueueRoot);

            eventPublisher.publishEvent(
                new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));
          }
          return numSongsInQueue;
        }
      }
    } catch (EntityDoesNotExistException e) {
    }

    throw new SongQueueException(
        "Could not add move song down in queue, albumId: " + albumId + ", songId: " + songId);
  }

  @Override
  public Integer removeSongDownFromQueue(ChangeSongQueueRequest changeSongQueueRequest) {

    int albumId = changeSongQueueRequest.getAlbumId();
    int songId = changeSongQueueRequest.getSongId();

    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {

        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {

          Integer numSongsRemoved = songQueueRoot.removeSongFromQueue(song);
          if (numSongsRemoved.intValue() > 0) {

            songQueueRepository.storeAggregateRoot(songQueueRoot);

            eventPublisher.publishEvent(
                new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));
          }
          return numSongsRemoved;
        }
      }
    } catch (EntityDoesNotExistException e) {
    }

    throw new SongQueueException(
        "Could not add move song down in queue, albumId: " + albumId + ", songId: " + songId);
  }

  @Override
  public Integer saveQueueAsPlaylist(String filename) {

    try {

      List<String> songPathnames = new ArrayList<>();
      for (SongQueueEntryEntity queueEntry : this.songQueueRoot.getSongs()) {

        SongFileEntity song = queueEntry.getSong();
        String songPathname = song.getNaturalIdentity();
        songPathnames.add(songPathname);
      }

      PlaylistManager.savePlayList(new File(filename), songPathnames);

      return Integer.valueOf(songPathnames.size());

    } catch (Exception e) {
      throw new SongQueueException("Could not save queue as playlist: " + filename, e);
    }
  }

  @Override
  public Integer loadPlaylistIntoQueue(LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest) {

    String username = loadPlaylistIntoQueueRequest.getUsername();
    String filename = loadPlaylistIntoQueueRequest.getFilename();

    try {

      List<SongIdentifier> songIdentifiers = new ArrayList<>();
      Integer priority = 0; // TODO: Implement loadPlaylistIntoQueue() ability to specify priority

      for (String songPathname : PlaylistManager.loadPlayList(new File(filename))) {

        SongFileEntity song = this.songLibraryRoot.getSongByPath(songPathname);

        songIdentifiers.add(new SongIdentifier(song.getAlbum().getPersistentIdentity(),
            song.getPersistentIdentity()));
      }

      AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest =
          new AddMultipleSongsToQueueRequest(username, songIdentifiers, priority);

      addMultipleSongsToQueue(addMultipleSongsToQueueRequest);

      return Integer.valueOf(songIdentifiers.size());

    } catch (Exception e) {
      throw new SongQueueException(
          "Could not load playlist into queue: username: " + username + ", filename: " + filename,
          e);
    }
  }

  private SongQueueEntryDto addSongToQueue(String username, Integer albumId, Integer songId,
      Integer priority) {

    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {

        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {

          SongQueueEntryEntity queueEntry = songQueueRoot.addSongToQueue(username, song, priority);

          songQueueRepository.storeAggregateRoot(songQueueRoot);

          return SongQueueMapper.toDto(queueEntry);
        }
      }
    } catch (EntityDoesNotExistException e) {
    }

    throw new SongQueueException("Could not add song to queue, albumId: " + albumId + ", songId: "
        + songId + ", priority: " + priority);
  }

  // Repository methods
  @Override
  public SongQueueRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    return this.songQueueRepository.loadAggregateRoot(naturalIdentity);
  }

  @Override
  public SongQueueRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    return this.songQueueRepository.loadAggregateRoot(persistentIdentity);
  }

  @Override
  public void storeAggregateRoot(SongQueueRootEntity root) {

    this.songQueueRepository.storeAggregateRoot(root);
  }

  // Command methods
  @Override
  public CommandResponse processCommand(CommandRequest commandRequest) {

    throw new SongLibraryException("Not implemented yet!");
  }

  // Query methods
  @Override
  public QueryResponse<QueryRequest, QueryResponseItem> processQuery(QueryRequest queryRequest) {

    throw new SongLibraryException("Not implemented yet!");
  }

  @EventListener
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {

    log.info("""
        Received ScanFileSystemForSongsEvent:
        scanPath={}
        albumCount={}
        """, event.scanPath(), event.albumCount());

    this.rootPath = event.scanPath();

    // Refresh song library state
    initialize();
  }

  private void initialize() {

    // If we cannot load the song library from disk at startup, then assume a new install and return
    // an
    // empty root folder. The application will automatically ask the user to scan for songs at
    // startup.
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
  }
}
