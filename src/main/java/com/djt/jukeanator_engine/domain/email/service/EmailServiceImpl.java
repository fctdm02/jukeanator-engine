package com.djt.jukeanator_engine.domain.email.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;

public class EmailServiceImpl implements EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a").withZone(ZoneId.systemDefault());

  private final JavaMailSender mailSender;
  private final String fromAddress;
  private final List<String> financialLedgerRecipients;

  public EmailServiceImpl(JavaMailSender mailSender, String fromAddress,
      List<String> financialLedgerRecipients) {
    this.mailSender = Objects.requireNonNull(mailSender);
    this.fromAddress = Objects.requireNonNull(fromAddress);
    this.financialLedgerRecipients = List.copyOf(financialLedgerRecipients);
  }

  @Override
  public void sendPurchaseReceiptEmail(String toAddress, String firstName, int credits,
      int bonusCredits, BigDecimal amountUsd, String paymentSource, String transactionId,
      Instant timestamp, int newBalance) {

    send(new String[] {toAddress}, "Your JukeANator receipt", buildReceiptBody(firstName, credits,
        bonusCredits, amountUsd, paymentSource, transactionId, timestamp, newBalance),
        "purchase receipt");
  }

  @Override
  public void sendAccountVerificationEmail(String toAddress, String firstName,
      String verificationUrl, Instant expiresAt) {

    StringBuilder body = new StringBuilder();
    body.append("Hi ").append(firstName).append(",\n\n");
    body.append("Thanks for creating a JukeANator account. Please confirm your email address by ")
        .append("opening the link below:\n\n");
    body.append(verificationUrl).append("\n\n");
    body.append("This link expires ").append(TIMESTAMP_FORMAT.format(expiresAt)).append(".\n\n");
    body.append("If you did not create this account, you can ignore this email.\n");

    send(new String[] {toAddress}, "Verify your JukeANator account", body.toString(),
        "account verification");
  }

  @Override
  public void sendFinancialLedgerSplitSummaryEmail(JukeboxSplitPeriodDto period) {

    if (financialLedgerRecipients.isEmpty()) {
      log.info("No email.financial-ledger-recipients configured; skipping split summary email "
          + "for period {}", period.persistentIdentity());
      return;
    }

    StringBuilder body = new StringBuilder();
    body.append("A jukebox split was finalized.\n\n");
    body.append("Period start: ").append(formatInstant(period.startDate())).append('\n');
    body.append("Period end: ").append(formatInstant(period.endDate())).append("\n\n");
    body.append("Cash: $").append(money(period.cashTotal())).append('\n');
    body.append("Credit card: $").append(money(period.cardTotal())).append('\n');
    body.append("Mobile: $").append(money(period.mobileTotal())).append('\n');
    body.append("Total earned: $").append(money(period.totalEarned())).append("\n\n");
    body.append("Split to owner: ").append(period.splitPercentageToOwner()).append("%\n");
    body.append("Amount due owner: $").append(money(period.amountDueOwner())).append('\n');
    body.append("Amount due operator: $").append(money(period.amountDueOperator())).append('\n');

    send(financialLedgerRecipients.toArray(String[]::new),
        "JukeANator split summary: " + formatInstant(period.startDate()) + " - "
            + formatInstant(period.endDate()),
        body.toString(), "financial ledger split summary");
  }

  // A bad SMTP config or transient network failure must never fail or roll back the operation
  // that triggered the email -- log and move on.
  private void send(String[] toAddresses, String subject, String text, String description) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromAddress);
      message.setTo(toAddresses);
      message.setSubject(subject);
      message.setText(text);
      mailSender.send(message);
    } catch (Exception e) {
      log.warn("Failed to send {} email to {}: {}", description, String.join(", ", toAddresses),
          e.getMessage());
    }
  }

  private String buildReceiptBody(String firstName, int credits, int bonusCredits,
      BigDecimal amountUsd, String paymentSource, String transactionId, Instant timestamp,
      int newBalance) {

    StringBuilder body = new StringBuilder();
    body.append("Hi ").append(firstName).append(",\n\n");
    body.append("Thanks for your purchase! Here's your receipt:\n\n");
    body.append("Credits added: ").append(credits);
    if (bonusCredits > 0) {
      body.append(" (+").append(bonusCredits).append(" bonus)");
    }
    body.append('\n');
    body.append("Amount charged: $").append(money(amountUsd)).append('\n');
    body.append("Payment method: ").append(paymentSource).append('\n');
    body.append("Transaction ID: ").append(transactionId).append('\n');
    body.append("Date: ").append(TIMESTAMP_FORMAT.format(timestamp)).append('\n');
    body.append("New balance: ").append(newBalance).append(" credits\n\n");
    body.append("Enjoy playing music!\n");
    return body.toString();
  }

  private static String money(BigDecimal amount) {
    return amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private static String formatInstant(Instant instant) {
    return instant == null ? "-" : TIMESTAMP_FORMAT.format(instant);
  }
}
