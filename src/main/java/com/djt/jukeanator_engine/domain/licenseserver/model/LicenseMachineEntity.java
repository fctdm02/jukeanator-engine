package com.djt.jukeanator_engine.domain.licenseserver.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * A computer registered to a {@link LicenseCustomerEntity}, identified by its legacy machine id
 * ("serial number"). Replaces the legacy {@code CUSTOMER_SERIAL} table; the heartbeat columns
 * replace the legacy per-IP {@code CUSTOMER_SERIAL_IP_ADDRESS} table.
 */
@Entity
@Table(name = "license_machine")
public class LicenseMachineEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(name = "customer_id", nullable = false)
  private Integer customerId;

  @Column(name = "serial_number", nullable = false, unique = true)
  private String serialNumber;

  /** Subscription paid-through quarter ({@code nQyyyy}), or empty for perpetual tiers. */
  @Column(name = "last_paid_quarter", nullable = false)
  private String lastPaidQuarter = "";

  @Column(name = "location_name")
  private String locationName;

  @Column(name = "build_date")
  private String buildDate;

  @Column(nullable = false)
  private boolean revoked;

  @Enumerated(EnumType.STRING)
  @Column(name = "client_kind", nullable = false)
  private ClientKind clientKind = ClientKind.LEGACY;

  @Column(name = "app_version")
  private String appVersion;

  @Column(name = "app_mode")
  private String appMode;

  @Column(name = "first_seen_at")
  private Instant firstSeenAt;

  @Column(name = "last_heartbeat_at")
  private Instant lastHeartbeatAt;

  @Column(name = "heartbeat_count", nullable = false)
  private int heartbeatCount;

  @Column(name = "last_license_issued_at")
  private Instant lastLicenseIssuedAt;

  @Column(name = "legacy_customer_serial_id")
  private Integer legacyCustomerSerialId;

  public LicenseMachineEntity() {}

  @Override
  public String getNaturalIdentity() {
    return serialNumber;
  }

  public Integer getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Integer customerId) {
    this.customerId = customerId;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }

  public String getLastPaidQuarter() {
    return lastPaidQuarter;
  }

  public void setLastPaidQuarter(String lastPaidQuarter) {
    this.lastPaidQuarter = lastPaidQuarter == null ? "" : lastPaidQuarter;
  }

  public String getLocationName() {
    return locationName;
  }

  public void setLocationName(String locationName) {
    this.locationName = locationName;
  }

  public String getBuildDate() {
    return buildDate;
  }

  public void setBuildDate(String buildDate) {
    this.buildDate = buildDate;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public void setRevoked(boolean revoked) {
    this.revoked = revoked;
  }

  public ClientKind getClientKind() {
    return clientKind;
  }

  public void setClientKind(ClientKind clientKind) {
    this.clientKind = clientKind;
  }

  public String getAppVersion() {
    return appVersion;
  }

  public void setAppVersion(String appVersion) {
    this.appVersion = appVersion;
  }

  public String getAppMode() {
    return appMode;
  }

  public void setAppMode(String appMode) {
    this.appMode = appMode;
  }

  public Instant getFirstSeenAt() {
    return firstSeenAt;
  }

  public void setFirstSeenAt(Instant firstSeenAt) {
    this.firstSeenAt = firstSeenAt;
  }

  public Instant getLastHeartbeatAt() {
    return lastHeartbeatAt;
  }

  public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
    this.lastHeartbeatAt = lastHeartbeatAt;
  }

  public int getHeartbeatCount() {
    return heartbeatCount;
  }

  public void setHeartbeatCount(int heartbeatCount) {
    this.heartbeatCount = heartbeatCount;
  }

  public Instant getLastLicenseIssuedAt() {
    return lastLicenseIssuedAt;
  }

  public void setLastLicenseIssuedAt(Instant lastLicenseIssuedAt) {
    this.lastLicenseIssuedAt = lastLicenseIssuedAt;
  }

  public Integer getLegacyCustomerSerialId() {
    return legacyCustomerSerialId;
  }

  public void setLegacyCustomerSerialId(Integer legacyCustomerSerialId) {
    this.legacyCustomerSerialId = legacyCustomerSerialId;
  }
}
