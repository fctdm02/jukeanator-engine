package com.djt.jukeanator_engine.domain.songqueue.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

/**
 * @author tmyers
 */
public interface SongQueueService {

  /**
   * 
   * @return
   */
  List<SongQueueEntryDto> getQueuedSongs();

  /**
   * @param addSongToQueueRequest
   * @return
   */
  SongQueueEntryDto addSongToQueue(AddSongToQueueRequest addSongToQueueRequest);

  /**
   * 
   * @param addAlbumToQueueRequest
   * @return
   */
  List<SongQueueEntryDto> addAlbumToQueue(AddAlbumToQueueRequest addAlbumToQueueRequest);
  
  /**
   * 
   * @param addMultipleSongsToQueueRequest
   * @return
   */
  List<SongQueueEntryDto> addMultipleSongsToQueue(AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest);
  
  /**
   * @return
   */
  Integer flushQueue();

  /**
   * @return
   */
  Integer randomizeQueue();
  
  /**
   * 
   * @return
   */
  SongQueueEntryDto dequeueNextSong();
}
