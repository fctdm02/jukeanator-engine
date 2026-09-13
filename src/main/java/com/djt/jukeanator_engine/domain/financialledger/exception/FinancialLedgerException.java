package com.djt.jukeanator_engine.domain.financialledger.exception;

public class FinancialLedgerException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public FinancialLedgerException(String message) {
    super(message);
  }

  public FinancialLedgerException(String message, Throwable cause) {
    super(message, cause);
  }
}
