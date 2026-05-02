package com.djt.jukeanator_engine.domain.songqueue.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;

/**
 * @author tmyers
 */
public final class SongQueueRepositoryPostgresImpl implements SongQueueRepository {
  
  public SongQueueRepositoryPostgresImpl() {
  }
  
  @Override
  public SongQueueRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    // TODO: TDM:
    return new SongQueueRootEntity();
    //throw new SongLibraryException("Not implemented yet!");
  }
  
  @Override
  public SongQueueRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new SongLibraryException("Not implemented yet!");
  }
  
  @Override
  public void storeAggregateRoot(SongQueueRootEntity root) {

    throw new SongLibraryException("Not implemented yet!");
  }
}
