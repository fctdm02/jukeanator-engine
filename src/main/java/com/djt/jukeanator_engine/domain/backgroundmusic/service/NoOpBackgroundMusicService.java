package com.djt.jukeanator_engine.domain.backgroundmusic.service;

import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueChangedEvent;

/**
 * Master-only stand-in: the master instance constructs a {@code SongQueueServiceImpl} (so it can
 * take the locationId-dispatch branch that forwards to a slave over {@code SlaveCommandGateway}),
 * but master has no local audio hardware, no background-music playlist, and its own queue state
 * is never really used -- {@code SongQueueServiceImpl#autoPopulateQueue()}'s only caller path on
 * master is inert startup bookkeeping. {@link #isEnabled()} returning {@code false} makes that
 * method a no-op, so none of the other methods here are ever actually invoked.
 *
 * @author tmyers
 */
public class NoOpBackgroundMusicService implements BackgroundMusicService {

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public SongFileEntity getNextSong() {
    throw new UnsupportedOperationException("Background music is not available on the master instance");
  }

  @Override
  public boolean isSmartAdditionsActive() {
    return false;
  }

  @Override
  public int getSmartAdditionsFactor() {
    return 0;
  }

  @Override
  public SongFileEntity getNextSmartAdditionSong(SongFileEntity coreSong) {
    return null;
  }

  @Override
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {
    // no-op
  }

  @Override
  public void handleSongQueueChangedEvent(SongQueueChangedEvent event) {
    // no-op
  }

  @Override
  public void handleSongPlaybackStartedEvent(SongPlaybackStartedEvent event) {
    // no-op
  }
}
