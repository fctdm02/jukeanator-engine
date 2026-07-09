package com.djt.jukeanator_engine.domain.backgroundmusic.repository;

import java.util.List;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;

/**
 * @author tmyers
 */
public interface BackgroundMusicRepository {

  List<BackgroundMusicSongEntity> loadAll();

  void storeAll(List<BackgroundMusicSongEntity> songs);
}
