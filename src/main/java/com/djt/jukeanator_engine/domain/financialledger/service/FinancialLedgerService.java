package com.djt.jukeanator_engine.domain.financialledger.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;

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
}
