package com.djt.jukeanator_engine.domain.email.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import com.djt.jukeanator_engine.domain.email.listener.AccountVerificationEmailListener;
import com.djt.jukeanator_engine.domain.email.listener.FinancialLedgerSplitEmailListener;
import com.djt.jukeanator_engine.domain.email.listener.PurchaseReceiptEmailListener;
import com.djt.jukeanator_engine.domain.email.service.EmailService;
import com.djt.jukeanator_engine.domain.email.service.EmailServiceImpl;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;

/**
 * Wires the {@code email} domain's beans. Each listener reacts to another domain's event, so those
 * domains only publish events and never depend on {@link EmailService} directly.
 */
@Configuration
public class EmailConfig {

  @Bean
  public EmailService emailService(JavaMailSender mailSender, EmailProperties emailProperties) {
    return new EmailServiceImpl(mailSender, emailProperties.getFromAddress(),
        emailProperties.getFinancialLedgerRecipients());
  }

  @Bean
  public PurchaseReceiptEmailListener purchaseReceiptEmailListener(EmailService emailService) {
    return new PurchaseReceiptEmailListener(emailService);
  }

  @Bean
  public AccountVerificationEmailListener accountVerificationEmailListener(
      EmailService emailService) {
    return new AccountVerificationEmailListener(emailService);
  }

  @Bean
  public FinancialLedgerSplitEmailListener financialLedgerSplitEmailListener(
      EmailService emailService, FinancialLedgerService financialLedgerService) {
    return new FinancialLedgerSplitEmailListener(emailService, financialLedgerService);
  }
}
