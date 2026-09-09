package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class EmailServiceImpl implements EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a").withZone(ZoneId.systemDefault());

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public EmailServiceImpl(JavaMailSender mailSender, String fromAddress) {
    this.mailSender = Objects.requireNonNull(mailSender);
    this.fromAddress = Objects.requireNonNull(fromAddress);
  }

  @Override
  public void sendPurchaseReceiptEmail(String toAddress, String firstName, int credits,
      int bonusCredits, BigDecimal amountUsd, String paymentSource, String transactionId,
      Instant timestamp, int newBalance) {

    // A bad SMTP config or transient network failure must never fail or roll back the purchase
    // that already succeeded -- log and move on.
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromAddress);
      message.setTo(toAddress);
      message.setSubject("Your JukeANator receipt");
      message.setText(buildBody(firstName, credits, bonusCredits, amountUsd, paymentSource,
          transactionId, timestamp, newBalance));
      mailSender.send(message);
    } catch (Exception e) {
      log.warn("Failed to send purchase receipt email to {}: {}", toAddress, e.getMessage());
    }
  }

  private String buildBody(String firstName, int credits, int bonusCredits, BigDecimal amountUsd,
      String paymentSource, String transactionId, Instant timestamp, int newBalance) {

    StringBuilder body = new StringBuilder();
    body.append("Hi ").append(firstName).append(",\n\n");
    body.append("Thanks for your purchase! Here's your receipt:\n\n");
    body.append("Credits added: ").append(credits);
    if (bonusCredits > 0) {
      body.append(" (+").append(bonusCredits).append(" bonus)");
    }
    body.append('\n');
    body.append("Amount charged: $").append(amountUsd.setScale(2, java.math.RoundingMode.HALF_UP))
        .append('\n');
    body.append("Payment method: ").append(paymentSource).append('\n');
    body.append("Transaction ID: ").append(transactionId).append('\n');
    body.append("Date: ").append(TIMESTAMP_FORMAT.format(timestamp)).append('\n');
    body.append("New balance: ").append(newBalance).append(" credits\n\n");
    body.append("Enjoy playing music!\n");
    return body.toString();
  }
}
