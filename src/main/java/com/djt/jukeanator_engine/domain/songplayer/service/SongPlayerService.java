package com.djt.jukeanator_engine.domain.songplayer.service;

import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;

/**
 * @author tmyers
 */
public interface SongPlayerService {

  /**
   * 
   * @return
   */
  NowPlayingSongDto getNowPlayingSong();
}
