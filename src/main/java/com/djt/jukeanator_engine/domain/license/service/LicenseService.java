package com.djt.jukeanator_engine.domain.license.service;

import java.nio.file.Path;

import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.license.model.LicenseStatus;

/**
 * This instance's legacy license (standalone and slave modes): the {@code JukeANator.CTL} license
 * key, the {@code ExtendCode.CTL} subscription for the subscription tiers, provisioning against the
 * license server, and the resulting commercial / non-commercial state. Non-commercial operation
 * disables the payment inputs (bill acceptor, card reader); everything else keeps working.
 *
 * <p>Network failures never block local operation: without the license server the last known
 * local state (the files on disk) applies.
 */
public interface LicenseService {

  /** Current status. Cheap; safe to call on every payment key press. */
  @PublicServiceMethod
  LicenseStatus getStatus();

  /** True when payment inputs may operate. */
  @PublicServiceMethod
  boolean isCommercialEnabled();

  /** The registered customer email ({@code license.customer-email}), or null. */
  @PublicServiceMethod
  String getCustomerEmail();

  /** The customer name ({@code license.customer-name}), or null. */
  @PublicServiceMethod
  String getCustomerName();

  /** The location name ({@code license.location-name}), or null. */
  @PublicServiceMethod
  String getLocationName();

  /**
   * Asks the license server for the registered customer's name ({@code command=getcustomername},
   * the only legacy command that returns customer details) and, when it returns one, saves it as
   * {@code license.customer-name} in the external config. Needs the customer email; failures are
   * logged and ignored.
   *
   * @return the customer name now configured, or null
   */
  String refreshCustomerName();

  /** Re-reads the license files and re-evaluates the subscription without contacting the server. */
  LicenseStatus refreshLocal();

  /**
   * Subscription tiers only: asks the license server for the paid-through quarter and rewrites
   * {@code ExtendCode.CTL}; falls back to the local file when the server cannot be reached.
   */
  LicenseStatus validateSubscription();

  /**
   * Retrieves a license key for this computer from the license server using the customer's
   * registered email and passcode.
   *
   * @throws LicenseException with an operator-facing message if provisioning fails
   */
  LicenseStatus provision(String customerEmail, String passcode, String locationName);

  /**
   * Installs a {@code JukeANator.CTL} the customer was sent, after checking it belongs to this
   * computer.
   *
   * @throws LicenseException with an operator-facing message if the file is not valid here
   */
  LicenseStatus importLicenseFile(Path file);

  /** Saves the registration details (blank values are left unchanged) and re-evaluates. */
  LicenseStatus updateRegistrationDetails(String customerEmail, String customerName,
      String locationName);

  /** Provisions with {@code license.auto-provision-*} when no license is installed. */
  void autoProvisionIfConfigured();

  /** Hourly: decrements the subscription hour counter (subscription tiers only). */
  void hourlyTick();

  /** Reports in to the license server; failures are logged and ignored. */
  void sendHeartbeat();

  /** Asks the license server for the newest jukeanator-engine version. */
  void checkEngineVersion();
}
