package com.djt.jukeanator_engine.domain.email.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties bound to the {@code email:} YAML prefix. SMTP connection settings themselves stay
 * under Spring Boot's own {@code spring.mail.*}.
 */
@Validated
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

  private String fromAddress = "tmyers1@yahoo.com"; // sender for every email this app sends
  private List<String> financialLedgerRecipients = new ArrayList<>(List.of("tmyers1@yahoo.com"));
  private boolean verificationRequired = true; // Create Account must verify its email address
  private String verificationBaseUrl; // public web UI URL; blank = derived from the request
  private Duration verificationTokenTtl = Duration.ofHours(24);

  public boolean isVerificationRequired() {
    return verificationRequired;
  }

  public void setVerificationRequired(boolean verificationRequired) {
    this.verificationRequired = verificationRequired;
  }

  public String getFromAddress() {
    return fromAddress;
  }

  public void setFromAddress(String fromAddress) {
    this.fromAddress = fromAddress;
  }

  public List<String> getFinancialLedgerRecipients() {
    return financialLedgerRecipients;
  }

  /** Blank entries (e.g. from a trailing comma) are dropped. */
  public void setFinancialLedgerRecipients(List<String> financialLedgerRecipients) {
    List<String> cleaned = new ArrayList<>();
    if (financialLedgerRecipients != null) {
      for (String recipient : financialLedgerRecipients) {
        if (recipient != null && !recipient.isBlank()) {
          cleaned.add(recipient.trim());
        }
      }
    }
    this.financialLedgerRecipients = cleaned;
  }

  public String getVerificationBaseUrl() {
    return verificationBaseUrl;
  }

  public void setVerificationBaseUrl(String verificationBaseUrl) {
    this.verificationBaseUrl = verificationBaseUrl;
  }

  public Duration getVerificationTokenTtl() {
    return verificationTokenTtl;
  }

  public void setVerificationTokenTtl(Duration verificationTokenTtl) {
    this.verificationTokenTtl = verificationTokenTtl;
  }
}
