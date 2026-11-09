package com.djt.jukeanator_engine.domain.licenseserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * A licensed customer on the license server (master mode). Replaces the legacy jukeanator.com
 * {@code CUSTOMER} table. A customer owns any number of {@link LicenseMachineEntity registered
 * computers}.
 *
 * <p>{@code licenseType} is the legacy single-letter tier (see
 * {@link com.djt.jukeanator_engine.domain.license.legacy.LicenseTier#getLetter()}); it decides
 * which license key the customer's computers receive when provisioned.
 */
@Entity
@Table(name = "license_customer")
public class LicenseCustomerEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "phone_number")
  private String phoneNumber;

  private String address;

  private String notes;

  @Column(name = "passcode_sha1", nullable = false)
  private String passcodeSha1;

  @Column(name = "license_type", nullable = false)
  private String licenseType;

  @Column(name = "available_licenses", nullable = false)
  private int availableLicenses;

  @Column(nullable = false)
  private boolean revoked;

  @Column(name = "auto_created", nullable = false)
  private boolean autoCreated;

  @Column(name = "legacy_customer_id")
  private Integer legacyCustomerId;

  public LicenseCustomerEntity() {}

  @Override
  public String getNaturalIdentity() {
    return email;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getPasscodeSha1() {
    return passcodeSha1;
  }

  public void setPasscodeSha1(String passcodeSha1) {
    this.passcodeSha1 = passcodeSha1;
  }

  public String getLicenseType() {
    return licenseType;
  }

  public void setLicenseType(String licenseType) {
    this.licenseType = licenseType;
  }

  public int getAvailableLicenses() {
    return availableLicenses;
  }

  public void setAvailableLicenses(int availableLicenses) {
    this.availableLicenses = availableLicenses;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public void setRevoked(boolean revoked) {
    this.revoked = revoked;
  }

  public boolean isAutoCreated() {
    return autoCreated;
  }

  public void setAutoCreated(boolean autoCreated) {
    this.autoCreated = autoCreated;
  }

  public Integer getLegacyCustomerId() {
    return legacyCustomerId;
  }

  public void setLegacyCustomerId(Integer legacyCustomerId) {
    this.legacyCustomerId = legacyCustomerId;
  }
}
