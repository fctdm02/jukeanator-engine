package com.djt.jukeanator_engine.domain.songqueue.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * When each song last started playing, for the queue's "minimum minutes between song plays" rule.
 * Kept only as long as the rule looks back, and -- when given a file -- written through to it on
 * every play, so a restart of the jukebox does not let a song that just played be queued again.
 *
 * <p>Songs are matched the way the rule always has: the same title by the same song artist, or by
 * the same album artist (so the same song on a compilation or a greatest-hits album still counts).
 */
public class RecentSongPlays {

  private static final Logger log = LoggerFactory.getLogger(RecentSongPlays.class);

  /** One play: who and what (for matching), and when it started. */
  public record Play(String songName, String artistName, String albumArtistName,
      long startedAtEpochMillis) {
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Duration window;
  private final Path file;
  private final List<Play> plays = new ArrayList<>();

  /**
   * @param window how far back a play counts (minimum-minutes-between-song-plays)
   * @param file where plays are kept across restarts, or null to keep them in memory only
   */
  public RecentSongPlays(Duration window, Path file) {
    this.window = window;
    this.file = file;
    load();
  }

  /** Records that {@code song} started playing at {@code startedAt}. */
  public synchronized void recordPlay(SongFileEntity song, Instant startedAt) {

    plays.add(new Play(song.getSongName(), song.getArtistName(), albumArtistName(song),
        startedAt.toEpochMilli()));
    prune(startedAt);
    store();
  }

  /**
   * @return when a song matching {@code song} last started playing within the window, if it did
   */
  public synchronized Optional<Instant> lastPlayedWithinWindow(SongFileEntity song, Instant now) {

    prune(now);
    String songName = song.getSongName();
    String artistName = song.getArtistName();
    String albumArtistName = albumArtistName(song);
    Instant latest = null;
    for (Play play : plays) {
      boolean sameSong = songName != null && songName.equals(play.songName())
          && ((artistName != null && artistName.equals(play.artistName()))
              || (albumArtistName != null && albumArtistName.equals(play.albumArtistName())));
      if (sameSong) {
        Instant startedAt = Instant.ofEpochMilli(play.startedAtEpochMillis());
        if (latest == null || startedAt.isAfter(latest)) {
          latest = startedAt;
        }
      }
    }
    return Optional.ofNullable(latest);
  }

  private void prune(Instant now) {
    long cutoff = now.minus(window).toEpochMilli();
    plays.removeIf(play -> play.startedAtEpochMillis() < cutoff);
  }

  private static String albumArtistName(SongFileEntity song) {
    try {
      return song.getAlbum().getParentArtist().getName();
    } catch (RuntimeException e) {
      return null; // a song detached from the library tree -- matched by its own artist only
    }
  }

  private void load() {

    if (file == null || !Files.isRegularFile(file)) {
      return;
    }
    try {
      plays.addAll(MAPPER.readValue(file.toFile(), new TypeReference<List<Play>>() {}));
      prune(Instant.now());
    } catch (IOException | RuntimeException e) {
      // Losing this only shortens the wait for songs played just before the restart.
      log.warn("Could not read recent song plays from {}; starting with none", file, e);
    }
  }

  private void store() {

    if (file == null) {
      return;
    }
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Path temp = file.resolveSibling(file.getFileName() + ".tmp");
      MAPPER.writeValue(temp.toFile(), plays);
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException | RuntimeException e) {
      log.warn("Could not store recent song plays to {}", file, e);
    }
  }
}
