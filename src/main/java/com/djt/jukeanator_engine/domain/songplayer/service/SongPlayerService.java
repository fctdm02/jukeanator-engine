package com.djt.jukeanator_engine.domain.songplayer.service;

import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

/**
 * @author tmyers
 */
public interface SongPlayerService {

  /**
   * 
   * @return
   */
  @PublicServiceMethod
  SongDto getNowPlayingSong();

  /**
   * 
   * @return
   */
  @PublicServiceMethod
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

  /**
   * Prevents the player from dequeuing and playing any further songs. Any song currently playing is
   * stopped immediately. Has no effect if the queue is already locked.
   */
  void lockQueue();

  /**
   * Releases a previous {@link #lockQueue()} and resumes normal queue processing. Has no effect if
   * the queue is not currently locked.
   */
  void unlockQueue();

  /**
   * 
   * @param event
   */
  @PublicServiceMethod
  void handleSongAddedToQueueEvent(SongAddedToQueueEvent event);

  /**
   * 
   * @param event
   */
  @PublicServiceMethod
  void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event);
}
