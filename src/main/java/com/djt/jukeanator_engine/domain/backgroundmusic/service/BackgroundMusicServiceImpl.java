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
import com.djt.jukeanator_engine.domain.backgroundmusic.config.BackgroundMusicProperties;
import com.djt.jukeanator_engine.domain.backgroundmusic.exception.BackgroundMusicServiceException;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartAdditionReason;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.BackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.SmartBackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.utils.BackgroundMusicHelper;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

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

  // ── Core background-music in-memory cache (Item 5) ────────────────────────
  private List<BackgroundMusicSongEntity> allSongs = new ArrayList<>();
  private Map<Integer, BackgroundMusicSongEntity> songsById = new HashMap<>();
  private List<Integer> notPlayedIds = new ArrayList<>();
  private Set<String> currentPlaylistPaths = new HashSet<>();

  // ── Genres excluded from smart-addition candidate selection ───────────────
  private Set<String> excludedGenres = new HashSet<>();

  // ── Smart-additions in-memory cache (disposable per-cycle pool) ───────────
  private List<SmartBackgroundMusicSongEntity> smartPool = new ArrayList<>();
  private Map<Integer, SmartBackgroundMusicSongEntity> smartSongsById = new HashMap<>();
  private List<Integer> smartNotPlayedIds = new ArrayList<>();

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

    initialize();
  }

  private void initialize() {

    log.info("enableBackgroundMusic: " + this.enableBackgroundMusic);
    log.info("enableSmartBackgroundMusicAdditions: " + this.enableSmartBackgroundMusicAdditions);
    log.info("smartBackgroundMusicAdditionsFactor: " + this.smartBackgroundMusicAdditionsFactor);
    log.info("smartBackgroundMusicAdditionsBegin: " + this.smartBackgroundMusicAdditionsBegin);
    log.info("smartBackgroundMusicAdditionsEnd: " + this.smartBackgroundMusicAdditionsEnd);
    log.info("smartBackgroundMusicMinPlays: " + this.smartBackgroundMusicMinPlays);

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
   */
  private void loadAndReconcile() throws IOException {

    List<String> playlistPaths = backgroundMusicHelper.readBackgroundMusicPlaylist(this.rootPath);
    this.currentPlaylistPaths = new HashSet<>(playlistPaths);

    List<String> genreExclusions =
        backgroundMusicHelper.readSmartBackgroundMusicGenreExclusions(this.rootPath);
    this.excludedGenres = genreExclusions.stream()
        .map(String::toLowerCase)
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
  }

  private void rebuildSongsById() {

    this.songsById = new HashMap<>();
    for (BackgroundMusicSongEntity song : allSongs) {
      songsById.put(song.getPersistentIdentity(), song);
    }
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

  @Override
  public SongFileEntity getNextSong() {

    try {

      if (notPlayedIds.isEmpty()) {
        // Every song has been played this cycle — reset all of them and start over.
        for (BackgroundMusicSongEntity song : allSongs) {
          song.setTimeLastPlayed(null);
        }
        rebuildNotPlayedCache();
      }

      if (notPlayedIds.isEmpty()) {
        throw new BackgroundMusicServiceException("No background music songs available");
      }

      Integer chosenId = pickRandom(notPlayedIds);
      BackgroundMusicSongEntity chosen = songsById.get(chosenId);

      chosen.markPlayed(Instant.now());
      notPlayedIds.remove(chosenId);

      backgroundMusicRepository.storeAll(allSongs);

      String normalizedPath = backgroundMusicHelper.normalizePathForCurrentOS(
          chosen.getSongFilePath(), this.rootPathWindows, this.rootPathUnix);

      return this.songLibraryService.getSongLibraryRoot().getSongByPath(normalizedPath);

    } catch (Exception e) {
      throw new BackgroundMusicServiceException(
          "Cannot get next background music song, error: " + e.getMessage(), e);
    }
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

  @Override
  public SongFileEntity getNextSmartAdditionSong(SongFileEntity coreSong) {

    try {

      buildSmartAdditionPool(coreSong, getSmartAdditionsFactor());

      if (smartNotPlayedIds.isEmpty()) {
        // Pool was exhausted/empty; caller should treat this as a miss.
        return null;
      }

      Integer chosenId = pickRandom(smartNotPlayedIds);
      SmartBackgroundMusicSongEntity chosen = smartSongsById.get(chosenId);

      chosen.markPlayed(Instant.now());
      smartNotPlayedIds.remove(chosenId);
      smartBackgroundMusicRepository.storeAll(smartPool);

      String normalizedPath = backgroundMusicHelper.normalizePathForCurrentOS(
          chosen.getSongFilePath(), this.rootPathWindows, this.rootPathUnix);

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
    for (SmartBackgroundMusicSongEntity song : smartPool) {
      smartSongsById.put(song.getPersistentIdentity(), song);
    }
    this.smartNotPlayedIds = smartPool.stream()
        .filter(SmartBackgroundMusicSongEntity::isNotYetPlayed)
        .map(SmartBackgroundMusicSongEntity::getPersistentIdentity)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * Ensures the in-memory smart-additions pool contains candidates relevant to
   * {@code coreSong}. The pool is rebuilt only when it is empty (i.e. all previously loaded
   * candidates have been played), and — being a disposable per-cycle pool — is wholesale-replaced
   * in the repository each time it is rebuilt rather than accumulated as permanent history.
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
   * (popularity-ordered) and sorted randomly before loading into the pool. Each candidate is
   * tagged with the {@link SmartAdditionReason} that explains why it was picked.
   */
  private void buildSmartAdditionPool(SongFileEntity coreSong, int factor) {

    try {

      // Only rebuild the pool when it is empty — preserves the played/not-played cycle.
      if (!smartNotPlayedIds.isEmpty()) {
        return;
      }

      String genreName = coreSong.getAlbum().getParentGenre().getName();

      if (excludedGenres.contains(genreName.toLowerCase())) {
        log.debug("buildSmartAdditionPool: genre '{}' is excluded from smart additions",
            genreName);
        return;
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

      List<SmartBackgroundMusicSongEntity> newPool = new ArrayList<>();
      Set<String> excludedPaths = new HashSet<>();
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
                coreSongPath, reason));
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
                coreSongPath, SmartAdditionReason.POPULAR_SONG_FROM_GENRE));
            excludedPaths.add(entity.getNaturalIdentity());
            added++;
          }
        }
      }

      // Shuffle the final combined list one more time to fully randomise the draw order.
      Collections.shuffle(newPool, ThreadLocalRandom.current());

      if (!newPool.isEmpty()) {
        // Disposable per-cycle pool — wholesale-replace whatever was persisted before.
        smartBackgroundMusicRepository.storeAll(newPool);
        this.smartPool = newPool;
        rebuildSmartCaches();
      }

    } catch (Exception e) {
      // Fail-safe: if pool-building fails for any reason, log and leave the pool empty so
      // getNextSmartAdditionSong() will fall back gracefully.
      log.warn("buildSmartAdditionPool: could not build smart-addition pool for {}: {}", coreSong,
          e.getMessage(), e);
    }
  }

  /**
   * Returns {@code true} when {@code s} has at least {@code smartBackgroundMusicMinPlays} plays,
   * making it eligible as a smart-addition candidate.
   */
  private boolean isEligibleByPlayCount(SongDto s) {

    int plays = (s.getNumPlays() == null) ? 0 : s.getNumPlays();
    return plays >= smartBackgroundMusicMinPlays;
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
}
