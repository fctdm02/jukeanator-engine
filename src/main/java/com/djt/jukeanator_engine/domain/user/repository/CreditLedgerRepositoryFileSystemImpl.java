package com.djt.jukeanator_engine.domain.user.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.CreditLedgerRootEntity;

/**
 * @author tmyers
 */
public final class CreditLedgerRepositoryFileSystemImpl implements CreditLedgerRepository {

  private CreditLedgerRootObjectPersistor objectPersistor;
  private String filePath;

  public CreditLedgerRepositoryFileSystemImpl(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    filePath = basePath + File.separator + CreditLedgerRootEntity.CREDIT_LEDGER_FILENAME;
    this.objectPersistor = new CreditLedgerRootObjectPersistor();
  }

  @Override
  public CreditLedgerRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    try {
      return this.objectPersistor.loadLedgerFromDisk(filePath);
    } catch (ClassNotFoundException | IOException e) {
      throw new EntityDoesNotExistException(
          "Could not read credit ledger from disk with naturalIdentity: " + naturalIdentity
              + " and filePath: " + filePath);
    }
  }

  @Override
  public void storeAggregateRoot(CreditLedgerRootEntity root) {

    try {
      this.objectPersistor.writeLedgerToDisk(root, filePath);
    } catch (IOException ioe) {
      throw new UserServiceException("Could not write credit ledger to disk with naturalIdentity: "
          + root.getNaturalIdentity() + " and filePath: " + filePath);
    }
  }

  @Override
  public CreditLedgerRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new UserServiceException("This method is unsupported for the file system implementation");
  }
}
