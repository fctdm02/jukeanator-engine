package com.djt.jukeanator_engine.domain.songlibrary.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public final class SongLibraryRepositoryPostgresImpl implements SongLibraryRepository {
  
  public SongLibraryRepositoryPostgresImpl() {
  }
  
  @Override
  public RootFolderEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    // TODO: TDM:
    return new RootFolderEntity();
    //throw new SongLibraryException("Not implemented yet!");
  }
  
  @Override
  public RootFolderEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new SongLibraryException("Not implemented yet!");
  }
  
  @Override
  public void storeAggregateRoot(RootFolderEntity rootFolder) {

    throw new SongLibraryException("Not implemented yet!");
  }
}
