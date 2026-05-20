package com.djt.jukeanator_engine.domain.songlibrary.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AggregateRootRepository;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public interface SongLibraryRepository extends AggregateRootRepository<RootFolderEntity> {
 
  /**
   * 
   * @param albumId
   * @param songId
   * @return
   * @throws EntityDoesNotExistException
   */
  Integer incrementNumPlaysForSong(Integer albumId, Integer songId) throws EntityDoesNotExistException;
}
