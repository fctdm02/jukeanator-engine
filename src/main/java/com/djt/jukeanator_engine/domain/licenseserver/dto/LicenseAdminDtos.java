package com.djt.jukeanator_engine.domain.licenseserver.dto;

import java.time.Instant;
import java.util.List;

/** Request/response bodies of the license administration API ({@code /api/admin/licenses}). */
public final class LicenseAdminDtos {

  private LicenseAdminDtos() {}

  public record MachineDto(
      int id,
      int customerId,
      String serialNumber,
      String lastPaidQuarter,
      String locationName,
      String buildDate,
      boolean revoked,
      String clientKind,
      String appVersion,
      String appMode,
      Instant firstSeenAt,
      Instant lastHeartbeatAt,
      int heartbeatCount,
      Instant lastLicenseIssuedAt) {}

  public record CustomerDto(
      int id,
      String customerName,
      String email,
      String phoneNumber,
      String address,
      String notes,
      String licenseType,
      int availableLicenses,
      boolean revoked,
      boolean autoCreated,
      Instant dateAdded,
      List<MachineDto> machines) {}

  /**
   * Create or update a customer. On update, null fields are left unchanged and a blank
   * {@code passcode} keeps the current one. The passcode is plaintext here and stored as the
   * SHA-1 hex the legacy client sends.
   */
  public record CustomerRequest(
      String customerName,
      String email,
      String passcode,
      String phoneNumber,
      String address,
      String notes,
      String licenseType,
      Integer availableLicenses) {}

  public record AddLicensesRequest(int count) {}

  public record RevokeRequest(boolean revoked) {}

  /** Update a machine; null fields are left unchanged. */
  public record MachineRequest(
      String lastPaidQuarter,
      String locationName,
      Integer customerId,
      Boolean revoked) {}

  /**
   * Extend a machine's subscription by {@code quarters} (4 = one year), counting from its current
   * paid-through quarter or, if that has lapsed, from the current quarter.
   */
  public record ExtendRequest(int quarters) {}
}
