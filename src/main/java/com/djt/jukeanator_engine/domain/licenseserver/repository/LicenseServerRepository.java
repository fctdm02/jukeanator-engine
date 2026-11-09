package com.djt.jukeanator_engine.domain.licenseserver.repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.djt.jukeanator_engine.domain.licenseserver.model.LicenseCustomerEntity;
import com.djt.jukeanator_engine.domain.licenseserver.model.LicenseMachineEntity;

/**
 * Persistence for the license server's customers and machines. Master mode always runs with
 * {@code app.repository-type=jpa}, so there is only a JPA implementation.
 */
public interface LicenseServerRepository {

  /** Runs {@code work} in one database transaction. */
  <T> T inTransaction(Supplier<T> work);

  Optional<LicenseCustomerEntity> findCustomerById(int customerId);

  /** Case-insensitive email lookup. */
  Optional<LicenseCustomerEntity> findCustomerByEmail(String email);

  /** Customers whose name, email or one of whose machines' serial numbers contains {@code q}. */
  List<LicenseCustomerEntity> searchCustomers(String q, int limit);

  Optional<LicenseMachineEntity> findMachineById(int machineId);

  Optional<LicenseMachineEntity> findMachineBySerial(String serialNumber);

  List<LicenseMachineEntity> findMachinesByCustomer(int customerId);

  LicenseCustomerEntity saveCustomer(LicenseCustomerEntity customer);

  LicenseMachineEntity saveMachine(LicenseMachineEntity machine);

  /**
   * Atomically decrements a customer's available licenses if at least one remains.
   *
   * @return true if a license was consumed
   */
  boolean consumeLicense(int customerId);
}
