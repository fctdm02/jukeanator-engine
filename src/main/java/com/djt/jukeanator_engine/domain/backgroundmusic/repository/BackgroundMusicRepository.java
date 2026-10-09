package com.djt.jukeanator_engine.domain.backgroundmusic.repository;

import java.util.List;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

/**
 * @author tmyers
 */
public interface BackgroundMusicRepository {

  List<BackgroundMusicSongEntity> loadAll();

  void storeAll(List<BackgroundMusicSongEntity> songs);

  /**
   * Persists just {@code song}'s play stats ({@code timeLastPlayed}/{@code numberOfPlays}) -- a
   * targeted update for the common case where only one song's play state actually changed (see
   * {@code BackgroundMusicServiceImpl.handleSongPlaybackStartedEvent}), instead of rewriting
   * every row via {@link #storeAll(List)}. {@code allSongs} is the full in-memory list; a
   * JPA-backed implementation ignores it and issues a single {@code UPDATE}, while the
   * file-system implementation has no way to update one row within its JSON file and falls back
   * to persisting {@code allSongs} in full.
   *
   * @param allSongs the full in-memory background-music song list
   * @param song the song (already mutated in-memory, e.g. via {@code markPlayed()}) whose
   *        {@code timeLastPlayed}/{@code numberOfPlays} should be persisted
   */
  void updatePlayStats(List<BackgroundMusicSongEntity> allSongs, BackgroundMusicSongEntity song)
      throws EntityDoesNotExistException;

  /**
   * Resets every song's {@code timeLastPlayed} back to {@code null}, except for songs whose
   * in-memory {@code timeLastPlayed} the caller deliberately left set (songs still sitting in the
   * song queue) -- a targeted bulk update for when a full played/not-played cycle completes (see
   * {@code BackgroundMusicServiceImpl.startNewBackgroundCycle}), instead of rewriting every row
   * via {@link #storeAll(List)}. {@code allSongs} is the full in-memory list, already reset
   * in-memory by the caller; a JPA-backed implementation issues a single bulk {@code UPDATE}
   * followed by a targeted update for each song still stamped in {@code allSongs}, while the
   * file-system implementation falls back to persisting {@code allSongs} in full.
   *
   * @param allSongs the full in-memory background-music song list, already reset in-memory
   */
  void resetAllPlayedTimestamps(List<BackgroundMusicSongEntity> allSongs);
}
