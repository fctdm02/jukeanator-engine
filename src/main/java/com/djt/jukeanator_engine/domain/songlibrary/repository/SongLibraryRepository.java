package com.djt.jukeanator_engine.domain.songlibrary.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AggregateRootRepository;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public interface SongLibraryRepository extends AggregateRootRepository<RootFolderEntity> {

  String DEFAULT_SONG_LIBRARY_LOCATION_NAME = "JukeANator";
  
  /**
   * 
   * @throws EntityDoesNotExistException
   */
  void storeSongLibraryAsync() throws EntityDoesNotExistException;
}
