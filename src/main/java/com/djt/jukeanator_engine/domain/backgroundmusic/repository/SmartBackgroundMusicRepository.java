package com.djt.jukeanator_engine.domain.backgroundmusic.repository;

import java.util.List;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;

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
}
