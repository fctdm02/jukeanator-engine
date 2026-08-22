package com.djt.jukeanator_engine.domain.backgroundmusic.repository;

import java.util.List;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

/**
 * @author tmyers
 */
public interface SmartBackgroundMusicRepository {

  List<SmartBackgroundMusicSongEntity> loadAll();

  void storeAll(List<SmartBackgroundMusicSongEntity> songs);

  /**
   * @return {@code true} when the underlying persisted smart-background-music file already
   *         exists. Used to decide whether the smart-additions pool needs to be fully generated
   *         from scratch (first run) or simply loaded/refreshed.
   */
  boolean exists();

  /**
   * Persists just {@code song}'s play stats ({@code timeLastPlayed}/{@code numberOfPlays}) -- a
   * targeted update for the common case where only one song's play state actually changed (see
   * {@code BackgroundMusicServiceImpl.handleSongPlaybackStartedEvent}), instead of rewriting
   * every row via {@link #storeAll(List)}. {@code smartPool} is the full in-memory list; a
   * JPA-backed implementation ignores it and issues a single {@code UPDATE}, while the
   * file-system implementation has no way to update one row within its JSON file and falls back
   * to persisting {@code smartPool} in full.
   *
   * @param smartPool the full in-memory smart-addition song list
   * @param song the song (already mutated in-memory, e.g. via {@code markPlayed()}) whose
   *        {@code timeLastPlayed}/{@code numberOfPlays} should be persisted
   */
  void updatePlayStats(List<SmartBackgroundMusicSongEntity> smartPool,
      SmartBackgroundMusicSongEntity song) throws EntityDoesNotExistException;

  /**
   * Resets every song's {@code timeLastPlayed} back to {@code null} -- a targeted bulk update for
   * when a full played/not-played cycle completes (see {@code
   * BackgroundMusicServiceImpl.getNextSmartAdditionSong}), instead of rewriting every row via
   * {@link #storeAll(List)}. {@code smartPool} is the full in-memory list, whose entries the
   * caller has already nulled out in-memory; a JPA-backed implementation ignores it and issues a
   * single bulk {@code UPDATE}, while the file-system implementation falls back to persisting
   * {@code smartPool} in full.
   *
   * @param smartPool the full in-memory smart-addition song list, already reset in-memory
   */
  void resetAllPlayedTimestamps(List<SmartBackgroundMusicSongEntity> smartPool);
}
