package com.djt.jukeanator_engine.domain.location.repository;

import com.djt.jukeanator_engine.domain.common.repository.AggregateRootRepository;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;

/**
 * @author tmyers
 */
public interface LocationRepository extends AggregateRootRepository<LocationRootEntity> {

  /**
   * Allocates the next id from the shared persistent-identity sequence for a JPA-backed store, so
   * a location id assigned at registration time can never later collide with one Hibernate
   * generates itself for some other entity. Returns {@code null} for the filesystem-backed
   * implementation, which has no such sequence -- {@code LocationServiceImpl.registerLocation}
   * falls back to count+1 in that case.
   */
  Integer nextPersistentIdentity();
}
