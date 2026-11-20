package com.djt.jukeanator_engine.domain.backgroundmusic.service;

import java.util.function.Predicate;

import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueChangedEvent;

/**
 * Drives the background-music playlist: a played/not-played rotation over
 * {@code BackgroundMusic.TXT}, optionally interleaved with "smart addition" songs (same
 * artist/album and popular genre songs) during a configured time window.
 *
 * <p>
 * NOTE: This service has no controller or HTTP client — it is a system-internal engine intended to
 * be consumed only by {@code SongQueueServiceImpl#autoPopulateQueue()}.
 *
 * @author tmyers
 */
public interface BackgroundMusicService {

  /**
   * @return whether background music is currently enabled and available. Becomes {@code false} at
   *         startup if the background-music playlist could not be initialized and no fallback
   *         (top songs) could be created either.
   */
  boolean isEnabled();

  /**
   * @return the next core background-music song, drawn from the played/not-played rotation over
   *         {@code BackgroundMusic.TXT}
   */
  default SongFileEntity getNextSong() {
    return getNextSong(song -> true);
  }

  /**
   * Same as {@link #getNextSong()}, but only returns a song for which {@code isQueueable} is
   * {@code true} (e.g. the song queue's eligibility rules: not explicit outside the allowed hours,
   * not played too recently, and so on). Songs that fail it are skipped for this call but stay in
   * the not-played pool, so they are still played this cycle if they become queueable later.
   *
   * <p>
   * If none of the songs left in the current cycle can be queued, the cycle is completed early and
   * a new one started, so background music keeps playing rather than stalling on them.
   *
   * @param isQueueable tests whether a candidate can be queued right now
   * @return the next core background-music song that can be queued
   * @throws com.djt.jukeanator_engine.domain.backgroundmusic.exception.BackgroundMusicServiceException
   *         if no song in the playlist can be queued right now, even in a new cycle
   */
  SongFileEntity getNextSong(Predicate<SongFileEntity> isQueueable);

  /**
   * @return {@code true} when smart additions are enabled and the current wall-clock hour falls
   *         within the configured smart-additions time window
   */
  boolean isSmartAdditionsActive();

  /**
   * @return the configured smart-additions factor, clamped to the range [1, 10]
   */
  int getSmartAdditionsFactor();

  /**
   * Draws the next smart-addition song from the full smart-additions pool (built up-front from
   * every source song in {@code BackgroundMusic.TXT} — same artist/album, or a popular song from
   * the same genre, relative to whichever source song seeded it).
   *
   * @param coreSong the core background-music song that triggered this smart-addition draw
   * @return the next smart-addition song, or {@code null} if no candidates are available
   */
  SongFileEntity getNextSmartAdditionSong(SongFileEntity coreSong);

  /**
   * Records that {@code song} was just added to the song queue as a background-music or
   * smart-addition pick — updating and persisting {@code timeLastPlayed} for whichever of the two
   * pools it came from (a no-op if it matches neither). This is distinct from
   * {@link #handleSongPlaybackStartedEvent}, which reflects the song actually starting to play and
   * also increments the play count; queueing only stamps {@code timeLastPlayed} so an
   * already-queued-but-not-yet-played song is not immediately re-selected.
   *
   * @param song the background-music or smart-addition song that was just queued
   */
  void markSongQueued(SongFileEntity song);

  /**
   * NOTE: System method, not to be invoked on behalf of a user
   *
   * @param event
   */
  @PublicServiceMethod
  void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event);

  /**
   * NOTE: System method, not to be invoked on behalf of a user. Re-tags in-memory song
   * identifiers after this instance's own location id is corrected post-handshake.
   *
   * @param event
   */
  @PublicServiceMethod
  void handleOwnLocationIdChangedEvent(OwnLocationIdChangedEvent event);

  /**
   * NOTE: System method, not to be invoked on behalf of a user. Keeps track of what is currently
   * sitting in the song queue, so selection can avoid picking a song that's already queued but
   * hasn't played yet.
   *
   * @param event
   */
  @PublicServiceMethod
  void handleSongQueueChangedEvent(SongQueueChangedEvent event);

  /**
   * NOTE: System method, not to be invoked on behalf of a user. Marks a background or
   * smart-addition song as played if, and only if, it is the song that just started playing.
   *
   * @param event
   */
  @PublicServiceMethod
  void handleSongPlaybackStartedEvent(SongPlaybackStartedEvent event);
}
