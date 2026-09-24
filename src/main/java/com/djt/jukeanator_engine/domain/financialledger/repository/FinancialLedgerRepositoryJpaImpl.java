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
import com.djt.jukeanator_engine.domain.financialledger.model.AbstractLocationTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCashTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocationMobileCreditUsageEntity;

/**
 * JPA/Hibernate-backed implementation of {@link FinancialLedgerRepository}, following the same
 * shape as {@code LocationRepositoryJpaImpl}: {@link FinancialLedgerRootEntity} is not itself
 * JPA-mapped (no {@code financial_ledger_root} table) -- it's an in-memory aggregate assembled
 * directly from {@link JukeboxSplitPeriodEntity}, {@link LocalCashTransactionEntity}, {@link
 * LocalCreditTransactionEntity}, and {@link LocationMobileCreditUsageEntity} rows. None of these
 * child types are ever deleted once created, so unlike {@code LocationRepositoryJpaImpl} there is
 * no orphan-removal step -- only merge (existing rows, e.g. finalizing a period or marking one
 * synced to master) and insert (new rows).
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

      Set<Integer> persistedCashIds = new HashSet<>(entityManager
          .createQuery("select t.persistentIdentity from LocalCashTransactionEntity t",
              Integer.class)
          .getResultList());

      for (LocalCashTransactionEntity transaction : root.getLocalCashTransactions()) {
        if (persistedCashIds.contains(transaction.getPersistentIdentity())) {
          entityManager.merge(transaction);
        } else {
          insertNewLocationTransaction(transaction, "CASH");
        }
      }

      Set<Integer> persistedCreditCardIds = new HashSet<>(entityManager
          .createQuery("select t.persistentIdentity from LocalCreditTransactionEntity t",
              Integer.class)
          .getResultList());

      for (LocalCreditTransactionEntity transaction : root.getLocalCreditCardTransactions()) {
        if (persistedCreditCardIds.contains(transaction.getPersistentIdentity())) {
          entityManager.merge(transaction);
        } else {
          insertNewLocationTransaction(transaction, "CREDIT_CARD");
        }
      }

      Set<Integer> persistedMobileIds = new HashSet<>(entityManager
          .createQuery("select u.persistentIdentity from LocationMobileCreditUsageEntity u",
              Integer.class)
          .getResultList());

      for (LocationMobileCreditUsageEntity usage : root.getMobileCreditUsages()) {
        if (persistedMobileIds.contains(usage.getPersistentIdentity())) {
          entityManager.merge(usage);
        } else {
          insertNewMobileCreditUsage(usage);
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

  // parent_location_id is sourced from JukeboxSplitPeriodEntity.getLocationId() -- the period's
  // transient parentLocation on the owning instance (same as SongLibraryRepositoryJpaImpl sourcing
  // it from RootFolderEntity's), or the mirrored slave's location id on master. The entity maps it
  // read-only, so merge() of an existing row leaves it be.
  private void insertNewPeriod(JukeboxSplitPeriodEntity period) {

    requireNonNull(period.getLocationId(),
        "period.getLocationId() cannot be null when storing via FinancialLedgerRepositoryJpaImpl");

    entityManager.createNativeQuery("insert into location_jukebox_split "
        + "(persistent_identity, version, parent_location_id, start_date, end_date, "
        + "split_percentage_to_owner, cash_total, card_total, mobile_total, total_earned, "
        + "amount_due_owner, amount_due_operator, source_period_id, synced_to_master_at) "
        + "values (:id, :version, :parentLocationId, :startDate, :endDate, "
        + ":splitPercentageToOwner, :cashTotal, :cardTotal, :mobileTotal, :totalEarned, "
        + ":amountDueOwner, :amountDueOperator, :sourcePeriodId, :syncedToMasterAt)")
        .setParameter("id", period.getPersistentIdentity())
        .setParameter("version", period.getVersion())
        .setParameter("parentLocationId", period.getLocationId())
        .setParameter("startDate", period.getStartDate())
        .setParameter("endDate", period.getEndDate())
        .setParameter("splitPercentageToOwner", period.getSplitPercentageToOwner())
        .setParameter("cashTotal", period.getCashTotal())
        .setParameter("cardTotal", period.getCardTotal())
        .setParameter("mobileTotal", period.getMobileTotal())
        .setParameter("totalEarned", period.getTotalEarned())
        .setParameter("amountDueOwner", period.getAmountDueOwner())
        .setParameter("amountDueOperator", period.getAmountDueOperator())
        .setParameter("sourcePeriodId", period.getSourcePeriodId())
        .setParameter("syncedToMasterAt", period.getSyncedToMasterAt())
        .executeUpdate();
  }

  /**
   * Both {@link LocalCashTransactionEntity} and {@link LocalCreditTransactionEntity} share one
   * table ({@code location_transaction}) now, differing only by {@code discriminatorValue}
   * ("CASH"/"CREDIT_CARD") -- see {@link AbstractLocationTransactionEntity}.
   */
  private void insertNewLocationTransaction(AbstractLocationTransactionEntity transaction,
      String discriminatorValue) {

    entityManager.createNativeQuery("insert into location_transaction "
        + "(persistent_identity, version, transaction_type, amount_dollars, timestamp, "
        + "location_id, source_transaction_id, synced_to_master_at) "
        + "values (:id, :version, :transactionType, :amountDollars, :timestamp, :locationId, "
        + ":sourceTransactionId, :syncedToMasterAt)")
        .setParameter("id", transaction.getPersistentIdentity())
        .setParameter("version", transaction.getVersion())
        .setParameter("transactionType", discriminatorValue)
        .setParameter("amountDollars", transaction.getAmountDollars())
        .setParameter("timestamp", transaction.getTimestamp())
        .setParameter("locationId", transaction.getLocationId())
        .setParameter("sourceTransactionId", transaction.getSourceTransactionId())
        .setParameter("syncedToMasterAt", transaction.getSyncedToMasterAt())
        .executeUpdate();
  }

  // Native insert for the same pre-assigned-id reason as the two inserts above.
  private void insertNewMobileCreditUsage(LocationMobileCreditUsageEntity usage) {

    entityManager.createNativeQuery("insert into location_mobile_credit_usage "
        + "(persistent_identity, version, location_id, source_sync_id, user_email, amount, type, "
        + "timestamp, song_album_id, song_id) "
        + "values (:id, :version, :locationId, :sourceSyncId, :userEmail, :amount, :type, "
        + ":timestamp, :songAlbumId, :songId)")
        .setParameter("id", usage.getPersistentIdentity())
        .setParameter("version", usage.getVersion())
        .setParameter("locationId", usage.getLocationId())
        .setParameter("sourceSyncId", usage.getSourceSyncId())
        .setParameter("userEmail", usage.getUserEmail())
        .setParameter("amount", usage.getAmount())
        .setParameter("type", usage.getType().name())
        .setParameter("timestamp", usage.getTimestamp())
        .setParameter("songAlbumId", usage.getSongAlbumId())
        .setParameter("songId", usage.getSongId())
        .executeUpdate();
  }

  private FinancialLedgerRootEntity loadOrCreateRoot() {

    return transactionTemplate.execute(status -> {

      List<JukeboxSplitPeriodEntity> periods = entityManager
          .createQuery("from JukeboxSplitPeriodEntity", JukeboxSplitPeriodEntity.class)
          .getResultList();

      List<LocalCashTransactionEntity> cashTransactions = entityManager
          .createQuery("from LocalCashTransactionEntity", LocalCashTransactionEntity.class)
          .getResultList();

      List<LocalCreditTransactionEntity> creditCardTransactions = entityManager
          .createQuery("from LocalCreditTransactionEntity", LocalCreditTransactionEntity.class)
          .getResultList();

      List<LocationMobileCreditUsageEntity> mobileCreditUsages = entityManager
          .createQuery("from LocationMobileCreditUsageEntity", LocationMobileCreditUsageEntity.class)
          .getResultList();

      FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();
      periods.forEach(root::addSplitPeriod);
      cashTransactions.forEach(root::addLocalCashTransaction);
      creditCardTransactions.forEach(root::addLocalCreditCardTransaction);
      mobileCreditUsages.forEach(root::addMobileCreditUsage);
      return root;
    });
  }
}
