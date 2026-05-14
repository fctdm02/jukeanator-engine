package com.djt.jukeanator_engine.domain.songplayer.service.utils;

import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;

public interface Player {

  boolean playSongMedia(String songPath);

  SongPlayerStatus getStatus();

  long getElapsedSeconds();

  long getTotalLengthSeconds();

  void pause();

  void stop();

  void release();
}
