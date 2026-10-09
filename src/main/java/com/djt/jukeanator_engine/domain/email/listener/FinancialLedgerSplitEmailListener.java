package com.djt.jukeanator_engine.domain.email.listener;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import com.djt.jukeanator_engine.domain.common.security.SecurityContextPropagatingRunnable;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.email.service.EmailService;
import com.djt.jukeanator_engine.domain.financialledger.event.JukeboxSplitFinalizedEvent;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;

/**
 * Emails the split summary once {@code FinancialLedgerService.addSplit()} finalizes a period.
 * {@code @Async} so the SMTP send neither holds the ledger's lock nor delays the Swing status
 * update. That thread has no security context, so the service calls run as the system principal
 * ({@code ServiceSecurityAspect} rejects unauthenticated service calls).
 */
public class FinancialLedgerSplitEmailListener {

  private static final Logger log =
      LoggerFactory.getLogger(FinancialLedgerSplitEmailListener.class);

  private final EmailService emailService;
  private final FinancialLedgerService financialLedgerService;

  public FinancialLedgerSplitEmailListener(EmailService emailService,
      FinancialLedgerService financialLedgerService) {
    this.emailService = Objects.requireNonNull(emailService);
    this.financialLedgerService = Objects.requireNonNull(financialLedgerService);
  }

  @Async
  @EventListener
  public void onJukeboxSplitFinalized(JukeboxSplitFinalizedEvent event) {
    new SecurityContextPropagatingRunnable(() -> financialLedgerService.getAllPeriods().stream()
        .filter(period -> Objects.equals(period.persistentIdentity(), event.periodId()))
        .findFirst()
        .ifPresentOrElse(emailService::sendFinancialLedgerSplitSummaryEmail,
            () -> log.warn("Finalized split period {} not found; no summary email sent",
                event.periodId())),
        SystemPrincipal.SystemAuthenticationToken.INSTANCE).run();
  }
}
