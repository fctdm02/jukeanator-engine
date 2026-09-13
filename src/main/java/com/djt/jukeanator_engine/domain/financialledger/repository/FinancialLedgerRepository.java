package com.djt.jukeanator_engine.domain.financialledger.repository;

import com.djt.jukeanator_engine.domain.common.repository.AggregateRootRepository;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;

public interface FinancialLedgerRepository
    extends AggregateRootRepository<FinancialLedgerRootEntity> {

  /**
   * Mints the next unique id for a new {@code JukeboxSplitPeriodEntity} or
   * {@code LocalCreditTransactionEntity} -- shared across both, since they live in separate
   * tables and can never collide. The filesystem implementation seeds a local counter from
   * whatever's already on disk; the JPA implementation pre-allocates from the same
   * {@code persistent_identity_seq} every {@code AbstractPersistentEntity} shares.
   */
  Integer nextPersistentIdentity();
}
