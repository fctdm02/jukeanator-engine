package com.djt.jukeanator_engine.domain.financialledger.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.util.stream.Stream;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AbstractRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.financialledger.dto.FinancialLedgerRootDto;
import com.djt.jukeanator_engine.domain.financialledger.exception.FinancialLedgerException;
import com.djt.jukeanator_engine.domain.financialledger.mapper.FinancialLedgerMapper;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;

public final class FinancialLedgerRepositoryFileSystemImpl extends AbstractRepositoryFileSystemImpl
    implements FinancialLedgerRepository {

  private String filePath;

  public FinancialLedgerRepositoryFileSystemImpl(String basePath) {
    super(basePath);
    requireNonNull(basePath, "basePath cannot be null");
    new File(basePath).mkdirs();
    this.filePath = basePath + File.separator + FinancialLedgerRootEntity.FINANCIAL_LEDGER_FILENAME;
  }

  @Override
  public void setBasePath(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    super.setBasePath(basePath);
    new File(basePath).mkdirs();
    this.filePath = basePath + File.separator + FinancialLedgerRootEntity.FINANCIAL_LEDGER_FILENAME;
  }

  @Override
  public FinancialLedgerRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    FinancialLedgerRootDto dto = readJson(filePath, FinancialLedgerRootDto.class);
    if (dto == null) {
      throw new EntityDoesNotExistException(
          "Could not read financial ledger from disk with naturalIdentity: " + naturalIdentity
              + " and filePath: " + filePath);
    }

    FinancialLedgerRootEntity root = FinancialLedgerMapper.toEntity(dto);
    seedIdentityCounter(root);
    return root;
  }

  @Override
  public void storeAggregateRoot(FinancialLedgerRootEntity root) {

    writeJson(filePath, FinancialLedgerMapper.toDto(root));
  }

  @Override
  public FinancialLedgerRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new FinancialLedgerException(
        "This method is unsupported for the file system implementation");
  }

  @Override
  public Integer nextPersistentIdentity() {
    return getNextPersistentIdentityValue();
  }

  private void seedIdentityCounter(FinancialLedgerRootEntity root) {

    seedNextPersistentIdentityFrom(Stream.concat(
        root.getSplitPeriods().stream().map(p -> p.getPersistentIdentity()),
        root.getLocalCreditTransactions().stream().map(t -> t.getPersistentIdentity())));
  }
}
