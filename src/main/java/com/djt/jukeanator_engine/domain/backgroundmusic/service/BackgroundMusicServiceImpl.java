package com.djt.jukeanator_engine.domain.backgroundmusic.service;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import com.djt.jukeanator_engine.domain.backgroundmusic.config.BackgroundMusicProperties;
import com.djt.jukeanator_engine.domain.backgroundmusic.exception.BackgroundMusicServiceException;
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
  private final BackgroundMusicHelper backgroundMusicHelper = new BackgroundMusicHelper();

  private String rootPath;
  private final String rootPathWindows;
  private final String rootPathUnix;

  private boolean enableBackgroundMusic;
  private final boolean enableSmartBackgroundMusicAdditions;
  private final int smartBackgroundMusicAdditionsFactor;
  private final int smartBackgroundMusicAdditionsBegin;
  private final int smartBackgroundMusicAdditionsEnd;

  public BackgroundMusicServiceImpl(String rootPath, String rootPathWindows, String rootPathUnix,
      BackgroundMusicProperties backgroundMusicProperties, SongLibraryService songLibraryService) {

    requireNonNull(rootPath, "rootPath cannot be null");
    requireNonNull(rootPathWindows, "rootPathWindows cannot be null");
    requireNonNull(rootPathUnix, "rootPathUnix cannot be null");
    requireNonNull(backgroundMusicProperties, "backgroundMusicProperties cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");

    this.rootPath = rootPath;
    this.rootPathWindows = rootPathWindows;
    this.rootPathUnix = rootPathUnix;
    this.songLibraryService = songLibraryService;

    this.enableBackgroundMusic = backgroundMusicProperties.isEnableBackgroundMusic();
    this.enableSmartBackgroundMusicAdditions =
        backgroundMusicProperties.isEnableSmartBackgroundMusicAdditions();
    this.smartBackgroundMusicAdditionsFactor =
        backgroundMusicProperties.getSmartBackgroundMusicAdditionsFactor();
    this.smartBackgroundMusicAdditionsBegin =
        backgroundMusicProperties.getSmartBackgroundMusicAdditionsBegin();
    this.smartBackgroundMusicAdditionsEnd =
        backgroundMusicProperties.getSmartBackgroundMusicAdditionsEnd();

    initialize();
  }

  private void initialize() {

    log.info("enableBackgroundMusic: " + this.enableBackgroundMusic);
    log.info("enableSmartBackgroundMusicAdditions: " + this.enableSmartBackgroundMusicAdditions);
    log.info("smartBackgroundMusicAdditionsFactor: " + this.smartBackgroundMusicAdditionsFactor);
    log.info("smartBackgroundMusicAdditionsBegin: " + this.smartBackgroundMusicAdditionsBegin);
    log.info("smartBackgroundMusicAdditionsEnd: " + this.smartBackgroundMusicAdditionsEnd);

    if (!this.enableBackgroundMusic) {
      return;
    }

    // Try first to load from a file called BackgroundMusic.TXT
    try {

      backgroundMusicHelper.initializeBackgroundMusic(this.rootPath);

    } catch (Exception e1) {

      // NOTE: If unable to load BackgroundMusic.TXT, then fall back to using the most popular
      // songs. The first draw from getNextSong() will self-heal the played/not-played files.
      log.error("Unable to initialize background music playlist, error: " + e1.getMessage());

      try {

        createBackgroundMusicFromTopSongs();

      } catch (Exception e) {

        // NOTE: If unable to create a playlist from the most popular songs, disable the feature.
        log.error(
            "Unable to auto-populate background music playlist with top songs, error: "
                + e.getMessage());

        this.enableBackgroundMusic = false;
      }
    }
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

  @Override
  public SongFileEntity getNextSong() {

    try {

      String songPathName = backgroundMusicHelper.getRandomSongFromBackgroundMusicPlaylist(
          this.rootPath, this.rootPathWindows, this.rootPathUnix);

      SongFileEntity song = this.songLibraryService.getSongLibraryRoot().getSongByPath(songPathName);

      backgroundMusicHelper.update(this.rootPath);

      return song;

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

      String songPathName = backgroundMusicHelper.getRandomSmartAdditionSongPath(this.rootPath,
          this.rootPathWindows, this.rootPathUnix);

      if (songPathName == null) {
        // Pool was exhausted; helper has reset — caller should treat this as a miss.
        return null;
      }

      SongFileEntity song;
      try {
        song = this.songLibraryService.getSongLibraryRoot().getSongByPath(songPathName);
      } catch (EntityDoesNotExistException ednee) {
        // Path not found in library — fail gracefully so the caller can fall back.
        log.warn("getNextSmartAdditionSong: could not find song for path: {}", songPathName);
        return null;
      }

      backgroundMusicHelper.updateSmartAdditions(this.rootPath);
      return song;

    } catch (Exception e) {
      log.warn("getNextSmartAdditionSong: failed for core song {}: {}", coreSong, e.getMessage());
      return null;
    }
  }

  /**
   * Ensures the smart-additions pool held by {@code BackgroundMusicHelper} contains candidates
   * relevant to {@code coreSong}. The pool is rebuilt only when it is empty (i.e. all previously
   * loaded candidates have been played), so across a single background-music cycle the pool
   * accumulates candidates from every core song encountered.
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
   * (popularity-ordered) and sorted randomly before loading into the pool.
   */
  private void buildSmartAdditionPool(SongFileEntity coreSong, int factor) {

    try {

      // Only rebuild the pool when it is empty — preserves the played/not-played cycle.
      if (backgroundMusicHelper.getSmartAdditionsNotPlayedCount() > 0) {
        return;
      }

      String genreName = coreSong.getAlbum().getParentGenre().getName();
      String artistName = coreSong.getArtistName();
      Integer albumId = coreSong.getAlbum().getPersistentIdentity();

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

      List<String> candidatePaths = new ArrayList<>();

      // ── Same-artist / same-album candidates ──────────────────────────────
      if (sameArtistSlots > 0 && genreResults.getSongs() != null) {

        List<SongDto> sameArtistSongs = new ArrayList<>();
        for (SongDto s : genreResults.getSongs()) {
          boolean sameArtist = artistName.equalsIgnoreCase(s.getArtistName());
          boolean sameAlbum = albumId.equals(s.getAlbumId());
          // Exclude the core song itself; prefer same album, fall back to same artist.
          boolean isCoreSong = coreSong.getPersistentIdentity().equals(s.getSongId())
              && albumId.equals(s.getAlbumId());
          if (!isCoreSong && (sameArtist || sameAlbum)) {
            sameArtistSongs.add(s);
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
          if (entity != null) {
            candidatePaths.add(entity.getNaturalIdentity());
          }
        }
      }

      // ── Genre candidates (different artist/album, popular) ────────────────
      if (genreSlots > 0 && genreResults.getSongs() != null) {

        // Build an exclusion set: paths already in candidatePaths + the core song itself.
        Set<String> excluded = new HashSet<>(candidatePaths);
        excluded.add(coreSong.getNaturalIdentity());

        List<SongDto> genreSongs = new ArrayList<>();
        for (SongDto s : genreResults.getSongs()) {
          boolean differentArtist = !artistName.equalsIgnoreCase(s.getArtistName());
          boolean differentAlbum = !albumId.equals(s.getAlbumId());
          if (differentArtist && differentAlbum) {
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
          if (entity != null && !excluded.contains(entity.getNaturalIdentity())) {
            candidatePaths.add(entity.getNaturalIdentity());
            excluded.add(entity.getNaturalIdentity());
            added++;
          }
        }
      }

      // Shuffle the final combined list one more time to fully randomise the draw order.
      Collections.shuffle(candidatePaths, ThreadLocalRandom.current());

      if (!candidatePaths.isEmpty()) {
        backgroundMusicHelper.loadSmartAdditionCandidates(this.rootPath, candidatePaths);
      }

    } catch (Exception e) {
      // Fail-safe: if pool-building fails for any reason, log and leave the pool empty so
      // getNextSmartAdditionSong() will fall back gracefully.
      log.warn("buildSmartAdditionPool: could not build smart-addition pool for {}: {}", coreSong,
          e.getMessage(), e);
    }
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
