package com.djt.jukeanator_engine.domain.user.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;

/**
 * @author tmyers
 */
public final class UserRepositoryFileSystemImpl implements UserRepository {

  private UserRootObjectPersistor objectPersistor;
  private String filePath;

  public UserRepositoryFileSystemImpl(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    filePath = basePath + File.separator + UserRootEntity.USER_LIST_FILENAME;
    this.objectPersistor = new UserRootObjectPersistor();
  }

  public void setBasePath(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    filePath = basePath + File.separator + UserRootEntity.USER_LIST_FILENAME;
  }

  @Override
  public UserRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    try {

      // TODO: How to reconcile naturalIdentity with filePath?
      return this.objectPersistor.loadUserListFromDisk(filePath);

    } catch (ClassNotFoundException | IOException e) {
      throw new EntityDoesNotExistException(
          "Could not read user list from disk with naturalIdentity: " + naturalIdentity
              + " and filePath: " + filePath);
    }
  }

  @Override
  public void storeAggregateRoot(UserRootEntity root) {

    try {
      this.objectPersistor.writeUserListToDisk(root, filePath);
    } catch (IOException ioe) {
      throw new SongLibraryServiceException("Could not write user list to disk with naturalIdentity: "
          + root.getNaturalIdentity() + " and filePath: " + filePath);
    }
  }

  @Override
  public UserRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new SongLibraryServiceException("This method is unsupported for the file system implementation");
  }
}
