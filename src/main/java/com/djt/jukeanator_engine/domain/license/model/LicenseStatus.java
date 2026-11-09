package com.djt.jukeanator_engine.domain.license.model;

import java.time.Instant;

import com.djt.jukeanator_engine.domain.license.legacy.LicenseTier;

/**
 * Snapshot of this instance's license state.
 *
 * @param licensedTier tier of the installed {@code JukeANator.CTL} ({@link LicenseTier#D} if none)
 * @param effectiveTier tier actually in force after revocation, demotion and subscription checks
 * @param machineId machine id the license matched, or the id that would be provisioned
 * @param licenseDirectory folder holding the license files
 * @param commercialEnabled true when payment inputs (bill acceptor, card reader) may operate
 * @param subscriptionQuarter paid-through quarter from {@code ExtendCode.CTL}, or null
 * @param subscriptionHoursRemaining runtime hours left in {@code ExtendCode.CTL}, or null
 * @param subscriptionExpiresAt expiry instant of the paid-through quarter, or null
 * @param messageCode legacy message code (e.g. {@code 555}, {@code 999}), or null
 * @param message message to show the operator, or null
 * @param warning true when {@code message} is a renewal warning rather than an error
 * @param registrationNeeded true when the operator must provide registration details
 * @param lastServerCheckAt when the license server last answered a subscription check
 * @param latestEngineVersion newest jukeanator-engine version reported by the server, or null
 * @param engineDownloadUrl where to download {@code latestEngineVersion}, or null
 */
public record LicenseStatus(
    LicenseTier licensedTier,
    LicenseTier effectiveTier,
    String machineId,
    String licenseDirectory,
    boolean commercialEnabled,
    String subscriptionQuarter,
    Integer subscriptionHoursRemaining,
    Instant subscriptionExpiresAt,
    String messageCode,
    String message,
    boolean warning,
    boolean registrationNeeded,
    Instant lastServerCheckAt,
    String latestEngineVersion,
    String engineDownloadUrl) {

  /** Status before any check has run: trial, nothing enabled. */
  public static LicenseStatus initial() {
    return new LicenseStatus(LicenseTier.D, LicenseTier.D, null, null, false, null, null, null,
        null, null, false, false, null, null, null);
  }

  public boolean isTrial() {
    return licensedTier == LicenseTier.D;
  }

  /** Human-readable license description, e.g. "Commercial-Subscription". */
  public String describe() {
    if (licensedTier == effectiveTier) {
      return effectiveTier.getDisplayName();
    }
    return effectiveTier.getDisplayName() + " (licensed: " + licensedTier.getDisplayName() + ")";
  }
}
