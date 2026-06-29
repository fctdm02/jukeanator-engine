package com.djt.jukeanator_engine.domain.user.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;

/**
 * @author tmyers
 */
public final class UserRepositoryPostgresImpl implements UserRepository {

  public UserRepositoryPostgresImpl() {}

  @Override
  public UserRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    // TODO: TDM:
    return new UserRootEntity();
    // throw new SongLibraryServiceException("Not implemented yet!");
  }

  @Override
  public UserRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new SongLibraryServiceException("Not implemented yet!");
  }

  @Override
  public void storeAggregateRoot(UserRootEntity root) {

    throw new SongLibraryServiceException("Not implemented yet!");
  }
}
