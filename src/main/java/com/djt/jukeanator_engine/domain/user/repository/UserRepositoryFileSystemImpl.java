package com.djt.jukeanator_engine.domain.user.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AbstractRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.user.dto.UserRootDto;
import com.djt.jukeanator_engine.domain.user.mapper.UserMapper;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;

/**
 * @author tmyers
 */
public final class UserRepositoryFileSystemImpl extends AbstractRepositoryFileSystemImpl
    implements UserRepository {

  private String filePath;

  public UserRepositoryFileSystemImpl(String basePath) {
    super(basePath);
    requireNonNull(basePath, "basePath cannot be null");
    this.filePath = basePath + File.separator + UserRootEntity.USER_LIST_FILENAME;
  }

  @Override
  public void setBasePath(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    super.setBasePath(basePath);
    this.filePath = basePath + File.separator + UserRootEntity.USER_LIST_FILENAME;
  }

  @Override
  public UserRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    // TODO: How to reconcile naturalIdentity with filePath?
    UserRootDto dto = readJson(filePath, UserRootDto.class);
    if (dto == null) {
      throw new EntityDoesNotExistException(
          "Could not read user list from disk with naturalIdentity: " + naturalIdentity
              + " and filePath: " + filePath);
    }

    return UserMapper.toEntity(dto);
  }

  @Override
  public void storeAggregateRoot(UserRootEntity root) {

    writeJson(filePath, UserMapper.toDto(root));
  }

  @Override
  public UserRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new SongLibraryServiceException("This method is unsupported for the file system implementation");
  }
}
