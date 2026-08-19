package com.djt.jukeanator_engine.domain.songlibrary.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AggregateRootRepository;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public interface SongLibraryRepository extends AggregateRootRepository<RootFolderEntity> {

  // Fixed filename for the filesystem-backed repository -- see
  // SongLibraryRepositoryFileSystemImpl, which is basePath-scoped (one repository instance per
  // deployment) so there's no need for the filename to vary by location.
  String SONG_LIBRARY_FILENAME = "SongLibrary.oos";
  
  /**
   * 
   * @throws EntityDoesNotExistException
   */
  void storeSongLibraryAsync() throws EntityDoesNotExistException;
}
