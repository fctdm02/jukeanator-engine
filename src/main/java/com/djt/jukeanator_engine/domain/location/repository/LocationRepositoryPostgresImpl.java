package com.djt.jukeanator_engine.domain.location.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;

public class LocationRepositoryPostgresImpl implements LocationRepository {

  @Override
  public LocationRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {
    throw new LocationServiceException("Not implemented yet!");
  }

  @Override
  public LocationRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {
    throw new LocationServiceException("Not implemented yet!");
  }

  @Override
  public void storeAggregateRoot(LocationRootEntity aggregateRoot) {
    throw new LocationServiceException("Not implemented yet!");
  }
}
