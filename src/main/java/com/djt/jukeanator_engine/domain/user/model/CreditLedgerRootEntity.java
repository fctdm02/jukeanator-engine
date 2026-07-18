package com.djt.jukeanator_engine.domain.user.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * Append-only aggregate root for all credit transactions — kept separate from
 * {@link UserRootEntity} so that an ever-growing ledger doesn't force a full rewrite of every
 * user's profile data on every single credit spend (today's {@code storeAggregateRoot} already
 * does a full rewrite per mutation).
 *
 * @author tmyers
 */
public class CreditLedgerRootEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  public static final String CREDIT_LEDGER_FILENAME = "JukeANator_CreditLedger.oos";

  private List<CreditTransactionEntity> transactions = new ArrayList<>();

  public CreditLedgerRootEntity() {
    super(Integer.valueOf(0));
  }

  @Override
  public String getNaturalIdentity() {
    return "CreditLedgerRootEntity";
  }

  public void appendTransaction(CreditTransactionEntity transaction) {
    transactions.add(transaction);
  }

  public List<CreditTransactionEntity> getTransactions() {
    return transactions;
  }

  public List<CreditTransactionEntity> findByLocationId(String locationId, Instant from,
      Instant to) {
    return transactions.stream()
        .filter(t -> locationId.equals(t.getLocationId()))
        .filter(t -> !t.getTimestamp().isBefore(from) && !t.getTimestamp().isAfter(to))
        .toList();
  }

  public List<CreditTransactionEntity> findByUserEmail(String userEmail, Instant from,
      Instant to) {
    return transactions.stream()
        .filter(t -> userEmail.equals(t.getUserEmail()))
        .filter(t -> !t.getTimestamp().isBefore(from) && !t.getTimestamp().isAfter(to))
        .toList();
  }
}
