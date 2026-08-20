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

  // Every method below takes locationId as its first parameter -- standalone/slave instances have
  // exactly one location (their own) and always execute locally; the master instance forwards to
  // whichever location owns the request over the existing SlaveCommandGateway/ws-slave channel
  // (see SongPlayerServiceMasterImpl -- unlike SongQueueServiceImpl, the real, hardware-backed
  // SongPlayerServiceImpl is never constructed on master at all, since its constructor has real
  // side effects (spinning up a VLC/Winamp process); the two implementations stay separate
  // classes, selected by the same NotMasterModeCondition/app.mode=master conditional pattern
  // already used for repository-type selection elsewhere).
  /**
   *
   * @return
   */
  @PublicServiceMethod
  SongDto getNowPlayingSong(Integer locationId);

  /**
   *
   * @return
   */
  @PublicServiceMethod
  SongPlaybackStatusDto getPlaybackStatus(Integer locationId);

  /**
   *
   */
  void playNextTrack(Integer locationId);

  /**
   *
   */
  void pause(Integer locationId);

  /**
   *
   */
  void stop(Integer locationId);

  /**
   * Prevents the player from dequeuing and playing any further songs. Any song currently playing is
   * stopped immediately. Has no effect if the queue is already locked.
   */
  void lockQueue(Integer locationId);

  /**
   * Releases a previous {@link #lockQueue(Integer)} and resumes normal queue processing. Has no
   * effect if the queue is not currently locked.
   */
  void unlockQueue(Integer locationId);

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
