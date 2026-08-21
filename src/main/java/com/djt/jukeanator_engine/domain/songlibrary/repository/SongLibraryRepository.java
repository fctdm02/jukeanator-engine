package com.djt.jukeanator_engine.domain.songlibrary.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AggregateRootRepository;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public interface SongLibraryRepository extends AggregateRootRepository<RootFolderEntity> {

  // The persisted filename is derived from the location's live display name (see
  // LocationMetaDataFileEntity), sanitized and suffixed with this extension -- not fixed.
  // SongLibraryRepositoryFileSystemImpl reconciles this at load time: if the expected-named file
  // isn't found, it looks for whatever other .oos file exists under basePath and renames it to
  // match, rather than silently starting a second file. See docs/multi-tenant-mode.md, Item 2,
  // for why an earlier version of this used a fixed filename instead, and why reconciling at
  // load time is what makes it safe to derive the filename from an editable value again.
  String OOS_FILE_EXTENSION = ".oos";

  /**
   * The absolute path of the {@code .oos} file most recently loaded from or stored to, or
   * {@code null} if not applicable (e.g. non-filesystem-backed implementations).
   */
  default String getResolvedFilePath() {
    return null;
  }

  /**
   *
   * @throws EntityDoesNotExistException
   */
  void storeSongLibraryAsync() throws EntityDoesNotExistException;

  /**
   * 
   * @param root
   * @param locationId
   * @param albumId
   * @param songId
   * @param numPlays
   * @return
   * @throws EntityDoesNotExistException
   */
  Integer updateNumPlaysForSong(
      RootFolderEntity root, 
      Integer locationId, 
      Integer albumId,
      Integer songId, 
      Integer numPlays) throws EntityDoesNotExistException;
}
