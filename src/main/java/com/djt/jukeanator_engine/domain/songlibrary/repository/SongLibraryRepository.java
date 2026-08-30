package com.djt.jukeanator_engine.domain.songlibrary.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AggregateRootRepository;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public interface SongLibraryRepository extends AggregateRootRepository<RootFolderEntity> {

  // The persisted filename is derived from the location's live display name (see
  // RootFolderEntity#getLocationName), sanitized and suffixed with this extension -- not fixed.
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
   * Renames the persisted {@code .oos} file from {@code oldLocationName}'s expected filename to
   * {@code newLocationName}'s, if it exists and the name actually changed -- called right after
   * {@code LocationService#updateOwnLocationInfo} edits this instance's own location's name (e.g.
   * via the Admin Panel's "Edit Location Info" dialog), so the file takes its new name
   * immediately instead of waiting for the next restart's load-time reconciliation (see {@link
   * #OOS_FILE_EXTENSION}'s javadoc). A no-op by default -- non-filesystem-backed implementations
   * (e.g. JPA) have no per-location file to rename.
   */
  default void renameLocationLibraryFile(String oldLocationName, String newLocationName) {
    // no-op by default
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
