package com.djt.jukeanator_engine.domain.location.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;

/**
 * @author tmyers
 */
public final class LocationRepositoryFileSystemImpl implements LocationRepository {

  private LocationRootObjectPersistor objectPersistor;
  private String filePath;

  public LocationRepositoryFileSystemImpl(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    new File(basePath).mkdirs();
    filePath = basePath + File.separator + LocationRootEntity.LOCATION_LIST_FILENAME;
    this.objectPersistor = new LocationRootObjectPersistor();
  }

  @Override
  public LocationRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    try {
      return this.objectPersistor.loadLocationsFromDisk(filePath);
    } catch (ClassNotFoundException | IOException e) {
      throw new EntityDoesNotExistException(
          "Could not read location list from disk with naturalIdentity: " + naturalIdentity
              + " and filePath: " + filePath);
    }
  }

  @Override
  public void storeAggregateRoot(LocationRootEntity root) {

    try {
      this.objectPersistor.writeLocationsToDisk(root, filePath);
    } catch (IOException ioe) {
      throw new LocationServiceException("Could not write location list to disk with "
          + "naturalIdentity: " + root.getNaturalIdentity() + " and filePath: " + filePath);
    }
  }

  @Override
  public LocationRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new LocationServiceException("This method is unsupported for the file system implementation");
  }
}
