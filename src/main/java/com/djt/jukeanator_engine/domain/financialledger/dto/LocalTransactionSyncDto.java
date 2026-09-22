package com.djt.jukeanator_engine.domain.financialledger.dto;

import java.time.Instant;

/**
 * Wire shape a slave POSTs to master to mirror one local (bill-acceptor / credit-card-reader)
 * transaction -- distinct from {@link LocalTransactionDto}, which is the filesystem-repository's
 * JSON persistence shape and additionally carries a master-or-slave-local {@code
 * persistentIdentity}/{@code locationId} that have no meaning on the wire (the URL path already
 * carries {@code locationId}; master mints its own {@code persistentIdentity} on receipt).
 * {@code sourceTransactionId} is the slave's own local {@code persistentIdentity} for this
 * transaction, master's idempotency key for a retried push.
 */
public record LocalTransactionSyncDto(Integer sourceTransactionId, int amountDollars,
    Instant timestamp) {
}
