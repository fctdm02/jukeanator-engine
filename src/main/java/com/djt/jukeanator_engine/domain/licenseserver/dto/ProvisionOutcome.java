package com.djt.jukeanator_engine.domain.licenseserver.dto;

/**
 * Result of a provisioning request: either the 16 raw license key bytes, or a legacy text
 * message explaining the refusal.
 */
public record ProvisionOutcome(byte[] licenseKey, String message) {

  public static ProvisionOutcome key(byte[] licenseKey) {
    return new ProvisionOutcome(licenseKey, null);
  }

  public static ProvisionOutcome refused(String message) {
    return new ProvisionOutcome(null, message);
  }

  public boolean isSuccess() {
    return licenseKey != null;
  }
}
