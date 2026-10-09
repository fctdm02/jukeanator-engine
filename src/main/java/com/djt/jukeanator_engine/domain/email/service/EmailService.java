package com.djt.jukeanator_engine.domain.email.service;

import java.math.BigDecimal;
import java.time.Instant;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;

/**
 * Sends every email this app produces. Implementations log and swallow send failures, so a bad
 * SMTP config never fails the operation that triggered the email.
 *
 * <p>A plain interface (not just a concrete class) so {@code @Async} can proxy it via the JDK
 * interface-proxy mode this app runs under (see {@code spring.aop.proxy-target-class: false} in
 * application.yml) — a concrete class there would silently fail to proxy.
 */
public interface EmailService {

  /** The Add Funds receipt (Web/Mobile UI). */
  void sendPurchaseReceiptEmail(String toAddress, String firstName, int credits, int bonusCredits,
      BigDecimal amountUsd, String paymentSource, String transactionId, Instant timestamp,
      int newBalance);

  /** The Create Account verification link (Web/Mobile UI). */
  void sendAccountVerificationEmail(String toAddress, String firstName, String verificationUrl,
      Instant expiresAt);

  /**
   * The Financial Ledger split summary (JFC/Swing UI), sent to {@code
   * email.financial-ledger-recipients}; a no-op when that list is empty.
   */
  void sendFinancialLedgerSplitSummaryEmail(JukeboxSplitPeriodDto period);
}
