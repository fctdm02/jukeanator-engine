package com.djt.jukeanator_engine.domain.financialledger.service;

import java.time.Instant;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.MasterSyncOutboxEntry;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;

public interface FinancialLedgerService {

  /** Records one genuine bill-acceptor pulse ({@code amountDollars} is always 1 today). */
  void recordLocalCashCredit(int amountDollars);

  /** Records one genuine credit-card-reader pulse ({@code amountDollars} is always 1 today). */
  void recordLocalCreditCardCredit(int amountDollars);

  /**
   * Every jukebox-split period, oldest-first, with the open (current) period's sub-totals
   * computed live rather than read from storage.
   */
  List<JukeboxSplitPeriodDto> getAllPeriods();

  /**
   * Finalizes the current period (locking in its sub-totals, split percentage, and amounts due)
   * and opens a fresh $0 period starting now.
   */
  void addSplit();

  /**
   * Master-only. Receives one mirrored local cash transaction from a slave (see {@code
   * FinancialLedgerSyncService}). Idempotent: a retried push carrying the same {@code
   * (locationId, dto.sourceTransactionId())} is a safe no-op.
   *
   * @throws LocationServiceException if {@code locationId}/{@code apiKey} don't verify
   */
  void receiveLocalCashSync(Integer locationId, String apiKey, LocalTransactionSyncDto dto)
      throws LocationServiceException;

  /**
   * NOTE: System method, not to be invoked on behalf of a user. Re-tags this instance's in-memory
   * local transactions after its own location id is corrected post-handshake.
   *
   * @param event
   */
  @PublicServiceMethod
  void handleOwnLocationIdChangedEvent(OwnLocationIdChangedEvent event);

  /** Same as {@link #receiveLocalCashSync}, for the credit-card-reader stream. */
  void receiveLocalCreditCardSync(Integer locationId, String apiKey, LocalTransactionSyncDto dto)
      throws LocationServiceException;

  /**
   * Master-only. Receives one mirrored, finalized jukebox-split period from a slave. Idempotent:
   * a retried push carrying the same {@code (locationId, dto.sourcePeriodId())} is a safe no-op.
   *
   * @throws LocationServiceException if {@code locationId}/{@code apiKey} don't verify
   */
  void receiveSplitPeriodSync(Integer locationId, String apiKey, JukeboxSplitPeriodSyncDto dto)
      throws LocationServiceException;

  /**
   * Slave-only. Every local transaction and finalized split period this instance recorded itself
   * that master has not yet acknowledged -- the slave-to-master outbox {@code
   * FinancialLedgerSyncService} drains.
   */
  List<MasterSyncOutboxEntry> getPendingMasterSync();

  /** Slave-only. Marks {@code entries} (from {@link #getPendingMasterSync()}) as acknowledged. */
  void markSyncedToMaster(List<MasterSyncOutboxEntry> entries);

  /**
   * Master-only. The mobile/web song-credit spends master has recorded against {@code locationId}
   * at or after {@code since} -- the slave's catch-up pull for any live mirror push it missed.
   * Spends recorded before cross-instance sync ids existed are omitted (they can't be mirrored
   * idempotently).
   *
   * @throws LocationServiceException if {@code locationId}/{@code apiKey} don't verify
   */
  List<UserSongCreditUsageDto> getMobileCreditUsageForSync(Integer locationId, String apiKey,
      Instant since) throws LocationServiceException;

  /**
   * Slave-only. Stores one mobile/web song-credit spend master recorded against this slave's own
   * location. Idempotent on {@code usage.syncId()}.
   */
  void receiveMobileCreditUsage(UserSongCreditUsageDto usage);

  /** The open (current) period's start date, or {@code null} if there is none (master). */
  Instant getOpenPeriodStartDate();

  /** The most recent mirrored mobile/web spend's timestamp, or {@code null} if there are none. */
  Instant getLatestMobileCreditUsageTimestamp();
}
