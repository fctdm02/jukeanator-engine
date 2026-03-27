package com.djt.jukeanator_engine.domain.common.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

public interface AggregateRootRepository<T> {

  /**
   * Loads the aggregate root by natural identity
   * 
   * @param naturalIdentity The aggregate root to load
   * 
   * @return The aggregate root identified by <code>naturalIdentity</code>
   * 
   * @throws EntityDoesNotExistException If the specified aggregate root does not exist
   */
  T loadAggregateRoot(String naturalIdentity) throws EntityDoesNotExistException;
  
  /**
   * Loads the aggregate root by persistent identity
   * 
   * @param persistentIdentity The aggregate root to load
   * 
   * @return The aggregate root identified by <code>persistentIdentity</code>
   * 
   * @throws EntityDoesNotExistException If the specified aggregate root does not exist
   */
  T loadAggregateRoot(int persistentIdentity) throws EntityDoesNotExistException;
  
  /**
   * 
   * @param aggregateRoot The aggregate root to store
   */
  void storeAggregateRoot(T aggregateRoot);  
}
