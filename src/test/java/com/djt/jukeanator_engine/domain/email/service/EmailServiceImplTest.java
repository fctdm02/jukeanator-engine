package com.djt.jukeanator_engine.domain.email.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;

class EmailServiceImplTest {

  private static final String FROM = "owner@example.com";

  private JavaMailSender mailSender;

  @BeforeEach
  void setUp() {
    mailSender = mock(JavaMailSender.class);
  }

  private SimpleMailMessage sentMessage() {
    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    return captor.getValue();
  }

  private static JukeboxSplitPeriodDto period() {
    return new JukeboxSplitPeriodDto(7, Instant.parse("2026-09-01T12:00:00Z"),
        Instant.parse("2026-10-01T12:00:00Z"), 40, new BigDecimal("100"), new BigDecimal("50.5"),
        new BigDecimal("25"), new BigDecimal("175.5"), new BigDecimal("70.20"),
        new BigDecimal("105.30"), 3, null, null);
  }

  @Test
  void purchaseReceipt_goesToThePurchaser() {

    new EmailServiceImpl(mailSender, FROM, List.of()).sendPurchaseReceiptEmail("pat@example.com",
        "Pat", 20, 5, new BigDecimal("10"), "Visa 1234", "tx-1", Instant.now(), 25);

    SimpleMailMessage message = sentMessage();
    assertEquals(FROM, message.getFrom());
    assertArrayEquals(new String[] {"pat@example.com"}, message.getTo());
    assertEquals("Your JukeANator receipt", message.getSubject());
    assertTrue(message.getText().contains("Amount charged: $10.00"));
    assertTrue(message.getText().contains("Transaction ID: tx-1"));
  }

  @Test
  void accountVerification_containsTheLink() {

    new EmailServiceImpl(mailSender, FROM, List.of()).sendAccountVerificationEmail(
        "pat@example.com", "Pat", "http://jukebox.test/api/users/verify?token=abc", Instant.now());

    SimpleMailMessage message = sentMessage();
    assertArrayEquals(new String[] {"pat@example.com"}, message.getTo());
    assertEquals("Verify your JukeANator account", message.getSubject());
    assertTrue(message.getText().contains("http://jukebox.test/api/users/verify?token=abc"));
  }

  @Test
  void splitSummary_goesToEveryFinancialLedgerRecipient() {

    new EmailServiceImpl(mailSender, FROM, List.of("a@example.com", "b@example.com"))
        .sendFinancialLedgerSplitSummaryEmail(period());

    SimpleMailMessage message = sentMessage();
    assertArrayEquals(new String[] {"a@example.com", "b@example.com"}, message.getTo());
    assertTrue(message.getSubject().startsWith("JukeANator split summary"));
    String text = message.getText();
    assertTrue(text.contains("Cash: $100.00"));
    assertTrue(text.contains("Credit card: $50.50"));
    assertTrue(text.contains("Mobile: $25.00"));
    assertTrue(text.contains("Total earned: $175.50"));
    assertTrue(text.contains("Split to owner: 40%"));
    assertTrue(text.contains("Amount due owner: $70.20"));
    assertTrue(text.contains("Amount due operator: $105.30"));
  }

  @Test
  void splitSummary_withNoRecipientsSendsNothing() {

    new EmailServiceImpl(mailSender, FROM, List.of()).sendFinancialLedgerSplitSummaryEmail(period());

    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void sendFailureIsSwallowed() {

    doThrow(new MailSendException("SMTP down")).when(mailSender)
        .send(any(SimpleMailMessage.class));

    // Must not throw: a mail failure never fails the operation that triggered the email.
    new EmailServiceImpl(mailSender, FROM, List.of()).sendAccountVerificationEmail(
        "pat@example.com", "Pat", "http://x", Instant.now());
  }
}
