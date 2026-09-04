package com.djt.jukeanator_engine.domain.songqueue.service;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.backgroundmusic.exception.BackgroundMusicServiceException;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.BackgroundMusicService;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.service.SlaveCommandGateway;
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
import com.fasterxml.jackson.core.type.TypeReference;

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

  // Present only on master (see LocationConfig) -- absent everywhere else, since standalone/slave
  // never needs to forward anything (isOwnLocation is always true there).
  private final Optional<SlaveCommandGateway> slaveCommandGateway;

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

  private RootFolderEntity songLibraryRoot;
  private SongQueueRootEntity songQueueRoot;

  // ── Rule B State Tracking ────────────────────────────────────────────────
  /** Keeps track of recent historically played songs (oldest first, newest at the end) */
  private final List<SongFileEntity> songPlayHistory = new ArrayList<>();

  /** Reference to the track that is currently playing on the output system */
  private SongFileEntity currentlyPlayingSong;

  // ── Song Queue Lock State ────────────────────────────────────────────────
  /**
   * When {@code true}, {@link #dequeueNextSong()} will not dequeue/play any song -- songs may
   * still be queued via {@link #addSongToQueue}/{@link #addAlbumToQueue}/
   * {@link #addMultipleSongsToQueue}. Toggled via {@link #lock()}/{@link #unlock()}.
   */
  private volatile boolean isSongQueueLocked = false;

  public SongQueueServiceImpl(SongQueueProperties songQueueProperties,
      SongLibraryService songLibraryService, BackgroundMusicService backgroundMusicService,
      SongQueueRepository songQueueRepository, ApplicationEventPublisher eventPublisher,
      Optional<SlaveCommandGateway> slaveCommandGateway) {

    requireNonNull(songQueueProperties, "songQueueProperties cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    requireNonNull(backgroundMusicService, "backgroundMusicService cannot be null");
    requireNonNull(songQueueRepository, "songQueueRepository cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");
    requireNonNull(slaveCommandGateway, "slaveCommandGateway cannot be null (use Optional.empty())");

    this.songLibraryService = songLibraryService;
    this.backgroundMusicService = backgroundMusicService;
    this.songQueueRepository = songQueueRepository;
    this.eventPublisher = eventPublisher;
    this.slaveCommandGateway = slaveCommandGateway;

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

  /**
   * True when {@code locationId} is this instance's own location -- always true on
   * standalone/slave (which have exactly one), and true on master only for a location it does not
   * actually own (never, since master owns none), so this is always false there.
   */
  private boolean isOwnLocation(Integer locationId) {
    return Objects.equals(locationId, songLibraryService.getOwnLocationId());
  }

  private SlaveCommandGateway requireGateway(Integer locationId) {
    return slaveCommandGateway.orElseThrow(() -> new SongQueueServiceException(
        "Cannot forward a song-queue request for locationId: [" + locationId
            + "] -- no SlaveCommandGateway is available (this instance is not running in master mode)."));
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

    this.songLibraryRoot = this.songLibraryService.getSongLibraryRoot(this.songLibraryService.getOwnLocationId());

    if (resetQueueAtStartup) {

      this.songQueueRoot = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);

    } else {
      try {

        this.songQueueRoot =
            this.songQueueRepository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);

      } catch (EntityDoesNotExistException ednee) {

        log.error("Could not load song queue from dataDir, using empty song library root for now, "
            + "error: " + ednee.getMessage());

        this.songQueueRoot = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
      }
    }

    log.info("resetQueueAtStartup: " + this.resetQueueAtStartup);
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

    if (isSongQueueLocked) {
      return null;
    }

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
      SongFileEntity coreSong;
      try {
        coreSong = backgroundMusicService.getNextSong();
      } catch (BackgroundMusicServiceException bmse) {
        // No eligible background-music candidate could be resolved against the song library
        // (e.g. the library is empty/stale). Stop trying rather than let this fail queue
        // seeding — and, during initializeInternal(), application startup itself.
        log.warn("autoPopulateQueue: could not get next background music song, stopping: {}",
            bmse.getMessage());
        break;
      }

      Integer coreAlbumId = coreSong.getAlbum().getId();
      Integer coreSongId = coreSong.getId();

      String coreIneligibility =
          isSongEligibleForQueue(songLibraryService.getOwnLocationId(), coreAlbumId, coreSongId, 0);
      if (coreIneligibility == null) {
        addSongToQueue("BG_MUSIC", coreAlbumId, coreSongId, 0);
        backgroundMusicService.markSongQueued(coreSong);
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

        Integer smartAlbumId = smartSong.getAlbum().getId();
        Integer smartSongId = smartSong.getId();

        String smartIneligibility = isSongEligibleForQueue(songLibraryService.getOwnLocationId(),
            smartAlbumId, smartSongId, 0);
        if (smartIneligibility == null) {
          addSongToQueue("SMART_BG_MUSIC", smartAlbumId, smartSongId, 0);
          backgroundMusicService.markSongQueued(smartSong);
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
  public Integer getHighestPriority(Integer locationId) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "getHighestPriority", null,
          Integer.class);
    }
    List<SongQueueEntryEntity> songs = songQueueRoot.getSongs();
    if (songs.isEmpty()) {
      return Integer.valueOf(2);
    }
    return Integer.valueOf(songs.getFirst().getPriority().intValue() + 1);
  }

  @Override
  public List<SongQueueEntryDto> getQueuedSongs(Integer locationId) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "getQueuedSongs", null,
          new TypeReference<List<SongQueueEntryDto>>() {});
    }
    return SongQueueMapper.toDto(songQueueRoot.getSongs());
  }

  @Override
  public String isSongEligibleForQueue(Integer locationId, Integer albumId, Integer songId,
      Integer priority) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "isSongEligibleForQueue",
          new EligibilityCheckPayload(albumId, songId, priority), String.class);
    }

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
  public SongQueueEntryDto addSongToQueue(Integer locationId,
      AddSongToQueueRequest addSongToQueueRequest) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "addSongToQueue",
          addSongToQueueRequest, SongQueueEntryDto.class);
    }

    SongQueueEntryDto queueEntryDto;
    synchronized (this) {
      queueEntryDto =
          addSongToQueue(addSongToQueueRequest.username(), addSongToQueueRequest.albumId(),
              addSongToQueueRequest.songId(), addSongToQueueRequest.priority());
    }

    eventPublisher.publishEvent(
        new SongAddedToQueueEvent(queueEntryDto, addSongToQueueRequest.priorityPlay()));
    eventPublisher.publishEvent(new SongQueueChangedEvent(getQueuedSongs(locationId)));

    return queueEntryDto;
  }

  @Override
  public List<SongQueueEntryDto> addAlbumToQueue(Integer locationId,
      AddAlbumToQueueRequest addAlbumToQueueRequest) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "addAlbumToQueue",
          addAlbumToQueueRequest, new TypeReference<List<SongQueueEntryDto>>() {});
    }
    if (addAlbumToQueueRequest == null) {
      return List.of();
    }

    String username = addAlbumToQueueRequest.username();
    Integer albumId = addAlbumToQueueRequest.albumId();
    Integer priority = addAlbumToQueueRequest.priority();

    List<SongIdentifier> songIdentifiers = new ArrayList<>();
    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {
        for (SongFileEntity song : album.getChildSongs()) {
          songIdentifiers.add(
              new SongIdentifier(locationId, albumId, song.getId()));
        }
      }
    } catch (EntityDoesNotExistException e) {
      throw new SongQueueServiceException("Could not add album to queue: username: " + username
          + ", albumId: " + albumId + ", priority: " + priority);
    }

    return addMultipleSongsToQueue(locationId,
        new AddMultipleSongsToQueueRequest(username, songIdentifiers, priority));
  }

  @Override
  public List<SongQueueEntryDto> addMultipleSongsToQueue(Integer locationId,
      AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "addMultipleSongsToQueue",
          addMultipleSongsToQueueRequest, new TypeReference<List<SongQueueEntryDto>>() {});
    }

    if (addMultipleSongsToQueueRequest == null
        || addMultipleSongsToQueueRequest.songIdentifiers().isEmpty()) {
      return List.of();
    }

    List<SongQueueEntryDto> queueEntries = new ArrayList<>();

    for (SongIdentifier songIdentifier : addMultipleSongsToQueueRequest.songIdentifiers()) {
      queueEntries.add(
          addSongToQueue(addMultipleSongsToQueueRequest.username(), songIdentifier.getAlbumId(),
              songIdentifier.getSongId(), addMultipleSongsToQueueRequest.priority()));
    }

    eventPublisher.publishEvent(new MultipleSongsAddedToQueueEvent(queueEntries));
    eventPublisher.publishEvent(new SongQueueChangedEvent(getQueuedSongs(locationId)));

    return queueEntries;
  }

  @Override
  public synchronized Integer flushQueue(Integer locationId) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "flushQueue", null, Integer.class);
    }
    Integer numSongsFlushed = songQueueRoot.flushQueue();
    songQueueRepository.storeAggregateRoot(songQueueRoot);

    // Top the queue back up to minimumNumberSongsToKeepInQueue — the same top-up
    // that dequeueNextSong()/removeSongDownFromQueue() perform — so a flush never
    // leaves the queue empty when background music is enabled.
    autoPopulateQueue();

    eventPublisher
        .publishEvent(new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));
    return numSongsFlushed;
  }

  @Override
  public Integer randomizeQueue(Integer locationId) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "randomizeQueue", null,
          Integer.class);
    }
    Integer numSongsRandomized = songQueueRoot.randomizeQueue();
    songQueueRepository.storeAggregateRoot(songQueueRoot);
    eventPublisher
        .publishEvent(new SongQueueChangedEvent(SongQueueMapper.toDto(songQueueRoot.getSongs())));
    return numSongsRandomized;
  }

  @Override
  public Integer moveSongUpInQueue(Integer locationId,
      ChangeSongQueueRequest changeSongQueueRequest) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "moveSongUpInQueue",
          changeSongQueueRequest, Integer.class);
    }
    int albumId = changeSongQueueRequest.albumId();
    int songId = changeSongQueueRequest.songId();

    Integer numSongsInQueue = -1;
    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {
        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {
          int preferredIndex = changeSongQueueRequest.queuePosition() != null
              ? changeSongQueueRequest.queuePosition()
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
  public Integer moveSongDownInQueue(Integer locationId,
      ChangeSongQueueRequest changeSongQueueRequest) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "moveSongDownInQueue",
          changeSongQueueRequest, Integer.class);
    }
    int albumId = changeSongQueueRequest.albumId();
    int songId = changeSongQueueRequest.songId();

    Integer numSongsInQueue = -1;
    try {
      AlbumFolderEntity album = songLibraryRoot.getAlbumById(albumId);
      if (album != null) {
        SongFileEntity song = album.getChildSong(songId);
        if (song != null) {
          int preferredIndex = changeSongQueueRequest.queuePosition() != null
              ? changeSongQueueRequest.queuePosition()
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
  public synchronized Integer removeSongDownFromQueue(Integer locationId,
      ChangeSongQueueRequest changeSongQueueRequest) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "removeSongDownFromQueue",
          changeSongQueueRequest, Integer.class);
    }
    int albumId = changeSongQueueRequest.albumId();
    int songId = changeSongQueueRequest.songId();

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
  public void lock() {
    isSongQueueLocked = true;
  }

  @Override
  public void unlock() {
    isSongQueueLocked = false;
  }

  @Override
  public boolean isLocked() {
    return isSongQueueLocked;
  }

  @Override
  public Integer saveQueueAsPlaylist(Integer locationId, String filename) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "saveQueueAsPlaylist", filename,
          Integer.class);
    }
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
  public Integer loadPlaylistIntoQueue(Integer locationId,
      LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest) {
    if (!isOwnLocation(locationId)) {
      return requireGateway(locationId).sendCommand(locationId, "loadPlaylistIntoQueue",
          loadPlaylistIntoQueueRequest, Integer.class);
    }
    String username = loadPlaylistIntoQueueRequest.username();
    String filename = loadPlaylistIntoQueueRequest.filename();

    try {
      List<SongIdentifier> songIdentifiers = new ArrayList<>();
      Integer priority = 0;

      for (String songPathname : PlaylistManager.loadPlayList(new File(filename))) {
        SongFileEntity song = this.songLibraryRoot.getSongByPath(songPathname);
        songIdentifiers.add(new SongIdentifier(locationId, song.getAlbum().getId(),
            song.getId()));
      }

      AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest =
          new AddMultipleSongsToQueueRequest(username, songIdentifiers, priority);

      addMultipleSongsToQueue(locationId, addMultipleSongsToQueueRequest);
      return Integer.valueOf(songIdentifiers.size());
    } catch (Exception e) {
      throw new SongQueueServiceException(
          "Could not load playlist into queue: username: " + username + ", filename: " + filename,
          e);
    }
  }

  @Override
  public synchronized Integer storeSongQueue() {
    songQueueRepository.storeAggregateRoot(songQueueRoot);
    return Integer.valueOf(songQueueRoot.getSongs().size());
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
  public Integer getOwnLocationId() {
    return songLibraryService.getOwnLocationId();
  }

  private record EligibilityCheckPayload(Integer albumId, Integer songId, Integer priority) {}

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
        songCount={}
        """, event.scanPath(), event.albumCount(), event.songCount());

    // SongLibraryServiceImpl has already re-initialized RootFolderEntity in response
    // to this same event; grab the new shared instance rather than loading from disk again.
    this.songLibraryRoot = this.songLibraryService.getSongLibraryRoot(this.songLibraryService.getOwnLocationId());
  }
}
