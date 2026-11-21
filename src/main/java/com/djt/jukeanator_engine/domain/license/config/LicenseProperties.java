package com.djt.jukeanator_engine.domain.license.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties bound to the {@code license:} YAML prefix: the legacy license key
 * ({@code JukeANator.CTL}) and subscription ({@code ExtendCode.CTL}) checks performed by
 * standalone and slave instances. Master instances are the license server and ignore these. The
 * license server is always the master instance, at {@code app.master-instance-url}.
 */
@Validated
@ConfigurationProperties(prefix = "license")
public class LicenseProperties {

  /**
   * Folder holding {@code JukeANator.CTL} / {@code ExtendCode.CTL}. Blank = search
   * {@code app.data-dir}, then the working directory, then {@code C:\kiosk} (legacy locations).
   */
  private String directory;

  /** Registered customer email; required by the subscription tiers. */
  private String customerEmail;

  private String customerName;

  private String locationName;

  /**
   * With {@link #autoProvisionPasscode}: provision a license at startup when none is installed.
   * Both are blanked in the external config after a successful provision.
   */
  private String autoProvisionEmail;

  private String autoProvisionPasscode;

  /**
   * Version of the license agreement the operator accepted at first start (blank = not yet
   * accepted). Written by the jukebox UI; see {@code LicenseAgreement.VERSION}.
   */
  private String agreementAcceptedVersion;

  /** When {@link #agreementAcceptedVersion} was accepted (ISO-8601). */
  private String agreementAcceptedAt;

  /**
   * Version of the license agreement whose Commercial Use section (public performance licensing
   * is the operator's responsibility) the operator accepted. Payment inputs stay disabled until
   * it matches the current version.
   */
  private String commercialAgreementAcceptedVersion;

  /** When {@link #commercialAgreementAcceptedVersion} was accepted (ISO-8601). */
  private String commercialAgreementAcceptedAt;

  /** How often to report in to the license server. */
  private Duration heartbeatInterval = Duration.ofHours(6);

  /** Local hour (0-23) of the daily subscription re-check. */
  private int revalidationHour = 6;

  /** Force non-commercial operation regardless of the installed license. */
  private boolean demoteToNonCommercial = false;

  /** Zone for subscription quarter math; blank = system default. */
  private String timeZone;

  /** Connect timeout for license server calls. */
  private Duration connectTimeout = Duration.ofSeconds(5);

  /** Read timeout for license server calls. */
  private Duration readTimeout = Duration.ofSeconds(20);

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(String directory) {
    this.directory = directory;
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public void setCustomerEmail(String customerEmail) {
    this.customerEmail = customerEmail;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public String getLocationName() {
    return locationName;
  }

  public void setLocationName(String locationName) {
    this.locationName = locationName;
  }

  public String getAutoProvisionEmail() {
    return autoProvisionEmail;
  }

  public void setAutoProvisionEmail(String autoProvisionEmail) {
    this.autoProvisionEmail = autoProvisionEmail;
  }

  public String getAutoProvisionPasscode() {
    return autoProvisionPasscode;
  }

  public void setAutoProvisionPasscode(String autoProvisionPasscode) {
    this.autoProvisionPasscode = autoProvisionPasscode;
  }

  public String getAgreementAcceptedVersion() {
    return agreementAcceptedVersion;
  }

  public void setAgreementAcceptedVersion(String agreementAcceptedVersion) {
    this.agreementAcceptedVersion = agreementAcceptedVersion;
  }

  public String getAgreementAcceptedAt() {
    return agreementAcceptedAt;
  }

  public void setAgreementAcceptedAt(String agreementAcceptedAt) {
    this.agreementAcceptedAt = agreementAcceptedAt;
  }

  public String getCommercialAgreementAcceptedVersion() {
    return commercialAgreementAcceptedVersion;
  }

  public void setCommercialAgreementAcceptedVersion(String commercialAgreementAcceptedVersion) {
    this.commercialAgreementAcceptedVersion = commercialAgreementAcceptedVersion;
  }

  public String getCommercialAgreementAcceptedAt() {
    return commercialAgreementAcceptedAt;
  }

  public void setCommercialAgreementAcceptedAt(String commercialAgreementAcceptedAt) {
    this.commercialAgreementAcceptedAt = commercialAgreementAcceptedAt;
  }

  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public int getRevalidationHour() {
    return revalidationHour;
  }

  public void setRevalidationHour(int revalidationHour) {
    this.revalidationHour = revalidationHour;
  }

  public boolean isDemoteToNonCommercial() {
    return demoteToNonCommercial;
  }

  public void setDemoteToNonCommercial(boolean demoteToNonCommercial) {
    this.demoteToNonCommercial = demoteToNonCommercial;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }
}
