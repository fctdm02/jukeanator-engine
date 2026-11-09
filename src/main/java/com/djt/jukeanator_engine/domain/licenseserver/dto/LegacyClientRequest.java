package com.djt.jukeanator_engine.domain.licenseserver.dto;

/**
 * The common parameters every legacy {@code /FrontController} command carries, already decoded
 * the way the legacy server decoded them.
 *
 * @param serialNumber machine id
 * @param licenseType normalized single letter (R/A/C/P/L) or the raw value if not numeric
 * @param customerEmail customer email, may be null
 * @param customerName customer name, may be null
 * @param locationName location name, empty if absent
 * @param buildDate client build date, empty if absent
 * @param appVersion jukeanator-engine version (engine clients only), else null
 * @param appMode jukeanator-engine {@code app.mode} (engine clients only), else null
 */
public record LegacyClientRequest(
    String serialNumber,
    String licenseType,
    String customerEmail,
    String customerName,
    String locationName,
    String buildDate,
    String appVersion,
    String appMode) {

  /** True when the request came from jukeanator-engine rather than legacy MP3Jukeboxx. */
  public boolean isEngineClient() {
    return appVersion != null && !appVersion.isBlank();
  }

  public boolean hasValidEmail() {
    return customerEmail != null && !customerEmail.trim().isEmpty() && customerEmail.contains("@");
  }
}
