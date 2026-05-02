package com.djt.jukeanator_engine.domain.songqueue.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;

/**
 * @author tmyers
 */
public interface SongQueueService extends AggregateRootService<SongQueueRootEntity> {

  /**
   * 
   * @return
   */
  List<SongQueueEntryEntity> getQueuedSongs();

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
  SongQueueEntryEntity getFirstEntryInSongQueue();
}
