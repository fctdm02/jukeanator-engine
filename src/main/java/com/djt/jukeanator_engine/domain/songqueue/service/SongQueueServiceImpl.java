package com.djt.jukeanator_engine.domain.songqueue.service;

import static java.util.Objects.requireNonNull;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepository;

/**
 * @author tmyers
 */
public final class SongQueueServiceImpl implements SongQueueService {

  private static final Logger log = LoggerFactory.getLogger(SongQueueServiceImpl.class);
  
  private SongQueueRepository songQueueRepository;
  private SongQueueRootEntity songQueue;
  
  public SongQueueServiceImpl(SongQueueRepository songQueueRepository) {

      requireNonNull(songQueueRepository, "songQueueRepository cannot be null");
      this.songQueueRepository = songQueueRepository;
      
      try {
        this.songQueue = this.songQueueRepository.loadAggregateRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
      } catch (EntityDoesNotExistException e) {
        this.songQueue = new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
      }
      
      log.info("Using song queue: " + this.songQueue);
  }
  
  // Service methods
  @Override
  public List<SongQueueEntryEntity> getSongs() {
    
    return songQueue.getSongs();
  }
  
  @Override
  public int addSongToQueue(SongFileEntity song, Integer priority) {
    
    return songQueue.addSongToQueue(song, priority);
  }  

  // Repository methods
  @Override
  public SongQueueRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    return this.songQueueRepository.loadAggregateRoot(naturalIdentity);
  }
  
  @Override
  public SongQueueRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    return this.songQueueRepository.loadAggregateRoot(persistentIdentity);
  }

  @Override
  public void storeAggregateRoot(SongQueueRootEntity root) {

    this.songQueueRepository.storeAggregateRoot(root);
  }

  // Command methods
  @Override
  public CommandResponse processCommand(CommandRequest commandRequest) {

    throw new SongLibraryException("Not implemented yet!");
  }

  // Query methods
  @Override
  public QueryResponse<QueryRequest, QueryResponseItem> processQuery(QueryRequest queryRequest) {

    throw new SongLibraryException("Not implemented yet!");
  }
}