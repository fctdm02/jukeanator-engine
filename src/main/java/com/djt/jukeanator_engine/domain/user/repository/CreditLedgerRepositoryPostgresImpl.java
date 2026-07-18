package com.djt.jukeanator_engine.domain.user.repository;

import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.CreditLedgerRootEntity;

public class CreditLedgerRepositoryPostgresImpl implements CreditLedgerRepository {

  @Override
  public CreditLedgerRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {
    throw new UserServiceException("Not implemented yet!");
  }

  @Override
  public CreditLedgerRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {
    throw new UserServiceException("Not implemented yet!");
  }

  @Override
  public void storeAggregateRoot(CreditLedgerRootEntity aggregateRoot) {
    throw new UserServiceException("Not implemented yet!");
  }
}
