package com.djt.jukeanator_engine.domain.songplayer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.songlibrary.config.SongLibraryProperties;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * ONE-OFF, TEMPORARY functional test: scans {@code C:\Music}, queues every album (except those
 * under {@code Christmas Music} and {@code Irish Music}, see {@link #EXCLUDED_DIRS}) the same way the
 * Admin screen's "Queue Album" button does, then plays the first 5 seconds of every song through
 * Winamp (as deployed at the bar) before skipping to the next one the same way the "Next" button
 * does. Its purpose is to find song files that are corrupt or otherwise fail to play, so they can
 * be deleted or replaced.
 *
 * <p>
 * Every failing file is flagged by its absolute path, both in the log ({@code PLAY-TEST FAILURE})
 * and in a report file ({@code <user.home>\Music-play-test-report.txt} by default, override with
 * {@code -DplayAllSongsReport=<path>}), as soon as it is detected:
 * <ul>
 * <li><b>NOT QUEUED</b> — a song file on disk that never made it into the queue (e.g. the scanner
 * skipped it, or queueing its album failed).</li>
 * <li><b>SUSPECT</b> — the song started, but after 5 seconds Winamp was not playing it, or playback
 * had not progressed (e.g. Winamp crashed, hung, or stalled on it).</li>
 * <li><b>NOT PLAYED</b> — the song was queued but never started (it failed to start, or was skipped
 * as having previously broken playback).</li>
 * </ul>
 *
 * <p>
 * Only runs when explicitly requested, since it takes roughly 6 seconds per song:
 *
 * <pre>
 * ./mvnw test -Dtest=PlayAllSongsFunctionalTest -DplayAllSongs=true
 * </pre>
 */
@SpringBootTest(properties = {"song-player.player-type=winamp",
    "song-library.disable-internet-search=true", "song-library.requires-metadata=false",
    "background-music.enable-background-music=false"})
@ActiveProfiles("test") // loads application-test.yml
@DirtiesContext // closes the context afterwards so Winamp is released
@EnabledIfSystemProperty(named = "playAllSongs", matches = "true")
class PlayAllSongsFunctionalTest extends AbstractServiceIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(PlayAllSongsFunctionalTest.class);

  private static final Path MUSIC_DIR = Path.of("C:\\Music");

  /** Folders under {@link #MUSIC_DIR} that are scanned but neither played nor flagged. */
  private static final List<Path> EXCLUDED_DIRS =
      List.of(MUSIC_DIR.resolve("Christmas Music"), MUSIC_DIR.resolve("Irish Music"));

  /**
   * Resume point: songs queued up to and including this one were already tested by an earlier run,
   * so they are neither played nor flagged. Override with {@code -DplayAllSongsStartAfter=<path>};
   * an empty value tests every song. Relies on the library listing albums in the same order on
   * every scan of the same folder.
   */
  private static final String START_AFTER = System.getProperty("playAllSongsStartAfter",
      "C:\\Music\\Hip Hop\\Compilations\\Def Jam 10th Year Anniversary - Disc 1\\"
          + "Public Enemy-13-Welcome To The Terrordome.mp3");

  // Defaults to the user's home folder: Windows does not let a non-elevated process create files
  // in the root of C:\.
  private static final Path REPORT_FILE = Path.of(System.getProperty("playAllSongsReport",
      Path.of(System.getProperty("user.home"), "Music-play-test-report.txt").toString()));

  /** How long each song is played before skipping to the next one. */
  private static final long PLAY_MS = 5_000L;

  /** A song must have advanced at least this far after {@link #PLAY_MS} to count as playing. */
  private static final long MIN_ELAPSED_SECONDS = 3L;

  /** How often playback status is sampled while a song plays. */
  private static final long SAMPLE_MS = 500L;

  /** How long to wait for the next song to start before reporting a stall. */
  private static final long NEXT_SONG_WAIT_MS = 30_000L;

  /** Give up once no song has started for this long while songs are still queued. */
  private static final long MAX_OUTAGE_MS = 5 * 60_000L;

  private static final int PROGRESS_INTERVAL = 25;

  private static final String NOT_QUEUED = "NOT QUEUED";
  private static final String SUSPECT = "SUSPECT";
  private static final String NOT_PLAYED = "NOT PLAYED";

  @Autowired
  private SongPlayerService songPlayerService;

  @Autowired
  private SongQueueService songQueueService;

  @Autowired
  private SongLibraryService songLibraryService;

  @Autowired
  private SongLibraryProperties songLibraryProperties;

  /** Flagged path → reason, in detection order. */
  private final Map<String, String> failures = new LinkedHashMap<>();

  @Test
  void playsTheFirstFiveSecondsOfEverySong() throws Exception {

    assumeTrue(Files.isDirectory(MUSIC_DIR), MUSIC_DIR + " does not exist");
    startReport();

    // 1. Scan the music folder
    log.info("PLAY-TEST: scanning {}", MUSIC_DIR);
    songLibraryService.scanFileSystemForSongs(new ScanRequest(MUSIC_DIR.toString()));
    Integer locationId = songLibraryService.getOwnLocationId();
    List<AlbumDto> albums = songLibraryService.getAlbums(locationId);
    log.info("PLAY-TEST: scanned {} albums", albums.size());

    // 2-4. Queue every album while the queue is locked, so nothing starts mid-way
    // AlbumDto carries no folder path, so songs under an excluded folder are recognized by their
    // queue entry's path and removed again straight away (the queue is locked, so none plays).
    // Songs up to and including START_AFTER are removed the same way, as each album is queued, so
    // the removals happen while the queue is still small.
    songQueueService.lock();
    Map<SongDto, SongQueueEntryDto> queued = new LinkedHashMap<>();
    Set<String> alreadyTested = new HashSet<>();
    String startAfter = START_AFTER.isBlank() ? null : normalize(START_AFTER);
    boolean reachedStart = startAfter == null;
    int excluded = 0;
    for (AlbumDto album : albums) {
      try {
        for (SongQueueEntryDto entry : songQueueService.addAlbumToQueue(locationId,
            new AddAlbumToQueueRequest(SongQueueService.LOCAL_USERNAME, album.albumId(), 1))) {
          String path = normalize(entry.songPath());
          if (!reachedStart) {
            removeFromQueue(locationId, entry);
            alreadyTested.add(path);
            reachedStart = path.equals(startAfter);
          } else if (isExcluded(entry.songPath())) {
            removeFromQueue(locationId, entry);
            excluded++;
          } else {
            queued.put(entry.song(), entry);
          }
        }
      } catch (RuntimeException e) {
        log.error("PLAY-TEST: unable to queue album {} (albumId {})", album.albumName(),
            album.albumId(), e);
      }
    }
    assertTrue(reachedStart, "The start-after song was not found in the library: " + START_AFTER);
    log.info("PLAY-TEST: skipped {} songs already tested (up to and including {}); left out {} "
        + "songs under {}", alreadyTested.size(), START_AFTER, excluded, EXCLUDED_DIRS);
    flagFilesNotQueued(queued.values(), alreadyTested);
    int total = queued.size();
    log.info("PLAY-TEST: queued {} songs; playing the first {} s of each (about {} min)", total,
        PLAY_MS / 1000, Duration.ofSeconds(total * (PLAY_MS / 1000 + 1)).toMinutes());
    assertFalse(queued.isEmpty(), "No songs were queued from " + MUSIC_DIR);

    songQueueService.unlock();

    // 5. Play each song for 5 seconds, then skip to the next one
    long startMs = System.currentTimeMillis();
    Set<SongDto> started = new HashSet<>();
    SongDto previous = null;
    int ok = 0;
    while (true) {

      SongDto current = awaitNextSong(locationId, previous, queued);
      if (current == null) {
        break;
      }
      started.add(current);
      flagSkippedSongs(locationId, queued, started, current);

      String outcome = playFor5Seconds(locationId, current);
      if (outcome == null) {
        ok++;
      } else {
        flag(SUSPECT, queued.get(current), outcome);
      }
      previous = current;

      if (current.equals(songPlayerService.getNowPlayingSong(locationId))) {
        songPlayerService.playNextTrack(locationId);
      }

      if (started.size() % PROGRESS_INTERVAL == 0) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        long remainingMs = elapsedMs / started.size() * (total - started.size());
        log.info("PLAY-TEST: {} of {} songs started ({} flagged), elapsed {} min, about {} min "
            + "remaining", started.size(), total, failures.size(), elapsedMs / 60_000,
            remainingMs / 60_000);
      }
    }

    // 6. Anything queued that never started
    for (Map.Entry<SongDto, SongQueueEntryDto> e : queued.entrySet()) {
      if (!started.contains(e.getKey())) {
        flag(NOT_PLAYED, e.getValue(), "never started");
      }
    }

    finishReport(total, ok);

    // 7. The report is the real result
    assertTrue(failures.isEmpty(), failures.size() + " song file(s) failed to play; see "
        + REPORT_FILE.toAbsolutePath());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Playback
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Waits for a song other than {@code previous} to become the now-playing song.
   *
   * @return the new song, or {@code null} once the queue is empty and nothing more starts (or the
   *         player has been unavailable for {@link #MAX_OUTAGE_MS})
   */
  private SongDto awaitNextSong(Integer locationId, SongDto previous,
      Map<SongDto, SongQueueEntryDto> queued) throws InterruptedException {

    long waitStart = System.currentTimeMillis();
    long lastWarning = waitStart;
    while (true) {
      SongDto now = songPlayerService.getNowPlayingSong(locationId);
      if (now != null && !now.equals(previous) && queued.containsKey(now)) {
        return now;
      }

      long waited = System.currentTimeMillis() - waitStart;
      boolean queueEmpty = queuedSongs(locationId).isEmpty();
      if (queueEmpty && waited >= NEXT_SONG_WAIT_MS) {
        return null;
      }
      if (waited >= MAX_OUTAGE_MS) {
        log.error("PLAY-TEST: no song has started for {} min while songs are still queued; "
            + "giving up", MAX_OUTAGE_MS / 60_000);
        return null;
      }
      if (System.currentTimeMillis() - lastWarning >= NEXT_SONG_WAIT_MS) {
        log.warn("PLAY-TEST: still waiting for the next song to start ({} s)", waited / 1000);
        lastWarning = System.currentTimeMillis();
      }
      Thread.sleep(SAMPLE_MS);
    }
  }

  /**
   * Lets {@code song} play for {@link #PLAY_MS}, sampling its playback status.
   *
   * @return {@code null} if it played, otherwise why it is suspect
   */
  private String playFor5Seconds(Integer locationId, SongDto song) throws InterruptedException {

    long maxElapsed = 0L;
    long totalSeconds = 0L;
    SongPlaybackStatusDto status = null;
    long deadline = System.currentTimeMillis() + PLAY_MS;
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(SAMPLE_MS);

      if (!song.equals(songPlayerService.getNowPlayingSong(locationId))) {
        // It ended before the 5 s were up: fine only for a song that short which played through
        if (totalSeconds > 0 && totalSeconds <= PLAY_MS / 1000 + 1
            && maxElapsed >= totalSeconds - 1) {
          return null;
        }
        return "playback ended after " + maxElapsed + " s (length " + totalSeconds
            + " s); the player probably crashed or hung on it";
      }

      status = songPlayerService.getPlaybackStatus(locationId);
      if (status.elapsedSeconds() != null) {
        maxElapsed = Math.max(maxElapsed, status.elapsedSeconds());
      }
      if (status.totalSeconds() != null && status.totalSeconds() > 0) {
        totalSeconds = status.totalSeconds();
      }
    }

    if (status == null || status.status() != SongPlayerStatus.PLAYING) {
      return "not playing after " + PLAY_MS / 1000 + " s (status "
          + (status != null ? status.status() : "unknown") + ", elapsed " + maxElapsed + " s)";
    }
    if (maxElapsed < MIN_ELAPSED_SECONDS) {
      return "playback did not progress (elapsed " + maxElapsed + " s after " + PLAY_MS / 1000
          + " s)";
    }
    return null;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Flagging
  // ─────────────────────────────────────────────────────────────────────────

  private void removeFromQueue(Integer locationId, SongQueueEntryDto entry) {
    songQueueService.removeSongDownFromQueue(locationId,
        new ChangeSongQueueRequest(entry.song().albumId(), entry.song().songId()));
  }

  /**
   * Flags every song file under {@link #MUSIC_DIR} that did not make it into the queue, other than
   * those excluded or already tested (normalized paths).
   */
  private void flagFilesNotQueued(Iterable<SongQueueEntryDto> queuedEntries,
      Set<String> alreadyTested) throws IOException {

    Set<String> queuedPaths = new HashSet<>();
    for (SongQueueEntryDto entry : queuedEntries) {
      queuedPaths.add(normalize(entry.songPath()));
    }
    Set<String> extensions = songLibraryProperties.getAcceptedSongFileExtensions().stream()
        .map(e -> e.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

    List<Path> notQueued;
    try (Stream<Path> files = Files.walk(MUSIC_DIR)) {
      notQueued = files.filter(Files::isRegularFile)
          .filter(p -> extensions.stream()
              .anyMatch(ext -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)))
          .filter(p -> !isExcluded(p.toString()))
          .filter(p -> !alreadyTested.contains(normalize(p.toString())))
          .filter(p -> !queuedPaths.contains(normalize(p.toString()))).toList();
    }
    for (Path path : notQueued) {
      flag(NOT_QUEUED, path.toAbsolutePath().toString(),
          "not in the scanned library, or its album could not be queued");
    }
  }

  /**
   * Flags songs that left the queue without ever starting (skipped by the player service, e.g. as
   * having previously broken playback, or because they failed to start).
   */
  private void flagSkippedSongs(Integer locationId, Map<SongDto, SongQueueEntryDto> queued,
      Set<SongDto> started, SongDto current) {

    Set<SongDto> stillQueued = queuedSongs(locationId).stream()
        .map(SongQueueEntryDto::song).collect(Collectors.toSet());
    for (Map.Entry<SongDto, SongQueueEntryDto> e : queued.entrySet()) {
      SongDto song = e.getKey();
      if (!started.contains(song) && !stillQueued.contains(song) && !song.equals(current)
          && !failures.containsKey(e.getValue().songPath())) {
        flag(NOT_PLAYED, e.getValue(), "left the queue without starting (failed to start, or "
            + "skipped as having broken playback)");
      }
    }
  }

  /**
   * {@link SongQueueService#getQueuedSongs}, retried a few times so that one transient failure
   * cannot abort a run that takes many hours.
   */
  private List<SongQueueEntryDto> queuedSongs(Integer locationId) {
    for (int attempt = 1;; attempt++) {
      try {
        return songQueueService.getQueuedSongs(locationId);
      } catch (RuntimeException e) {
        if (attempt >= 5) {
          throw e;
        }
        log.warn("PLAY-TEST: getQueuedSongs failed (attempt {}); retrying", attempt, e);
        try {
          Thread.sleep(SAMPLE_MS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }

  private void flag(String reason, SongQueueEntryDto entry, String detail) {
    SongDto song = entry.song();
    flag(reason, entry.songPath(), detail + " — " + song.artistName() + " / " + song.albumName()
        + " / " + song.songName());
  }

  private void flag(String reason, String path, String detail) {
    if (failures.containsKey(path)) {
      return;
    }
    failures.put(path, reason);
    log.error("PLAY-TEST FAILURE [{}] {} : {}", reason, path, detail);
    appendReport("[" + reason + "] " + path + " : " + detail);
  }

  /** Whether {@code path} lies under one of the {@link #EXCLUDED_DIRS}. */
  private static boolean isExcluded(String path) {
    String normalized = normalize(path);
    return EXCLUDED_DIRS.stream()
        .anyMatch(dir -> normalized.startsWith(normalize(dir.toString()) + File.separator));
  }

  private static String normalize(String path) {
    return Path.of(path).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Report
  // ─────────────────────────────────────────────────────────────────────────

  private void startReport() throws IOException {
    Files.writeString(REPORT_FILE, "Play-all-songs test of " + MUSIC_DIR + ", started "
        + LocalDateTime.now() + System.lineSeparator() + System.lineSeparator()
        + "Failures, as detected:" + System.lineSeparator(), StandardCharsets.UTF_8);
    log.info("PLAY-TEST: writing failures to {}", REPORT_FILE.toAbsolutePath());
  }

  private void appendReport(String line) {
    try {
      Files.writeString(REPORT_FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      log.error("PLAY-TEST: unable to write to {}", REPORT_FILE, e);
    }
  }

  private void finishReport(int total, int ok) {

    Map<String, Long> counts = failures.values().stream()
        .collect(Collectors.groupingBy(r -> r, LinkedHashMap::new, Collectors.counting()));
    List<String> lines = new ArrayList<>();
    lines.add("");
    lines.add("Finished " + LocalDateTime.now());
    lines.add("Songs queued: " + total + ", played OK: " + ok + ", flagged: " + failures.size()
        + " " + counts);
    lines.add("");
    lines.add("Files to delete or replace:");
    lines.addAll(failures.keySet());
    lines.forEach(this::appendReport);

    log.info("PLAY-TEST: done. queued {}, played OK {}, flagged {} {}; report: {}", total, ok,
        failures.size(), counts, REPORT_FILE.toAbsolutePath());
  }
}
