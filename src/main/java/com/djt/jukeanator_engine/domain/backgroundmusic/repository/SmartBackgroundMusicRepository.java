package com.djt.jukeanator_engine.domain.backgroundmusic.repository;

import java.util.List;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;

/**
 * @author tmyers
 */
public interface SmartBackgroundMusicRepository {

  List<SmartBackgroundMusicSongEntity> loadAll();

  void storeAll(List<SmartBackgroundMusicSongEntity> songs);
}
