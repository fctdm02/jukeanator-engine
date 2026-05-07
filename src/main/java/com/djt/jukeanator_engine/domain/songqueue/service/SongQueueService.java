package com.djt.jukeanator_engine.domain.songqueue.service;

import java.util.List;
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
   * @param albumId
   * @param songId
   * @param priority
   * @return
   */
  Integer addSongToQueue(Integer albumId, Integer songId, Integer priority);
  
  /**
   * 
   * @return
   */
  SongQueueEntryDto getFirstEntryInSongQueue();
}
