package com.djt.jukeanator_engine.domain.backgroundmusic.service;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.backgroundmusic.config.BackgroundMusicProperties;
import com.djt.jukeanator_engine.domain.backgroundmusic.exception.BackgroundMusicServiceException;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartAdditionReason;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.BackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.SmartBackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.utils.BackgroundMusicHelper;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueChangedEvent;

/**
 * @author tmyers
 */
public class BackgroundMusicServiceImpl implements BackgroundMusicService {

  private static final Logger log = LoggerFactory.getLogger(BackgroundMusicServiceImpl.class);

  private final SongLibraryService songLibraryService;
  private final BackgroundMusicRepository backgroundMusicRepository;
  private final SmartBackgroundMusicRepository smartBackgroundMusicRepository;
  private final BackgroundMusicHelper backgroundMusicHelper = new BackgroundMusicHelper();

  private String rootPath;
  private final String rootPathWindows;
  private final String rootPathUnix;

  private boolean enableBackgroundMusic;
  private final boolean enableSmartBackgroundMusicAdditions;
  private final int smartBackgroundMusicAdditionsFactor;
  private final int smartBackgroundMusicAdditionsBegin;
  private final int smartBackgroundMusicAdditionsEnd;
  private final int smartBackgroundMusicMinPlays;
  private final double smartBackgroundMusicFavoriteAlbumsPercentage;

  // ── Core background-music in-memory cache (Item 5) ────────────────────────
  private List<BackgroundMusicSongEntity> allSongs = new ArrayList<>();
  private Map<Integer, BackgroundMusicSongEntity> songsById = new HashMap<>();
  private Map<String, Integer> normalizedPathToId = new HashMap<>();
  private List<Integer> notPlayedIds = new ArrayList<>();
  private Set<String> currentPlaylistPaths = new HashSet<>();

  // ── Genres excluded from smart-addition candidate selection ───────────────
  private Set<String> excludedGenres = new HashSet<>();

  // ── Favorite albums (forward-slash-normalized path suffixes, see #isFavoriteAlbum) whose songs
  // are interleaved into the smart-addition pool with no source song — see
  // SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM.
  private Set<String> favoriteAlbumPaths = new HashSet<>();

  // ── Smart-additions in-memory cache (disposable per-cycle pool) ───────────
  private List<SmartBackgroundMusicSongEntity> smartPool = new ArrayList<>();
  private Map<Integer, SmartBackgroundMusicSongEntity> smartSongsById = new HashMap<>();
  private Map<String, Integer> smartNormalizedPathToId = new HashMap<>();
  private List<Integer> smartNotPlayedIds = new ArrayList<>();

  // ── Live song-queue contents (normalized paths), kept in sync via
  // SongQueueChangedEvent — used to avoid picking a song that's already queued but hasn't
  // played yet. ─────────────────────────────────────────────────────────────
  private Set<String> currentlyQueuedPaths = new HashSet<>();

