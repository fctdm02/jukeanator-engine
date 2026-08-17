package com.djt.jukeanator_engine.domain.location.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AbstractRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.location.dto.LocationRootDto;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.location.mapper.LocationMapper;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;

/**
 * @author tmyers
 */
public final class LocationRepositoryFileSystemImpl extends AbstractRepositoryFileSystemImpl
    implements LocationRepository {

  private String filePath;

  public LocationRepositoryFileSystemImpl(String basePath) {
    super(basePath);
    requireNonNull(basePath, "basePath cannot be null");
    new File(basePath).mkdirs();
    this.filePath = basePath + File.separator + LocationRootEntity.LOCATION_LIST_FILENAME;
  }

  @Override
  public void setBasePath(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    super.setBasePath(basePath);
    new File(basePath).mkdirs();
    this.filePath = basePath + File.separator + LocationRootEntity.LOCATION_LIST_FILENAME;
  }

  @Override
  public LocationRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    LocationRootDto dto = readJson(filePath, LocationRootDto.class);
    if (dto == null) {
      throw new EntityDoesNotExistException(
          "Could not read location list from disk with naturalIdentity: " + naturalIdentity
              + " and filePath: " + filePath);
    }

    return LocationMapper.toEntity(dto);
  }

  @Override
  public void storeAggregateRoot(LocationRootEntity root) {

    writeJson(filePath, LocationMapper.toDto(root));
  }

  @Override
  public LocationRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new LocationServiceException("This method is unsupported for the file system implementation");
  }
}
