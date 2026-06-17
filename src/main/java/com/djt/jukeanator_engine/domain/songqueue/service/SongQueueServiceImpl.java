package com.djt.jukeanator_engine.domain.songqueue.service;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
  private final int allowExplicitSongsBegin;
  private final int allowExplicitSongsEnd;

  private String rootPath;
  private RootFolderEntity songLibraryRoot;
  private SongQueueRootEntity songQueueRoot;

  // Helper record/class to track history of queued artists within a rolling 2-hour window
  private static class ArtistQueueRecord {
    final String artistName;
    final Instant queuedAt;

    ArtistQueueRecord(String artistName, Instant queuedAt) {
      this.artistName = artistName;
      this.queuedAt = queuedAt;
    }
  }

  private final List<ArtistQueueRecord> artistQueueHistory = new CopyOnWriteArrayList<>();

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

    initialize();

    log.info("Using song library root: " + this.songLibraryRoot);
  }

  @Override
  public synchronized SongQueueEntryDto dequeueNextSong() {

    List<SongQueueEntryEntity> songs = songQueueRoot.getSongs();

    if (songs.isEmpty()) {
      return null;
    }

    SongQueueEntryEntity nextSong = songs.getFirst();
    songQueueRoot.removeSongFromQueue(nextSong);
    songQueueRepository.storeAggregateRoot(songQueueRoot);

    if (enableBackgroundMusic) {

      autoPopulateQueue();
    }

    eventPublisher
        .publishEvent(new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));

    return SongQueueMapper.toDto(nextSong);
  }

  /**
   * When background music is enabled, fills the queue up to {@code minimumNumberSongsToKeepInQueue}
   * by drawing random songs from the background-music playlist. Each candidate is checked with
   * {@link #isSongEligibleForQueue}; ineligible songs are skipped. A hard cap of 50 attempts
   * prevents an infinite loop when the eligible pool is exhausted.
   *
   * <p>
   * This method is called both after a song is dequeued (steady-state top-up) <em>and</em> during
   * {@link #initialize()} so that the queue is seeded on startup even when no prior persisted songs
   * exist.
   *
   * <p>
   * Must be called while holding {@code this} monitor (i.e. from a {@code synchronized} context).
   */
  private void autoPopulateQueue() {

    int attempts = 0;
    while (songQueueRoot.getSongs().size() < minimumNumberSongsToKeepInQueue && attempts < 50) {

      attempts++;
      SongFileEntity randomSong =
          this.songLibraryRoot.getRandomSongFromBackgroundMusicPlaylist(this.rootPath);

      if (randomSong == null) {
        break; // Background-music playlist is empty; nothing more we can do.
      }

      Integer albumId = randomSong.getAlbum().getPersistentIdentity();
      Integer songId = randomSong.getPersistentIdentity();

      if (isSongEligibleForQueue(albumId, songId, 0)) {
        addSongToQueue("BACKGROUND_MUSIC", albumId, songId, 0);
      }
    }

    if (attempts == 50 && songQueueRoot.getSongs().size() < minimumNumberSongsToKeepInQueue) {
      log.warn(
          "autoPopulateQueue: reached 50-attempt limit; could only fill queue to {} of {} required songs",
          songQueueRoot.getSongs().size(), minimumNumberSongsToKeepInQueue);
    }
  }

  @Override
  public Integer getHighestPriority() {
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

    Instant now = Instant.now();

    try {

      // Fetch the target song entity
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album == null) {
        return false;
      }
      SongFileEntity targetSong = album.getChildSong(songId);
      if (targetSong == null) {
        return false;
      }

      // Constraints Checking A: Minimum minutes between matching song plays in current queue
      for (SongQueueEntryEntity queuedEntry : songQueueRoot.getSongs()) {
        if (queuedEntry.getSong().getPersistentIdentity() == songId
            && queuedEntry.getSong().getAlbum().getPersistentIdentity() == albumId) {
          long minutesBetween = Duration.between(queuedEntry.getQueuedAtTime(), now).toMinutes();
          if (minutesBetween < minimumMinutesBetweenSongPlays) {
            return false;
          }
        }
      }

      // Constraints Checking B: Maximum consecutive plays by artist within a 2-hour window
      Instant twoHoursAgo = now.minus(Duration.ofHours(2));
      artistQueueHistory.removeIf(record -> record.queuedAt.isBefore(twoHoursAgo));

      int consecutiveCount = 0;
      String incomingArtist = targetSong.getArtistName();

      // Scan backward through history to verify if adding this song violates the consecutive limit
      for (int i = artistQueueHistory.size() - 1; i >= 0; i--) {
        if (artistQueueHistory.get(i).artistName.equalsIgnoreCase(incomingArtist)) {
          consecutiveCount++;
        } else {
          break; // Sequence broken
        }
      }
      if (consecutiveCount >= maximumConsecutiveSongPlaysByArtist) {
        return false;
      }

      // Constraints Checking C: Explicit content hour filtering
      if (!allowExplicitSongsAtAllTimes && targetSong.hasExplicit()) {
        int currentHour = ZonedDateTime.now(ZoneId.systemDefault()).getHour();
        boolean isEligibleTimeWindow;

        if (allowExplicitSongsBegin <= allowExplicitSongsEnd) {
          isEligibleTimeWindow =
              (currentHour >= allowExplicitSongsBegin && currentHour <= allowExplicitSongsEnd);
        } else {
          // Handle wrap-around scenarios (e.g., Begin: 21 [9 PM] to End: 5 [5 AM] next day)
          isEligibleTimeWindow =
              (currentHour >= allowExplicitSongsBegin || currentHour <= allowExplicitSongsEnd);
        }

        if (!isEligibleTimeWindow) {
          return false;
        }
      }

    } catch (Exception e) {
      // TODO: Handle this better
      e.printStackTrace();
      return false;
    }

    return true;
  }

  @Override
  public SongQueueEntryDto addSongToQueue(AddSongToQueueRequest addSongToQueueRequest) {
    SongQueueEntryDto queueEntryDto =
        addSongToQueue(addSongToQueueRequest.getUsername(), addSongToQueueRequest.getAlbumId(),
            addSongToQueueRequest.getSongId(), addSongToQueueRequest.getPriority());

    eventPublisher.publishEvent(new SongAddedToQueueEvent(queueEntryDto));
    eventPublisher.publishEvent(new SongQueueChangedEvent(getQueuedSongs()));

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

    eventPublisher.publishEvent(new MultipleSongsAddedToQueueEvent(queueEntries));
    eventPublisher.publishEvent(new SongQueueChangedEvent(getQueuedSongs()));

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
      Integer priority = 0;

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

          // Track when the artist was successfully queued
          artistQueueHistory.add(new ArtistQueueRecord(song.getArtistName(), Instant.now()));

          return SongQueueMapper.toDto(queueEntry);
        }
      }
    } catch (EntityDoesNotExistException e) {
    }

    throw new SongQueueException("Could not add song to queue, albumId: " + albumId + ", songId: "
        + songId + ", priority: " + priority);
  }

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

  @Override
  public CommandResponse processCommand(CommandRequest commandRequest) {
    throw new SongLibraryException("Not implemented yet!");
  }

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
    initialize();
  }

  private synchronized void initialize() {
    try {
      this.songLibraryRoot = this.songLibraryRepository.loadAggregateRoot(rootPath);
    } catch (EntityDoesNotExistException ednee) {
      log.error("Could not load song library from: " + rootPath
          + ", using empty song library root for now, error: " + ednee.getMessage());
      this.songUserRepositoryReset();
    }

    try {
      this.songQueueRoot =
          this.songQueueRepository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    } catch (EntityDoesNotExistException ednee) {
      log.error("Could not load song queue from: " + rootPath
          + ", using empty song library root for now, error: " + ednee.getMessage());
      this.songQueueRoot = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    }

    // Seed the queue with background music if it is below the minimum threshold.
    // This handles the cold-start case where there are no persisted songs in the queue,
    // so playback can begin immediately without waiting for dequeueNextSong() to be called first.
    if (enableBackgroundMusic) {

      autoPopulateQueue();
    }
  }

  private void songUserRepositoryReset() {
    this.songQueueRoot = new SongQueueRootEntity(rootPath);
  }
}
