package com.djt.jukeanator_engine.domain.financialledger.repository;

import static java.util.Objects.requireNonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;

/**
 * JPA/Hibernate-backed implementation of {@link FinancialLedgerRepository}, following the same
 * shape as {@code LocationRepositoryJpaImpl}: {@link FinancialLedgerRootEntity} is not itself
 * JPA-mapped (no {@code financial_ledger_root} table) -- it's an in-memory aggregate assembled
 * directly from {@link JukeboxSplitPeriodEntity} and {@link LocalCreditTransactionEntity} rows.
 * Neither child type is ever deleted once created, so unlike {@code LocationRepositoryJpaImpl}
 * there is no orphan-removal step -- only merge (existing rows, e.g. finalizing a period) and
 * insert (new rows).
 */
public final class FinancialLedgerRepositoryJpaImpl implements FinancialLedgerRepository {

  private static final Integer FINANCIAL_LEDGER_ROOT_ID = Integer.valueOf(0);

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  public FinancialLedgerRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    requireNonNull(entityManagerFactory, "entityManagerFactory cannot be null");
    requireNonNull(transactionManager, "transactionManager cannot be null");

    this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public FinancialLedgerRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    // naturalIdentity is unused: there is exactly one (in-memory) FinancialLedgerRootEntity, same
    // as FinancialLedgerRepositoryFileSystemImpl ignoring naturalIdentity in favor of its one file.
    return loadOrCreateRoot();
  }

  @Override
  public FinancialLedgerRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    if (!FINANCIAL_LEDGER_ROOT_ID.equals(Integer.valueOf(persistentIdentity))) {
      throw new EntityDoesNotExistException(
          "FinancialLedgerRootEntity is a singleton aggregate; no root exists with "
              + "persistentIdentity: [" + persistentIdentity + "].");
    }
    return loadOrCreateRoot();
  }

  @Override
  public void storeAggregateRoot(FinancialLedgerRootEntity root) {

    requireNonNull(root, "root cannot be null");

    transactionTemplate.executeWithoutResult(status -> {

      Set<Integer> persistedPeriodIds = new HashSet<>(entityManager
          .createQuery("select p.persistentIdentity from JukeboxSplitPeriodEntity p", Integer.class)
          .getResultList());

      for (JukeboxSplitPeriodEntity period : root.getSplitPeriods()) {
        if (persistedPeriodIds.contains(period.getPersistentIdentity())) {
          entityManager.merge(period);
        } else {
          insertNewPeriod(period);
        }
      }

      Set<Integer> persistedTransactionIds = new HashSet<>(entityManager
          .createQuery("select t.persistentIdentity from LocalCreditTransactionEntity t",
              Integer.class)
          .getResultList());

      for (LocalCreditTransactionEntity transaction : root.getLocalCreditTransactions()) {
        if (persistedTransactionIds.contains(transaction.getPersistentIdentity())) {
          entityManager.merge(transaction);
        } else {
          insertNewTransaction(transaction);
        }
      }
    });
  }

  @Override
  public Integer nextPersistentIdentity() {

    // Same read-then-increment allocation against persistent_identity_seq as
    // LocationRepositoryJpaImpl.nextPersistentIdentity() -- see its javadoc for why this bypasses
    // the entity's own @GeneratedValue(SEQUENCE) generator.
    return transactionTemplate.execute(status -> {

      entityManager.createNativeQuery("update persistent_identity_seq set next_val = next_val + 1")
          .executeUpdate();
      Number nextVal = (Number) entityManager
          .createNativeQuery("select next_val from persistent_identity_seq").getSingleResult();
      return Integer.valueOf(nextVal.intValue() - 1);
    });
  }

  private void insertNewPeriod(JukeboxSplitPeriodEntity period) {

    entityManager.createNativeQuery("insert into jukebox_split_period "
        + "(persistent_identity, version, start_date, end_date, split_percentage_to_owner, "
        + "cash_total, card_total, mobile_total, total_earned, amount_due_owner, "
        + "amount_due_operator) "
        + "values (:id, :version, :startDate, :endDate, :splitPercentageToOwner, :cashTotal, "
        + ":cardTotal, :mobileTotal, :totalEarned, :amountDueOwner, :amountDueOperator)")
        .setParameter("id", period.getPersistentIdentity())
        .setParameter("version", period.getVersion())
        .setParameter("startDate", period.getStartDate())
        .setParameter("endDate", period.getEndDate())
        .setParameter("splitPercentageToOwner", period.getSplitPercentageToOwner())
        .setParameter("cashTotal", period.getCashTotal())
        .setParameter("cardTotal", period.getCardTotal())
        .setParameter("mobileTotal", period.getMobileTotal())
        .setParameter("totalEarned", period.getTotalEarned())
        .setParameter("amountDueOwner", period.getAmountDueOwner())
        .setParameter("amountDueOperator", period.getAmountDueOperator())
        .executeUpdate();
  }

  private void insertNewTransaction(LocalCreditTransactionEntity transaction) {

    entityManager.createNativeQuery("insert into local_credit_transactions "
        + "(persistent_identity, version, source, amount_dollars, timestamp, location_id) "
        + "values (:id, :version, :source, :amountDollars, :timestamp, :locationId)")
        .setParameter("id", transaction.getPersistentIdentity())
        .setParameter("version", transaction.getVersion())
        .setParameter("source", transaction.getSource().name())
        .setParameter("amountDollars", transaction.getAmountDollars())
        .setParameter("timestamp", transaction.getTimestamp())
        .setParameter("locationId", transaction.getLocationId())
        .executeUpdate();
  }

  private FinancialLedgerRootEntity loadOrCreateRoot() {

    return transactionTemplate.execute(status -> {

      List<JukeboxSplitPeriodEntity> periods = entityManager
          .createQuery("from JukeboxSplitPeriodEntity", JukeboxSplitPeriodEntity.class)
          .getResultList();

      List<LocalCreditTransactionEntity> transactions = entityManager
          .createQuery("from LocalCreditTransactionEntity", LocalCreditTransactionEntity.class)
          .getResultList();

      FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();
      periods.forEach(root::addSplitPeriod);
      transactions.forEach(root::addLocalCreditTransaction);
      return root;
    });
  }
}
