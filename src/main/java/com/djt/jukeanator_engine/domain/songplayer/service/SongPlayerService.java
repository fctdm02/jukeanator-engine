package com.djt.jukeanator_engine.domain.songplayer.service;

import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;

/**
 * @author tmyers
 */
public interface SongPlayerService {

  /**
   * 
   * @return
   */
  NowPlayingSongDto getNowPlayingSong();
  
  /**
   * 
   * @return
   */
  SongPlaybackStatusDto getPlaybackStatus();
  
  /**
   * 
   */
  void playNextTrack();

  /**
   * 
   */
  void pause();

  /**
   * 
   */
  void stop();  
}
