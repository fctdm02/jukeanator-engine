package com.djt.jukeanator_engine.domain.licenseserver.service;

import java.util.List;

import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LegacyClientRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.AddLicensesRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.CustomerDto;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.CustomerRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.ExtendRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.MachineDto;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.MachineRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.ProvisionOutcome;

/**
 * License server (master mode). The first group of methods implements the legacy
 * {@code /FrontController} commands that MP3Jukeboxx installs in the field and jukeanator-engine
 * instances call anonymously; their string results are the exact legacy reply texts (before the
 * legacy URL-encoding the controller applies). The second group is the administration API.
 */
public interface LicenseServerService {

  /**
   * Legacy {@code command=provision}: validates the customer's credentials and available
   * licenses, registers the machine, and returns the 16-byte {@code JukeANator.CTL} key.
   *
   * @param passcodeSha1 lowercase SHA-1 hex of the customer's passcode, as the client sends it
   */
  @PublicServiceMethod
  ProvisionOutcome provision(LegacyClientRequest request, String passcodeSha1);

  /**
   * Legacy {@code command=ascap} (subscription check): records a heartbeat, then returns the
   * encrypted {@code serial + paid-through quarter}, or {@code REVOKED…} / {@code ERROR…}.
   */
  @PublicServiceMethod
  String checkSubscription(LegacyClientRequest request);

  /** Legacy {@code command=heartbeat}. Returns {@code OK…} or {@code invalidCustomerEmail…}. */
  @PublicServiceMethod
  String heartbeat(LegacyClientRequest request);

  /** Legacy {@code command=getcustomername}. */
  @PublicServiceMethod
  String getCustomerName(LegacyClientRequest request);

  // ---------------------------------------------------------------- administration

  List<CustomerDto> searchCustomers(String q);

  CustomerDto getCustomer(int customerId);

  CustomerDto createCustomer(CustomerRequest request);

  CustomerDto updateCustomer(int customerId, CustomerRequest request);

  CustomerDto addLicenses(int customerId, AddLicensesRequest request);

  CustomerDto setCustomerRevoked(int customerId, boolean revoked);

  MachineDto updateMachine(int machineId, MachineRequest request);

  MachineDto extendMachine(int machineId, ExtendRequest request);
}
