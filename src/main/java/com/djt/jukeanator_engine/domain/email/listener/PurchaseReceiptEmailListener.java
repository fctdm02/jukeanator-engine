package com.djt.jukeanator_engine.domain.email.listener;

import java.util.Objects;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import com.djt.jukeanator_engine.domain.common.security.SecurityContextPropagatingRunnable;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.email.service.EmailService;
import com.djt.jukeanator_engine.domain.user.event.PurchaseCompletedEvent;

/**
 * Runs off the request thread ({@code @Async}) so a slow SMTP send never delays the HTTP response.
 * That thread has no security context, so the {@link EmailService} call runs as the system
 * principal ({@code ServiceSecurityAspect} rejects unauthenticated service calls).
 */
public class PurchaseReceiptEmailListener {

  private final EmailService emailService;

  public PurchaseReceiptEmailListener(EmailService emailService) {
    this.emailService = Objects.requireNonNull(emailService);
  }

  @Async
  @EventListener
  public void onPurchaseCompleted(PurchaseCompletedEvent event) {
    new SecurityContextPropagatingRunnable(() -> emailService.sendPurchaseReceiptEmail(
        event.emailAddress(), event.firstName(), event.credits(), event.bonusCredits(),
        event.amountUsd(), event.paymentSource(), event.transactionId(), event.timestamp(),
        event.newBalance()), SystemPrincipal.SystemAuthenticationToken.INSTANCE).run();
  }
}
