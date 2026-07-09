package com.djt.jukeanator_engine.domain.backgroundmusic.service;

import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
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
  SongFileEntity getNextSong();

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
   * NOTE: System method, not to be invoked on behalf of a user
   *
   * @param event
   */
  @PublicServiceMethod
  void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event);

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
