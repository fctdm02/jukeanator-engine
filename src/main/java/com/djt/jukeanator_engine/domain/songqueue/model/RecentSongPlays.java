package com.djt.jukeanator_engine.domain.songqueue.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * When each song last started playing, for the queue's "minimum minutes between song plays" rule.
 * Kept only as long as the rule looks back, and in memory only: the rule is enforced against the
 * plays of the running instance, so after a restart every song may be played again right away.
 *
 * <p>Songs are matched the way the rule always has: the same title by the same song artist, or by
 * the same album artist (so the same song on a compilation or a greatest-hits album still counts).
 */
public class RecentSongPlays {

  /** One play: who and what (for matching), and when it started. */
  public record Play(String songName, String artistName, String albumArtistName,
      long startedAtEpochMillis) {
  }

  private final Duration window;
  private final List<Play> plays = new ArrayList<>();

  /**
   * @param window how far back a play counts (minimum-minutes-between-song-plays)
   */
  public RecentSongPlays(Duration window) {
    this.window = window;
  }

  /** Records that {@code song} started playing at {@code startedAt}. */
  public synchronized void recordPlay(SongFileEntity song, Instant startedAt) {

    plays.add(new Play(song.getSongName(), song.getArtistName(), albumArtistName(song),
        startedAt.toEpochMilli()));
    prune(startedAt);
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
}
