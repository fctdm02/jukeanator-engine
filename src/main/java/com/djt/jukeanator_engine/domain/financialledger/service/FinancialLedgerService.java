package com.djt.jukeanator_engine.domain.financialledger.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;

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

  /** Same as {@link #receiveLocalCashSync}, for the credit-card-reader stream. */
  void receiveLocalCreditCardSync(Integer locationId, String apiKey, LocalTransactionSyncDto dto)
      throws LocationServiceException;
}
