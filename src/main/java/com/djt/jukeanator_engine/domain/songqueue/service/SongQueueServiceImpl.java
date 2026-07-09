package com.djt.jukeanator_engine.domain.songqueue.service;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.BackgroundMusicService;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
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
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueEmptyEvent;
import com.djt.jukeanator_engine.domain.songqueue.exception.SongQueueServiceException;
import com.djt.jukeanator_engine.domain.songqueue.mapper.SongQueueMapper;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;
import com.djt.jukeanator_engine.domain.songqueue.service.utils.PlaylistManager;

/**
 * @author tmyers
 */
public class SongQueueServiceImpl
    implements SongQueueService, AggregateRootService<SongQueueRootEntity> {

  private static final Logger log = LoggerFactory.getLogger(SongQueueServiceImpl.class);

  private final ApplicationEventPublisher eventPublisher;
  private final SongLibraryService songLibraryService;
  private final BackgroundMusicService backgroundMusicService;
  private final SongQueueRepository songQueueRepository;

  // Whether or not to start with an empty song queue
  private final boolean resetQueueAtStartup;

  // Only relevant when backgroundMusicService.isEnabled() is true
  private final int minimumNumberSongsToKeepInQueue;

  // SONG QUEUE PLAY CONSTRAINTS
  private final int minimumMinutesBetweenSongPlays;
  private final int maximumConsecutiveSongPlaysByArtist;
  private final boolean allowExplicitSongsAtAllTimes;
  private final int allowExplicitSongsBegin;
  private final int allowExplicitSongsEnd;

  private String rootPath;
  private String rootPathWindows;
  private String rootPathUnix;
  private RootFolderEntity songLibraryRoot;
  private SongQueueRootEntity songQueueRoot;

  // ── Rule B State Tracking ────────────────────────────────────────────────
  /** Keeps track of recent historically played songs (oldest first, newest at the end) */
  private final List<SongFileEntity> songPlayHistory = new ArrayList<>();

  /** Reference to the track that is currently playing on the output system */
  private SongFileEntity currentlyPlayingSong;

  public SongQueueServiceImpl(String rootPath, String rootPathWindows, String rootPathUnix,
      SongQueueProperties songQueueProperties, SongLibraryService songLibraryService,
      BackgroundMusicService backgroundMusicService, SongQueueRepository songQueueRepository,
      ApplicationEventPublisher eventPublisher) {

    requireNonNull(rootPath, "rootPath cannot be null");
    requireNonNull(rootPathWindows, "rootPathWindows cannot be null");
    requireNonNull(rootPathUnix, "rootPathUnix cannot be null");
    requireNonNull(songQueueProperties, "songQueueProperties cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    requireNonNull(backgroundMusicService, "backgroundMusicService cannot be null");
    requireNonNull(songQueueRepository, "songQueueRepository cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");

    this.rootPath = rootPath;
    this.rootPathWindows = rootPathWindows;
    this.rootPathUnix = rootPathUnix;
    this.songLibraryService = songLibraryService;
    this.backgroundMusicService = backgroundMusicService;
    this.songQueueRepository = songQueueRepository;
    this.eventPublisher = eventPublisher;

    this.resetQueueAtStartup = songQueueProperties.isResetQueueAtStartup();
    this.minimumNumberSongsToKeepInQueue = songQueueProperties.getMinimumNumberSongsToKeepInQueue();

    this.minimumMinutesBetweenSongPlays = songQueueProperties.getMinimumMinutesBetweenSongPlays();
    this.maximumConsecutiveSongPlaysByArtist =
        songQueueProperties.getMaximumConsecutiveSongPlaysByArtist();
    this.allowExplicitSongsAtAllTimes = songQueueProperties.isAllowExplicitSongsAtAllTimes();
    this.allowExplicitSongsBegin = songQueueProperties.getAllowExplicitSongsBegin();
    this.allowExplicitSongsEnd = songQueueProperties.getAllowExplicitSongsEnd();

    initialize();
  }

  private synchronized void initialize() {

    /*
     * This method runs during bean construction (from the constructor), which happens while Spring
     * is still refreshing the application context — before LocalSecurityContextConfigurer installs
     * the EDT auth and before any HTTP request has run the JWT filter. Calls into secured services
     * (e.g. SongLibraryService.getGenreMusicByPopularity() for smart additions) would otherwise be
     * rejected by ServiceSecurityAspect, so install the SYSTEM principal for the duration of
     * startup initialization.
     */
    SecurityContext startupCtx = SecurityContextHolder.createEmptyContext();
    startupCtx.setAuthentication(SystemPrincipal.SystemAuthenticationToken.INSTANCE);
    SecurityContextHolder.setContext(startupCtx);
    try {
      initializeInternal();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void initializeInternal() {

    this.songLibraryRoot = this.songLibraryService.getSongLibraryRoot();

    if (resetQueueAtStartup) {

      this.songQueueRoot = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);

    } else {
      try {

        this.songQueueRoot =
            this.songQueueRepository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);

      } catch (EntityDoesNotExistException ednee) {

        log.error("Could not load song queue from: " + rootPath
            + ", using empty song library root for now, error: " + ednee.getMessage());

        this.songQueueRoot = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
      }
    }

    if (!this.rootPath.equals(this.songLibraryRoot.getRootPath())) {

      this.songLibraryRoot.setRootPath(this.rootPath);
      this.songLibraryRoot.initialize();
    }

    if (!this.rootPath.equals(this.songQueueRoot.getRootPath())) {

      this.songQueueRoot.setRootPath(this.rootPath);
      this.songQueueRoot.resetQueuedAtTime();
    }

    log.info("resetQueueAtStartup: " + this.resetQueueAtStartup);
    log.info("rootPath: " + this.rootPath);
    log.info("rootPathWindows: " + this.rootPathWindows);
    log.info("rootPathUnix: " + this.rootPathUnix);
    log.info("songLibraryRoot: " + this.songLibraryRoot.getRootPath());
    log.info("songQueueRoot: " + this.songQueueRoot.getRootPath());
    log.info("minimumNumberSongsToKeepInQueue: " + this.minimumNumberSongsToKeepInQueue);
    log.info("minimumMinutesBetweenSongPlays: " + this.minimumMinutesBetweenSongPlays);
    log.info("maximumConsecutiveSongPlaysByArtist: " + this.maximumConsecutiveSongPlaysByArtist);
    log.info("allowExplicitSongsAtAllTimes: " + this.allowExplicitSongsAtAllTimes);
    log.info("allowExplicitSongsBegin: " + this.allowExplicitSongsBegin);
    log.info("allowExplicitSongsEnd: " + this.allowExplicitSongsEnd);

    // Seed the queue with background music if it is below the minimum threshold.
    // This handles the cold-start case where there are no persisted songs in the queue,
    // so playback can begin immediately without waiting for dequeueNextSong() to be called first.
    // autoPopulateQueue() is a no-op if background music is disabled.
    autoPopulateQueue();
  }

  @Override
  public synchronized SongQueueEntryDto dequeueNextSong() {

    List<SongQueueEntryEntity> songs = songQueueRoot.getSongs();

    if (songs.isEmpty()) {

      eventPublisher.publishEvent(new SongQueueEmptyEvent());
      return null;
    }

    SongQueueEntryEntity nextSong = songs.getFirst();
    songQueueRoot.removeSongFromQueue(nextSong);
    songQueueRepository.storeAggregateRoot(songQueueRoot);

    // ── Rule B State Tracking ────────────────────────────────────────────────
    // Rotate the currently playing song into history before advancing to the next track.
    if (currentlyPlayingSong != null) {
      songPlayHistory.add(currentlyPlayingSong);
    }
    currentlyPlayingSong = nextSong.getSong();

    autoPopulateQueue();

    eventPublisher
        .publishEvent(new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));

    return SongQueueMapper.toDto(nextSong);
  }

  @Override
  public boolean isQueueEmpty() {
    return this.songQueueRoot.isQueueEmpty();
  }

  @Override
  public boolean isBackgroundMusicEnabled() {
    return this.backgroundMusicService.isEnabled();
  }

  /**
   * When background music is enabled, fills the queue up to {@code minimumNumberSongsToKeepInQueue}
   * by drawing songs from {@link BackgroundMusicService}. Each candidate is checked with
   * {@link #isSongEligibleForQueue}; ineligible songs are skipped. A hard cap of 50 attempts
   * prevents an infinite loop when the eligible pool is exhausted.
   *
   * <p>
   * This is the only method that interacts with {@link BackgroundMusicService}: it is called both
   * after a song is dequeued/removed (steady-state top-up) <em>and</em> during
   * {@link #initializeInternal()} so that the queue is seeded on startup even when no prior
   * persisted songs exist. It is a no-op when {@link BackgroundMusicService#isEnabled()} is
   * {@code false}.
   *
   * <p>
   * Must be called while holding {@code this} monitor (i.e. from a {@code synchronized} context).
   */
  private void autoPopulateQueue() {

    if (!backgroundMusicService.isEnabled()) {
      return;
    }

    // Determine whether smart additions are active right now.
    boolean smartActive = backgroundMusicService.isSmartAdditionsActive();
    int factor = backgroundMusicService.getSmartAdditionsFactor();

    int attempts = 0;
    while (songQueueRoot.getSongs().size() < minimumNumberSongsToKeepInQueue && attempts < 50) {

      attempts++;

      // ── Draw one core background-music song ──────────────────────────────
      SongFileEntity coreSong = backgroundMusicService.getNextSong();

      Integer coreAlbumId = coreSong.getAlbum().getPersistentIdentity();
      Integer coreSongId = coreSong.getPersistentIdentity();

      String coreIneligibility = isSongEligibleForQueue(coreAlbumId, coreSongId, 0);
      if (coreIneligibility == null) {
        addSongToQueue("BACKGROUND_MUSIC", coreAlbumId, coreSongId, 0);
      } else {
        log.debug("autoPopulateQueue: core BG song {} not eligible: {}", coreSong,
            coreIneligibility);
        // Do not count an ineligible core song against the smart-additions for this slot.
        continue;
      }

      // ── Interleave smart-addition songs if the window is active ──────────
      if (!smartActive) {
        continue;
      }

      for (int i = 0; i < factor; i++) {

        if (songQueueRoot.getSongs().size() >= minimumNumberSongsToKeepInQueue) {
          break;
        }

        SongFileEntity smartSong = backgroundMusicService.getNextSmartAdditionSong(coreSong);
        if (smartSong == null) {
          // No candidates available at all — skip remaining slots.
          log.debug(
              "autoPopulateQueue: no smart-addition candidates available for {}, skipping slot {}",
              coreSong, i);
          break;
        }

        Integer smartAlbumId = smartSong.getAlbum().getPersistentIdentity();
        Integer smartSongId = smartSong.getPersistentIdentity();

        String smartIneligibility = isSongEligibleForQueue(smartAlbumId, smartSongId, 0);
        if (smartIneligibility == null) {
          addSongToQueue("BACKGROUND_MUSIC_SMART", smartAlbumId, smartSongId, 0);
        } else {
          log.debug("autoPopulateQueue: smart-addition song {} not eligible: {}", smartSong,
              smartIneligibility);
        }
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
  public String isSongEligibleForQueue(Integer albumId, Integer songId, Integer priority) {

    try {

      Instant now = Instant.now();

      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album == null) {
        return "the song cannot be found";
      }

      SongFileEntity targetSong = album.getChildSong(songId);
      if (targetSong == null) {
        return "the song cannot be found";
      }


      // ─────────────────────────────────────────────────────────────────────
      // Rule A — minimum time between plays of the same song
      // ─────────────────────────────────────────────────────────────────────
      String targetSongName = targetSong.getSongName();
      String targetSongArtistName = targetSong.getArtistName();
      String targetAlbumArtistName = album.getParentArtist().getName();

      List<SongQueueEntryEntity> queuedSongs = songQueueRoot.getSongs();
      for (SongQueueEntryEntity queuedEntry : queuedSongs) {

        SongFileEntity queuedSong = queuedEntry.getSong();

        String queuedSongName = queuedSong.getSongName();
        String queuedSongArtistName = queuedSong.getArtistName();
        String queuedSongAlbumArtistName = queuedSong.getAlbum().getParentArtist().getName();

        boolean isSameSong = (targetSongName.equals(queuedSongName)
            && (targetSongArtistName.equals(queuedSongArtistName)
                || targetAlbumArtistName.equals(queuedSongAlbumArtistName)));

        if (isSameSong) {

          long minutesBetween = Duration.between(queuedEntry.getQueuedAtTime(), now).toMinutes();
          if (minutesBetween < minimumMinutesBetweenSongPlays) {

            long minutesRemaining = minimumMinutesBetweenSongPlays - minutesBetween;

            return "has already been played in the last " + minimumMinutesBetweenSongPlays
                + " min. Try again in " + minutesRemaining + " min";
          }
        }
      }


      // ─────────────────────────────────────────────────────────────────────
      // Rule B — maximum consecutive songs by the same artist
      // ─────────────────────────────────────────────────────────────────────
      // Build a full unified timeline view of execution state:
      List<SongFileEntity> fullTimeline = new ArrayList<>();

      // A. Seed from recent history (only look back as far as maximumConsecutiveSongPlaysByArtist)
      if (!songPlayHistory.isEmpty()) {
        int historySize = songPlayHistory.size();
        int lookbackCount = Math.min(historySize, maximumConsecutiveSongPlaysByArtist);
        for (int i = historySize - lookbackCount; i < historySize; i++) {
          fullTimeline.add(songPlayHistory.get(i));
        }
      }

      // B. Include the currently playing song
      if (currentlyPlayingSong != null) {
        fullTimeline.add(currentlyPlayingSong);
      }

      // C. Build a prioritized sandbox mirror of the queue to simulate placement
      SongQueueRootEntity mirrorSongQueueRoot =
          new SongQueueRootEntity(songQueueRoot.getRootPath());
      for (SongQueueEntryEntity existingEntry : songQueueRoot.getSongs()) {
        mirrorSongQueueRoot.getSongs().add(existingEntry);
      }

      // Simulate inserting the incoming candidate using the real prioritization logic
      mirrorSongQueueRoot.addSongToQueue("ELIGIBILITY_CHECK", targetSong, priority);

      // Append the sorted queue state onto our timeline
      for (SongQueueEntryEntity entry : mirrorSongQueueRoot.getSongs()) {
        fullTimeline.add(entry.getSong());
      }

      // Linear scan to ensure no cluster beats the consecutive limit
      int consecutiveCount = 0;
      String lastArtist = null;

      for (SongFileEntity song : fullTimeline) {
        String currentArtist = song.getArtistName();

        if (currentArtist.equals(lastArtist)) {
          consecutiveCount++;
        } else {
          consecutiveCount = 1;
          lastArtist = currentArtist;
        }

        if (consecutiveCount > maximumConsecutiveSongPlaysByArtist) {
          return "the consecutive play count for '" + currentArtist + "' has been exceeded";
        }
      }


      // ─────────────────────────────────────────────────────────────────────
      // Rule C — explicit-content time window
      // ─────────────────────────────────────────────────────────────────────
      if (!allowExplicitSongsAtAllTimes) {

        if (targetSong.hasExplicit()) {

          // Convert "now" into local wall-clock hour (0–23)
          int currentHour = now.atZone(ZoneId.systemDefault()).getHour();

          // The allowed window spans allowExplicitSongsBegin (inclusive) through
          // midnight and into allowExplicitSongsEnd (exclusive) the next morning.
          //
          // Example: begin=21, end=5
          // Allowed: 21:00–23:59 and 00:00–04:59
          // Blocked: 05:00–20:59
          //
          // When begin > end the window crosses midnight; when begin < end it is
          // entirely within one calendar day.

          boolean withinWindow;
          if (allowExplicitSongsBegin > allowExplicitSongsEnd) {
            // Crosses midnight: allowed if hour >= begin OR hour < end
            withinWindow =
                (currentHour >= allowExplicitSongsBegin) || (currentHour < allowExplicitSongsEnd);
          } else {
            // Same-day window: allowed if begin <= hour < end
            withinWindow =
                (currentHour >= allowExplicitSongsBegin) && (currentHour < allowExplicitSongsEnd);
          }

          if (!withinWindow) {

            String period = (allowExplicitSongsBegin >= 12) ? "PM" : "AM";
            int displayHour = allowExplicitSongsBegin % 12;
            if (displayHour == 0) {
              displayHour = 12;
            }

            return "you must wait until " + displayHour + ":00" + period
                + " to play songs with explicit lyrics";
          }
        }
      }
    } catch (Exception e) {
      throw new SongQueueServiceException("Unable to determine song queue eligibility for albumId: "
          + albumId + ", songId: " + songId + " and priority: " + priority);
    }

    return null;
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
      throw new SongQueueServiceException("Could not add album to queue: username: " + username
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

    Integer numSongsInQueue = -1;
    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {
        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {
          int preferredIndex = changeSongQueueRequest.getQueuePosition() != null
              ? changeSongQueueRequest.getQueuePosition()
              : -1;
          numSongsInQueue = songQueueRoot.moveSongUpInQueue(song, preferredIndex);
          if (numSongsInQueue.intValue() > 0) {
            songQueueRepository.storeAggregateRoot(songQueueRoot);
            eventPublisher.publishEvent(
                new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));
          }
        } else {
          throw new SongQueueServiceException("Could not add move song up in queue, albumId: "
              + albumId + ", songId: " + songId + ", error: song does not exist!");
        }
      } else {
        throw new SongQueueServiceException("Could not add move song up in queue, albumId: "
            + albumId + ", songId: " + songId + ", error: album does not exist!");
      }
      return numSongsInQueue;
    } catch (EntityDoesNotExistException e) {
      throw new SongQueueServiceException("Could not add move song up in queue, albumId: " + albumId
          + ", songId: " + songId + ", error: " + e.getMessage(), e);
    }
  }

  @Override
  public Integer moveSongDownInQueue(ChangeSongQueueRequest changeSongQueueRequest) {
    int albumId = changeSongQueueRequest.getAlbumId();
    int songId = changeSongQueueRequest.getSongId();

    Integer numSongsInQueue = -1;
    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {
        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {
          int preferredIndex = changeSongQueueRequest.getQueuePosition() != null
              ? changeSongQueueRequest.getQueuePosition()
              : -1;
          numSongsInQueue = songQueueRoot.moveSongDownInQueue(song, preferredIndex);
          if (numSongsInQueue.intValue() > 0) {
            songQueueRepository.storeAggregateRoot(songQueueRoot);
            eventPublisher.publishEvent(
                new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));
          }
        } else {
          throw new SongQueueServiceException("Could not add move song down in queue, albumId: "
              + albumId + ", songId: " + songId + ", error: song does not exist!");
        }
      } else {
        throw new SongQueueServiceException("Could not add move song down in queue, albumId: "
            + albumId + ", songId: " + songId + ", error: album does not exist!");
      }
      return numSongsInQueue;
    } catch (EntityDoesNotExistException e) {
      throw new SongQueueServiceException("Could not add move song down in queue, albumId: "
          + albumId + ", songId: " + songId + ", error: " + e.getMessage(), e);
    }
  }

  @Override
  public synchronized Integer removeSongDownFromQueue(
      ChangeSongQueueRequest changeSongQueueRequest) {
    int albumId = changeSongQueueRequest.getAlbumId();
    int songId = changeSongQueueRequest.getSongId();

    Integer numSongsRemoved = 0;
    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {
        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {
          numSongsRemoved = songQueueRoot.removeSongFromQueue(song);
          if (numSongsRemoved.intValue() > 0) {
            songQueueRepository.storeAggregateRoot(songQueueRoot);

            // Top the queue back up to minimumNumberSongsToKeepInQueue — the
            // same top-up that dequeueNextSong() performs — so a patron
            // manually removing songs never drains the queue below the minimum.
            autoPopulateQueue();

            eventPublisher.publishEvent(
                new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));
          }
        } else {
          throw new SongQueueServiceException("Could not remove song down in queue, albumId: "
              + albumId + ", songId: " + songId + ", error: song does not exist!");
        }
      } else {
        throw new SongQueueServiceException("Could not remove song down in queue, albumId: "
            + albumId + ", songId: " + songId + ", error: album does not exist!");
      }
      return numSongsRemoved;
    } catch (EntityDoesNotExistException e) {
      throw new SongQueueServiceException("Could not remove song down in queue, albumId: " + albumId
          + ", songId: " + songId + ", error: " + e.getMessage(), e);
    }
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
      throw new SongQueueServiceException("Could not save queue as playlist: " + filename, e);
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
      throw new SongQueueServiceException(
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
    } catch (EntityDoesNotExistException ednee) {
      throw new SongQueueServiceException("Could not add song to queue, albumId: " + albumId
          + ", songId: " + songId + ", priority: " + priority, ednee);
    }
    throw new SongQueueServiceException("Could not add song to queue, albumId: " + albumId
        + ", songId: " + songId + ", priority: " + priority);
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
    throw new SongLibraryServiceException("Not implemented yet!");
  }

  @Override
  public QueryResponse<QueryRequest, QueryResponseItem> processQuery(QueryRequest queryRequest) {
    throw new SongLibraryServiceException("Not implemented yet!");
  }

  @EventListener
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {

    log.info("""
        Received ScanFileSystemForSongsEvent:
        scanPath={}
        albumCount={}
        """, event.scanPath(), event.albumCount());

    this.rootPath = event.scanPath();

    // SongLibraryServiceImpl has already re-initialized RootFolderEntity in response
    // to this same event; grab the new shared instance rather than loading from disk again.
    this.songLibraryRoot = this.songLibraryService.getSongLibraryRoot();
  }
}