  public BackgroundMusicServiceImpl(String rootPath, String rootPathWindows, String rootPathUnix,
      BackgroundMusicProperties backgroundMusicProperties, SongLibraryService songLibraryService,
      BackgroundMusicRepository backgroundMusicRepository,
      SmartBackgroundMusicRepository smartBackgroundMusicRepository) {

    requireNonNull(rootPath, "rootPath cannot be null");
    requireNonNull(rootPathWindows, "rootPathWindows cannot be null");
    requireNonNull(rootPathUnix, "rootPathUnix cannot be null");
    requireNonNull(backgroundMusicProperties, "backgroundMusicProperties cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    requireNonNull(backgroundMusicRepository, "backgroundMusicRepository cannot be null");
    requireNonNull(smartBackgroundMusicRepository, "smartBackgroundMusicRepository cannot be null");

    this.rootPath = rootPath;
    this.rootPathWindows = rootPathWindows;
    this.rootPathUnix = rootPathUnix;
    this.songLibraryService = songLibraryService;
    this.backgroundMusicRepository = backgroundMusicRepository;
    this.smartBackgroundMusicRepository = smartBackgroundMusicRepository;

    this.enableBackgroundMusic = backgroundMusicProperties.isEnableBackgroundMusic();
    this.enableSmartBackgroundMusicAdditions =
        backgroundMusicProperties.isEnableSmartBackgroundMusicAdditions();
    this.smartBackgroundMusicAdditionsFactor =
        backgroundMusicProperties.getSmartBackgroundMusicAdditionsFactor();
    this.smartBackgroundMusicAdditionsBegin =
        backgroundMusicProperties.getSmartBackgroundMusicAdditionsBegin();
    this.smartBackgroundMusicAdditionsEnd =
        backgroundMusicProperties.getSmartBackgroundMusicAdditionsEnd();
    this.smartBackgroundMusicMinPlays = backgroundMusicProperties.getSmartBackgroundMusicMinPlays();
    this.smartBackgroundMusicFavoriteAlbumsPercentage =
        backgroundMusicProperties.getSmartBackgroundMusicFavoriteAlbumsPercentage();

    initialize();
  }

  /**
   * Runs during bean construction (from the constructor), which happens while Spring is still
   * refreshing the application context — before {@code LocalSecurityContextConfigurer} installs
   * the EDT auth and before any HTTP request has run the JWT filter. {@link #loadAndReconcile()}
   * may call into secured services (e.g. {@code SongLibraryService.getGenreMusicByPopularity()}
   * for smart additions), which {@code ServiceSecurityAspect} would otherwise reject, so install
   * the SYSTEM principal for the duration of startup initialization — mirrors
   * {@code SongQueueServiceImpl#initialize()}.
   */
  private void initialize() {

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

    log.info("enableBackgroundMusic: " + this.enableBackgroundMusic);
    log.info("enableSmartBackgroundMusicAdditions: " + this.enableSmartBackgroundMusicAdditions);
    log.info("smartBackgroundMusicAdditionsFactor: " + this.smartBackgroundMusicAdditionsFactor);
    log.info("smartBackgroundMusicAdditionsBegin: " + this.smartBackgroundMusicAdditionsBegin);
    log.info("smartBackgroundMusicAdditionsEnd: " + this.smartBackgroundMusicAdditionsEnd);
    log.info("smartBackgroundMusicMinPlays: " + this.smartBackgroundMusicMinPlays);
    log.info("smartBackgroundMusicFavoriteAlbumsPercentage: "
        + this.smartBackgroundMusicFavoriteAlbumsPercentage);

    if (!this.enableBackgroundMusic) {
      return;
    }

    // Try first to load the playlist from BackgroundMusic.TXT and reconcile it against the
    // persisted repository.
    try {

      loadAndReconcile();

    } catch (Exception e1) {

      // NOTE: If unable to load BackgroundMusic.TXT, then fall back to using the most popular
      // songs. The retry below will self-heal the repository from the freshly-created file.
      log.error("Unable to initialize background music playlist, error: " + e1.getMessage());

      try {

        createBackgroundMusicFromTopSongs();
        loadAndReconcile();

      } catch (Exception e) {

        // NOTE: If unable to create a playlist from the most popular songs, disable the feature.
        log.error(
            "Unable to auto-populate background music playlist with top songs, error: "
                + e.getMessage());

        this.enableBackgroundMusic = false;
      }
    }
  }

  /**
   * Reads the canonical playlist from {@code BackgroundMusic.TXT}, loads the persisted
   * {@link BackgroundMusicSongEntity} collection, and reconciles the two: any playlist path not
   * yet known becomes a new entity (persisted with {@code timeLastPlayed=null}). Entities for
   * paths no longer present in the playlist are left untouched in the repository, but excluded
   * from the not-played selection cache. Also (re)loads the smart-addition genre exclusions list.
   *
   * <p>
   * If {@code SmartBackgroundMusicSongs.json} does not exist yet (no prior smart-additions run),
   * the smart pool is fully (re)generated from every source song currently in
   * {@code BackgroundMusic.TXT} — see {@link #refreshSmartAdditionPool()} — so the whole smart
   * list can be inspected up front rather than trickling in lazily.
   */
  private void loadAndReconcile() throws IOException {

    List<String> playlistPaths = backgroundMusicHelper.readBackgroundMusicPlaylist(this.rootPath);
    this.currentPlaylistPaths = new HashSet<>(playlistPaths);

    List<String> genreExclusions =
        backgroundMusicHelper.readSmartBackgroundMusicGenreExclusions(this.rootPath);
    this.excludedGenres = genreExclusions.stream()
        .map(String::toLowerCase)
        .collect(Collectors.toCollection(HashSet::new));

    List<String> albumInclusions =
        backgroundMusicHelper.readSmartBackgroundMusicAlbumInclusions(this.rootPath);
    this.favoriteAlbumPaths = albumInclusions.stream()
        .map(BackgroundMusicServiceImpl::normalizeAlbumInclusionPath)
        .collect(Collectors.toCollection(HashSet::new));

    this.allSongs = new ArrayList<>(backgroundMusicRepository.loadAll());
    rebuildSongsById();

    Set<String> knownPaths = new HashSet<>();
    for (BackgroundMusicSongEntity song : allSongs) {
      knownPaths.add(song.getSongFilePath());
    }

    boolean changed = false;
    for (String path : playlistPaths) {
      if (!knownPaths.contains(path)) {
        allSongs.add(new BackgroundMusicSongEntity(null, path));
        knownPaths.add(path);
        changed = true;
      }
    }

    if (changed) {
      backgroundMusicRepository.storeAll(allSongs);
      rebuildSongsById();
    }

    rebuildNotPlayedCache();

    boolean smartPoolFileExists = smartBackgroundMusicRepository.exists();
    this.smartPool = new ArrayList<>(smartBackgroundMusicRepository.loadAll());
    rebuildSmartCaches();

    if (!smartPoolFileExists && enableSmartBackgroundMusicAdditions) {
      refreshSmartAdditionPool();
    }
  }

  private void rebuildSongsById() {

    this.songsById = new HashMap<>();
    this.normalizedPathToId = new HashMap<>();
    for (BackgroundMusicSongEntity song : allSongs) {
      songsById.put(song.getPersistentIdentity(), song);
      normalizedPathToId.put(normalizedPath(song), song.getPersistentIdentity());
    }
  }

  private String normalizedPath(BackgroundMusicSongEntity song) {
    return backgroundMusicHelper.normalizePathForCurrentOS(song.getSongFilePath(),
        this.rootPathWindows, this.rootPathUnix);
  }

  private void rebuildNotPlayedCache() {

    this.notPlayedIds = allSongs.stream()
        .filter(BackgroundMusicSongEntity::isNotYetPlayed)
        .filter(song -> currentPlaylistPaths.contains(song.getSongFilePath()))
        .map(BackgroundMusicSongEntity::getPersistentIdentity)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private void createBackgroundMusicFromTopSongs() throws IOException {

    RootFolderEntity songLibraryRoot = this.songLibraryService.getSongLibraryRoot();

    List<SongFileEntity> songs = new ArrayList<>(songLibraryRoot.getSongs());
    songs.sort((s1, s2) -> Integer.compare(s2.getNumPlays() == null ? 0 : s2.getNumPlays(),
        s1.getNumPlays() == null ? 0 : s1.getNumPlays()));

    List<String> topSongPathNames = new ArrayList<>();
    for (SongFileEntity song : songs) {

      topSongPathNames.add(song.getNaturalIdentity());

      if (topSongPathNames.size() >= 500) {
        break;
      }
    }

    backgroundMusicHelper.createBackgroundMusicFromTopSongs(this.rootPath, topSongPathNames);
  }

  @Override
  public boolean isEnabled() {
    return this.enableBackgroundMusic;
  }

  private Integer pickRandom(List<Integer> ids) {

    if (ids.size() == 1) {
      return ids.get(0);
    }
    int index = ThreadLocalRandom.current().nextInt(ids.size());
    return ids.get(index);
  }

  /**
   * NOTE: Selection is read-only — it does <b>not</b> mark the chosen song as played or remove
   * it from the not-played pool. A song is only ever marked played (via
   * {@link #handleSongPlaybackStartedEvent}) once it actually starts playing, since there is no
   * guarantee a queued song will ever be played (e.g. the queue is flushed, the jukebox goes into
   * hibernation, or the application restarts before its turn comes up).
   */
  @Override
  public SongFileEntity getNextSong() {

    try {

      Integer chosenId = pickNextEligibleBackgroundId();
      BackgroundMusicSongEntity chosen = songsById.get(chosenId);

      return this.songLibraryService.getSongLibraryRoot().getSongByPath(normalizedPath(chosen));

    } catch (Exception e) {
      throw new BackgroundMusicServiceException(
          "Cannot get next background music song, error: " + e.getMessage(), e);
    }
  }

  /**
   * Picks a persistent identity from the not-played pool, preferring candidates that are not
   * already sitting in the song queue (so the same song is never queued twice while waiting for
   * its turn to actually play). Falls back to the full not-played pool if every remaining
   * candidate happens to already be queued.
   *
   * <p>
   * When the pool is empty — i.e. every song has actually been played this cycle — resets every
   * song's {@code timeLastPlayed} back to {@code null} and persists that reset immediately, so it
   * survives a restart even if none of the reset songs happen to be replayed before then. Since
   * song popularity may have shifted over a full cycle, the smart-additions pool is refreshed at
   * the same time — see {@link #refreshSmartAdditionPool()}.
   */
  private Integer pickNextEligibleBackgroundId() {

    if (notPlayedIds.isEmpty()) {
      for (BackgroundMusicSongEntity song : allSongs) {
        song.setTimeLastPlayed(null);
      }
      backgroundMusicRepository.storeAll(allSongs);
      rebuildNotPlayedCache();

      if (enableSmartBackgroundMusicAdditions) {
        refreshSmartAdditionPool();
      }
    }

    if (notPlayedIds.isEmpty()) {
      throw new BackgroundMusicServiceException("No background music songs available");
    }

    List<Integer> eligible = notPlayedIds.stream()
        .filter(id -> !isCurrentlyQueued(songsById.get(id)))
        .collect(Collectors.toList());

    return pickRandom(eligible.isEmpty() ? notPlayedIds : eligible);
  }

  private boolean isCurrentlyQueued(BackgroundMusicSongEntity song) {
    return song != null && currentlyQueuedPaths.contains(normalizedPath(song));
  }

  /**
   * Returns {@code true} when the current wall-clock hour falls inside the smart-additions time
   * window defined by {@code smartBackgroundMusicAdditionsBegin} and
   * {@code smartBackgroundMusicAdditionsEnd}.
   *
   * <p>
   * A begin value greater than end indicates a window that crosses midnight (e.g. begin=22, end=2
   * covers 22:00–01:59).
   */
  private boolean isWithinSmartAdditionsWindow() {

    int currentHour = Instant.now().atZone(ZoneId.systemDefault()).getHour();

    if (smartBackgroundMusicAdditionsBegin > smartBackgroundMusicAdditionsEnd) {
      // Crosses midnight: active if hour >= begin OR hour < end
      return (currentHour >= smartBackgroundMusicAdditionsBegin)
          || (currentHour < smartBackgroundMusicAdditionsEnd);
    } else {
      // Same-day window: active if begin <= hour < end
      return (currentHour >= smartBackgroundMusicAdditionsBegin)
          && (currentHour < smartBackgroundMusicAdditionsEnd);
    }
  }

  @Override
  public boolean isSmartAdditionsActive() {
    return enableSmartBackgroundMusicAdditions && isWithinSmartAdditionsWindow();
  }

  @Override
  public int getSmartAdditionsFactor() {
    return Math.max(1, Math.min(10, smartBackgroundMusicAdditionsFactor));
  }

  /**
   * NOTE: Selection is read-only — see {@link #getNextSong()} for why a song is only marked
   * played once it is actually confirmed via {@link #handleSongPlaybackStartedEvent}.
   *
   * <p>
   * Selection draws from the whole smart-additions pool (built up-front from every source song in
   * {@code BackgroundMusic.TXT} — see {@link #refreshSmartAdditionPool()}), not just candidates
   * seeded from {@code coreSong}. When every smart song has been played this cycle, their
   * {@code timeLastPlayed} is simply reset (mirroring {@link #pickNextEligibleBackgroundId()}) —
   * a full recompute only happens when the core background-music list itself cycles.
   */
  @Override
  public SongFileEntity getNextSmartAdditionSong(SongFileEntity coreSong) {

    try {

      if (smartNotPlayedIds.isEmpty() && !smartPool.isEmpty()) {
        for (SmartBackgroundMusicSongEntity song : smartPool) {
          song.setTimeLastPlayed(null);
        }
        smartBackgroundMusicRepository.storeAll(smartPool);
        rebuildSmartCaches();
      }

      List<Integer> eligible = smartNotPlayedIds.stream()
          .filter(id -> !isCurrentlyQueued(smartSongsById.get(id)))
          .collect(Collectors.toList());

      if (eligible.isEmpty()) {
        // Pool exhausted/empty, or every remaining candidate is already queued; caller should
        // treat this as a miss and fall back to the core playlist.
        return null;
      }

      Integer chosenId = pickRandom(eligible);
      SmartBackgroundMusicSongEntity chosen = smartSongsById.get(chosenId);

      String normalizedPath = normalizedPath(chosen);

      try {
        return this.songLibraryService.getSongLibraryRoot().getSongByPath(normalizedPath);
      } catch (EntityDoesNotExistException ednee) {
        // Path not found in library — fail gracefully so the caller can fall back.
        log.warn("getNextSmartAdditionSong: could not find song for path: {}", normalizedPath);
        return null;
      }

    } catch (Exception e) {
      log.warn("getNextSmartAdditionSong: failed for core song {}: {}", coreSong, e.getMessage());
      return null;
    }
  }

  private void rebuildSmartCaches() {

    this.smartSongsById = new HashMap<>();
    this.smartNormalizedPathToId = new HashMap<>();
    for (SmartBackgroundMusicSongEntity song : smartPool) {
      smartSongsById.put(song.getPersistentIdentity(), song);
      smartNormalizedPathToId.put(normalizedPath(song), song.getPersistentIdentity());
    }
    this.smartNotPlayedIds = smartPool.stream()
        .filter(SmartBackgroundMusicSongEntity::isNotYetPlayed)
        .map(SmartBackgroundMusicSongEntity::getPersistentIdentity)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * (Re)builds the entire smart-additions pool from scratch, seeding candidates from every
   * source song currently in {@code BackgroundMusic.TXT} ({@link #currentPlaylistPaths}) — not
   * just one core song — so the complete smart-additions list can be inspected at once. Called:
   * <ul>
   * <li>at startup, when {@code SmartBackgroundMusicSongs.json} does not yet exist</li>
   * <li>whenever the core background-music list itself finishes a full played cycle (see
   * {@link #pickNextEligibleBackgroundId()}), since song popularity may have shifted</li>
   * </ul>
   *
   * <p>
   * Candidates are computed per source song via {@link #computeSmartCandidatesForSource}, using a
   * single shared "reserved paths" set across all sources so the same candidate song is never
   * claimed by more than one source in the same build pass. Once every source has been processed,
   * songs from favorite albums (see {@link #favoriteAlbumPaths}) are interleaved in via
   * {@link #computeFavoriteAlbumCandidates}, targeting
   * {@code smartBackgroundMusicFavoriteAlbumsPercentage} of the final pool. The freshly computed
   * candidate set is then merged against the previous pool by song path: songs that are still
   * valid candidates keep their persisted identity/play history (only {@code sourceSong}/
   * {@code sourceSongNumPlays}/{@code reason} are refreshed), brand new candidates start unplayed,
   * and candidates that no longer qualify are dropped.
   */
  private void refreshSmartAdditionPool() {

    try {

      int factor = getSmartAdditionsFactor();

      List<String> sourcePaths = new ArrayList<>(currentPlaylistPaths);
      Collections.shuffle(sourcePaths, ThreadLocalRandom.current());

      Set<String> reservedPaths = new HashSet<>();
      Map<String, SmartBackgroundMusicSongEntity> freshByPath = new HashMap<>();

      for (String sourcePath : sourcePaths) {

        SongFileEntity coreSong = resolveSourceSong(sourcePath);
        if (coreSong == null) {
          continue;
        }

        List<SmartBackgroundMusicSongEntity> candidates =
            computeSmartCandidatesForSource(coreSong, factor, reservedPaths);

        for (SmartBackgroundMusicSongEntity candidate : candidates) {
          freshByPath.put(candidate.getSongFilePath(), candidate);
        }
      }

      Set<String> favoriteExcludedPaths = new HashSet<>(reservedPaths);
      favoriteExcludedPaths.addAll(freshByPath.keySet());
      List<SmartBackgroundMusicSongEntity> favoriteCandidates =
          computeFavoriteAlbumCandidates(freshByPath.size(), favoriteExcludedPaths);
      for (SmartBackgroundMusicSongEntity candidate : favoriteCandidates) {
        freshByPath.put(candidate.getSongFilePath(), candidate);
      }

      Map<String, SmartBackgroundMusicSongEntity> existingByPath = new HashMap<>();
      for (SmartBackgroundMusicSongEntity existing : smartPool) {
        existingByPath.put(existing.getSongFilePath(), existing);
      }

      List<SmartBackgroundMusicSongEntity> merged = new ArrayList<>();
      for (SmartBackgroundMusicSongEntity fresh : freshByPath.values()) {

        SmartBackgroundMusicSongEntity existing = existingByPath.get(fresh.getSongFilePath());
        if (existing != null) {
          // Still a valid candidate — preserve persisted identity/play history, refresh only why
          // it was picked, since a different source may now claim it.
          existing.setSourceSong(fresh.getSourceSong());
          existing.setSourceSongNumPlays(fresh.getSourceSongNumPlays());
          existing.setReason(fresh.getReason());
          merged.add(existing);
        } else {
          merged.add(fresh);
        }
      }

      Collections.shuffle(merged, ThreadLocalRandom.current());

      smartBackgroundMusicRepository.storeAll(merged);
      this.smartPool = merged;
      rebuildSmartCaches();

    } catch (Exception e) {
      // Fail-safe: if the refresh fails for any reason, log and leave the existing pool in place.
      log.warn("refreshSmartAdditionPool: could not refresh smart-addition pool: {}",
          e.getMessage(), e);
    }
  }

  /**
   * Resolves a raw {@code BackgroundMusic.TXT} path into a {@link SongFileEntity}, normalizing it
   * for the current OS first.
   *
   * @return the resolved entity, or {@code null} if it could not be found (e.g. the file was
   *         moved/deleted since being added to the playlist)
   */
  private SongFileEntity resolveSourceSong(String rawPath) {

    try {
      String normalized =
          backgroundMusicHelper.normalizePathForCurrentOS(rawPath, rootPathWindows, rootPathUnix);
      return this.songLibraryService.getSongLibraryRoot().getSongByPath(normalized);
    } catch (Exception e) {
      log.debug("resolveSourceSong: could not resolve source song for path {}: {}", rawPath,
          e.getMessage());
      return null;
    }
  }

  /**
   * Computes the smart-addition candidates seeded from a single source song (same artist/album,
   * and popular songs from the same genre), skipping the source entirely if its genre is
   * excluded. Every returned candidate meets the min-plays filter ({@link #isEligibleByPlayCount})
   * and is tagged with the {@link SmartAdditionReason} that explains why it was picked relative to
   * {@code coreSong}.
   *
   * <h3>Mix formula</h3>
   * <ul>
   * <li>factor = 1 → 100 % genre songs (different artist/album, popular)</li>
   * <li>factor = 2 → 1 same-artist/album song + 1 genre song</li>
   * <li>factor = 3 → 1 same-artist/album song + 2 genre songs</li>
   * <li>factor ≥ 4 → 25 % same-artist/album + 75 % genre songs (rounded)</li>
   * </ul>
   *
   * <p>
   * All candidates are drawn from {@link SongLibraryService#getGenreMusicByPopularity}
   * (popularity-ordered); the top slice is shuffled before picking so repeated builds vary.
   * {@code reservedPaths} is shared across all source songs in one {@link #refreshSmartAdditionPool()}
   * pass — any path already claimed by another source is skipped, and every path this call picks
   * is added to it before returning, so no candidate is claimed by more than one source.
   */
  private List<SmartBackgroundMusicSongEntity> computeSmartCandidatesForSource(
      SongFileEntity coreSong, int factor, Set<String> reservedPaths) {

    List<SmartBackgroundMusicSongEntity> newPool = new ArrayList<>();

    try {

      String genreName = coreSong.getAlbum().getParentGenre().getName();

      if (excludedGenres.contains(genreName.toLowerCase())) {
        log.debug("computeSmartCandidatesForSource: genre '{}' is excluded from smart additions",
            genreName);
        return newPool;
      }

      String artistName = coreSong.getArtistName();
      Integer albumId = coreSong.getAlbum().getPersistentIdentity();
      String coreSongPath = coreSong.getNaturalIdentity();

      // Determine how many same-artist/album vs genre slots this factor calls for.
      int sameArtistSlots;
      int genreSlots;
      if (factor == 1) {
        sameArtistSlots = 0;
        genreSlots = 1;
      } else if (factor <= 3) {
        sameArtistSlots = 1;
        genreSlots = factor - 1;
      } else {
        // factor >= 4: 25 % same-artist/album, 75 % genre
        sameArtistSlots = Math.max(1, Math.round(factor * 0.25f));
        genreSlots = factor - sameArtistSlots;
      }

      // Fetch popularity-ranked results for this genre from the song library.
      SearchResultDto genreResults = this.songLibraryService.getGenreMusicByPopularity(genreName);

      Set<String> excludedPaths = new HashSet<>(reservedPaths);
      excludedPaths.add(coreSongPath);

      // ── Same-artist / same-album candidates ──────────────────────────────
      if (sameArtistSlots > 0 && genreResults.getSongs() != null) {

        List<SongDto> sameArtistSongs = new ArrayList<>();
        Map<Integer, SmartAdditionReason> reasonBySongId = new HashMap<>();
        for (SongDto s : genreResults.getSongs()) {
          boolean sameArtist = artistName.equalsIgnoreCase(s.getArtistName());
          boolean sameAlbum = albumId.equals(s.getAlbumId());
          // Exclude the core song itself; prefer same album, fall back to same artist.
          boolean isCoreSong = coreSong.getPersistentIdentity().equals(s.getSongId())
              && albumId.equals(s.getAlbumId());
          if (!isCoreSong && (sameArtist || sameAlbum) && isEligibleByPlayCount(s)) {
            sameArtistSongs.add(s);
            reasonBySongId.put(s.getSongId(),
                sameAlbum ? SmartAdditionReason.SAME_ALBUM : SmartAdditionReason.SAME_ARTIST);
          }
        }

        // Results from getGenreMusicByPopularity are already popularity-ordered; take the top N
        // then shuffle to satisfy the randomness requirement.
        List<SongDto> topSameArtist =
            sameArtistSongs.subList(0, Math.min(sameArtistSlots * 10, sameArtistSongs.size()));
        Collections.shuffle(topSameArtist, ThreadLocalRandom.current());

        for (int i = 0; i < Math.min(sameArtistSlots, topSameArtist.size()); i++) {
          SongDto s = topSameArtist.get(i);
          SongFileEntity entity = findSongEntity(s.getAlbumId(), s.getSongId());
          if (entity != null && !excludedPaths.contains(entity.getNaturalIdentity())) {
            SmartAdditionReason reason = reasonBySongId.get(s.getSongId());
            newPool.add(new SmartBackgroundMusicSongEntity(null, entity.getNaturalIdentity(),
                coreSongPath, coreSong.getNumPlays(), reason));
            excludedPaths.add(entity.getNaturalIdentity());
          }
        }
      }

      // ── Genre candidates (different artist/album, popular) ────────────────
      if (genreSlots > 0 && genreResults.getSongs() != null) {

        List<SongDto> genreSongs = new ArrayList<>();
        for (SongDto s : genreResults.getSongs()) {
          boolean differentArtist = !artistName.equalsIgnoreCase(s.getArtistName());
          boolean differentAlbum = !albumId.equals(s.getAlbumId());
          if (differentArtist && differentAlbum && isEligibleByPlayCount(s)) {
            genreSongs.add(s);
          }
        }

        // Take a wider popularity-ordered slice then shuffle so the queue stays random.
        List<SongDto> topGenre =
            genreSongs.subList(0, Math.min(genreSlots * 10, genreSongs.size()));
        Collections.shuffle(topGenre, ThreadLocalRandom.current());

        int added = 0;
        for (SongDto s : topGenre) {
          if (added >= genreSlots)
            break;
          SongFileEntity entity = findSongEntity(s.getAlbumId(), s.getSongId());
          if (entity != null && !excludedPaths.contains(entity.getNaturalIdentity())) {
            newPool.add(new SmartBackgroundMusicSongEntity(null, entity.getNaturalIdentity(),
                coreSongPath, coreSong.getNumPlays(), SmartAdditionReason.POPULAR_SONG_FROM_GENRE));
            excludedPaths.add(entity.getNaturalIdentity());
            added++;
          }
        }
      }

      // Reserve every path claimed by this source so later sources in the same build pass don't
      // pick it too.
      for (SmartBackgroundMusicSongEntity candidate : newPool) {
        reservedPaths.add(candidate.getSongFilePath());
      }

    } catch (Exception e) {
      // Fail-safe: if candidate computation fails for any reason, log and return whatever was
      // gathered so far (typically empty).
      log.warn("computeSmartCandidatesForSource: could not compute smart candidates for {}: {}",
          coreSong, e.getMessage(), e);
    }

    return newPool;
  }

  /**
   * Returns {@code true} when {@code s} has at least {@code smartBackgroundMusicMinPlays} plays,
   * making it eligible as a smart-addition candidate.
   */
  private boolean isEligibleByPlayCount(SongDto s) {
    return isEligibleByPlayCount(s.getNumPlays());
  }

  /**
   * Returns {@code true} when {@code numPlays} is at least {@code smartBackgroundMusicMinPlays},
   * making a candidate eligible as a smart-addition.
   */
  private boolean isEligibleByPlayCount(Integer numPlays) {

    int plays = (numPlays == null) ? 0 : numPlays;
    return plays >= smartBackgroundMusicMinPlays;
  }

  /**
   * Picks songs from {@link #favoriteAlbumPaths} to interleave into the smart-addition pool with
   * no source song of their own (tagged {@link SmartAdditionReason#SONG_FROM_FAVORITE_ALBUM}) —
   * see {@link BackgroundMusicHelper#SMART_BACKGROUND_MUSIC_ALBUM_INCLUSIONS_FILENAME}.
   *
   * <p>
   * The number of favorite-album songs picked targets
   * {@code smartBackgroundMusicFavoriteAlbumsPercentage} of the <em>final</em> pool size — i.e.
   * solving {@code favoriteCount = pct * (normalCandidateCount + favoriteCount)} for
   * {@code favoriteCount} — clamping the percentage to [0, 95] so the target never blows up as
   * {@code pct} approaches 100 %. Candidates still must meet {@link #isEligibleByPlayCount}, and
   * never overlap {@code excludedPaths} (paths already claimed elsewhere in this build pass).
   */
  private List<SmartBackgroundMusicSongEntity> computeFavoriteAlbumCandidates(
      int normalCandidateCount, Set<String> excludedPaths) {

    List<SmartBackgroundMusicSongEntity> result = new ArrayList<>();

    if (favoriteAlbumPaths.isEmpty()) {
      return result;
    }

    try {

      double pct = Math.max(0, Math.min(95, smartBackgroundMusicFavoriteAlbumsPercentage)) / 100.0;
      int target = (int) Math.round(pct * normalCandidateCount / (1 - pct));
      if (target <= 0) {
        return result;
      }

      List<SongFileEntity> eligibleSongs = new ArrayList<>();
      for (AlbumFolderEntity album : songLibraryService.getSongLibraryRoot().getAllAlbums()) {
        if (!isFavoriteAlbum(album)) {
          continue;
        }
        for (SongFileEntity song : album.getChildSongs()) {
          if (!excludedPaths.contains(song.getNaturalIdentity())
              && isEligibleByPlayCount(song.getNumPlays())) {
            eligibleSongs.add(song);
          }
        }
      }

      Collections.shuffle(eligibleSongs, ThreadLocalRandom.current());

      for (int i = 0; i < Math.min(target, eligibleSongs.size()); i++) {
        SongFileEntity song = eligibleSongs.get(i);
        result.add(new SmartBackgroundMusicSongEntity(null, song.getNaturalIdentity(), null, null,
            SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM));
        excludedPaths.add(song.getNaturalIdentity());
      }

    } catch (Exception e) {
      // Fail-safe: if candidate computation fails for any reason, log and return whatever was
      // gathered so far (typically empty).
      log.warn("computeFavoriteAlbumCandidates: could not compute favorite-album candidates: {}",
          e.getMessage(), e);
    }

    return result;
  }

  /**
   * Returns {@code true} when {@code album} matches one of {@link #favoriteAlbumPaths}.
   *
   * <p>
   * Matching is by path <em>suffix</em>, not exact equality: {@code album.getNaturalIdentity()}
   * is always the full absolute filesystem path (e.g.
   * {@code /home/user/Music/Rock_On_Third/Pop/Compilations/90s Pub Crawl}), but
   * {@code SmartBackgroundMusicAlbumInclusions.TXT} entries are typically shorter — anything from
   * a bare {@code Genre/Artist/Album} to a path rooted at the library's own folder name (e.g.
   * {@code Rock_On_Third/Pop/Compilations/90s Pub Crawl}) — since requiring the exact absolute
   * path (including OS-specific drive letters) would make the file impractical to hand-maintain.
   * A "/" boundary is required immediately before the match so a suffix can't accidentally match
   * partway through a folder name (e.g. {@code "Compilations"} matching
   * {@code ".../90s Compilations"}).
   */
  private boolean isFavoriteAlbum(AlbumFolderEntity album) {

    String albumPath = album.getNaturalIdentity().replace('\\', '/');

    for (String favoritePath : favoriteAlbumPaths) {
      if (albumPath.equals(favoritePath) || albumPath.endsWith("/" + favoritePath)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Normalizes a raw line from {@code SmartBackgroundMusicAlbumInclusions.TXT} into the form
   * {@link #isFavoriteAlbum} compares against: forward slashes, no leading/trailing slash.
   */
  private static String normalizeAlbumInclusionPath(String rawPath) {

    String normalized = rawPath.replace('\\', '/').trim();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  /**
   * Convenience method to resolve a {@link SongFileEntity} from albumId + songId without throwing.
   *
   * @return the entity, or {@code null} if not found
   */
  private SongFileEntity findSongEntity(Integer albumId, Integer songId) {

    try {
      RootFolderEntity songLibraryRoot = this.songLibraryService.getSongLibraryRoot();
      return songLibraryRoot.getAlbumById(albumId).getChildSong(songId);
    } catch (Exception e) {
      log.debug("findSongEntity: albumId={}, songId={} not found: {}", albumId, songId,
          e.getMessage());
    }
    return null;
  }

  @EventListener
  @Override
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {
    this.rootPath = event.scanPath();
  }

  /**
   * Keeps {@link #currentlyQueuedPaths} in sync with the live song queue, so selection can avoid
   * picking a song that is already sitting in the queue waiting to play.
   */
  @EventListener
  @Override
  public void handleSongQueueChangedEvent(SongQueueChangedEvent event) {

    try {
      this.currentlyQueuedPaths = event.queuedSongs().stream()
          .map(SongQueueEntryDto::getSongPath)
          .collect(Collectors.toCollection(HashSet::new));
    } catch (Exception e) {
      log.warn("handleSongQueueChangedEvent: failed to update queued-paths cache: {}",
          e.getMessage(), e);
    }
  }

  /**
   * Marks a background or smart-addition song as played — updating {@code timeLastPlayed} and
   * persisting it — if, and only if, the song that just started playing matches one of them. This
   * is the sole place {@code timeLastPlayed} is set: selection alone (via {@link #getNextSong()}
   * / {@link #getNextSmartAdditionSong}) never marks a song played, since a queued song is not
   * guaranteed to ever actually play (queue flush, hibernation, application restart, etc.).
   *
   * <p>
   * Matching is by song path, and applies regardless of how the song ended up in the queue (auto
   * -populated by background music, or manually queued by a user) — {@code timeLastPlayed}
   * reflects whether the file was actually played, not why it was played.
   */
  @EventListener
  @Override
  public void handleSongPlaybackStartedEvent(SongPlaybackStartedEvent event) {

    try {

      String playedPath = event.songQueueEntry().getSongPath();

      Integer backgroundId = normalizedPathToId.get(playedPath);
      if (backgroundId != null) {
        BackgroundMusicSongEntity song = songsById.get(backgroundId);
        song.markPlayed(Instant.now());
        notPlayedIds.remove(backgroundId);
        backgroundMusicRepository.storeAll(allSongs);
      }

      Integer smartId = smartNormalizedPathToId.get(playedPath);
      if (smartId != null) {
        SmartBackgroundMusicSongEntity song = smartSongsById.get(smartId);
        song.markPlayed(Instant.now());
        smartNotPlayedIds.remove(smartId);
        smartBackgroundMusicRepository.storeAll(smartPool);
      }

    } catch (Exception e) {
      log.warn("handleSongPlaybackStartedEvent: failed to mark song played for event {}: {}",
          event, e.getMessage(), e);
    }
  }
}
