package com.djt.jukeanator_engine.domain.licenseserver.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.djt.jukeanator_engine.domain.license.legacy.LegacyCrypto;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyResponses;
import com.djt.jukeanator_engine.domain.license.legacy.LicenseTier;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LegacyClientRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.CustomerRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.ExtendRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.ProvisionOutcome;
import com.djt.jukeanator_engine.domain.licenseserver.model.LicenseCustomerEntity;
import com.djt.jukeanator_engine.domain.licenseserver.model.LicenseMachineEntity;
import com.djt.jukeanator_engine.domain.licenseserver.repository.LicenseServerRepository;

class LicenseServerServiceImplTest {

  private static final ZoneId ZONE = ZoneId.of("America/Detroit");
  private static final Clock CLOCK =
      Clock.fixed(ZonedDateTime.of(2026, 11, 8, 12, 0, 0, 0, ZONE).toInstant(), ZONE);
  private static final String NEXT_YEAR_QUARTER = "4Q2027";
  private static final String EMAIL = "owner@example.com";
  private static final String PASSCODE_SHA1 = LegacyCrypto.sha1Hex("jukeanator22");

  private InMemoryRepository repository;
  private LicenseServerServiceImpl service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryRepository();
    service = new LicenseServerServiceImpl(repository, ZONE, CLOCK);
  }

  @Test
  void provision_newMachine_returnsKeyConsumesLicenseAndStampsQuarter() {
    customer("A", 2);
    ProvisionOutcome outcome = service.provision(request("661793536", "0"), PASSCODE_SHA1);

    assertTrue(outcome.isSuccess());
    assertArrayEquals(LegacyCrypto.licenseKeyBytes(LicenseTier.CD, "661793536"),
        outcome.licenseKey());
    assertEquals(1, repository.customers.get(0).getAvailableLicenses());
    assertEquals(NEXT_YEAR_QUARTER,
        repository.findMachineBySerial("661793536").orElseThrow().getLastPaidQuarter());
  }

  @Test
  void provision_sameMachineSamePeriod_doesNotConsumeAgain() {
    customer("C", 1);
    assertTrue(service.provision(request("S1", "1"), PASSCODE_SHA1).isSuccess());
    assertTrue(service.provision(request("S1", "1"), PASSCODE_SHA1).isSuccess());
    assertEquals(0, repository.customers.get(0).getAvailableLicenses());
  }

  @Test
  void provision_noLicensesLeft_isRefusedWithLegacyText() {
    customer("C", 0);
    ProvisionOutcome outcome = service.provision(request("S1", "1"), PASSCODE_SHA1);
    assertFalse(outcome.isSuccess());
    assertEquals("No more available licenses for given customer email: " + EMAIL,
        outcome.message());
  }

  @Test
  void provision_wrongPasscode_isRefusedWithLegacyText() {
    customer("C", 5);
    assertEquals("Incorrect credentials given for customer email: " + EMAIL,
        service.provision(request("S1", "1"), "bad").message());
  }

  @Test
  void provision_legacyRevokedEmail_isRefused() {
    ProvisionOutcome outcome = service.provision(new LegacyClientRequest("S1", "C",
        "eftobias@gmail.com", null, "", "", null, null), PASSCODE_SHA1);
    assertEquals("REVOKED: Unpaid subscription for customer email: eftobias@gmail.com",
        outcome.message());
  }

  @Test
  void checkSubscription_returnsEncryptedSerialAndQuarter() {
    customer("A", 1);
    service.provision(request("{GUID-1}", "0"), PASSCODE_SHA1);

    String reply = service.checkSubscription(request("{GUID-1}", "0"));

    assertEquals(LegacyResponses.urlEncode("{GUID-1}" + NEXT_YEAR_QUARTER),
        LegacyCrypto.decryptHex(reply));
  }

  @Test
  void checkSubscription_unknownSerial_isError_andRevokedIsRevoked() {
    assertEquals(LegacyResponses.ERROR,
        service.checkSubscription(new LegacyClientRequest("nobody", "A", null, null, "", "",
            null, null)));
    customer("A", 1);
    service.provision(request("S9", "0"), PASSCODE_SHA1);
    repository.findMachineBySerial("S9").orElseThrow().setRevoked(true);
    assertEquals(LegacyResponses.REVOKED, service.checkSubscription(request("S9", "0")));
  }

  @Test
  void heartbeat_unknownEmail_autoCreatesCustomerLikeLegacy() {
    assertEquals(LegacyResponses.OK, service.heartbeat(new LegacyClientRequest("S5", "C",
        "new@example.com", "New Bar", "Main St", "01-27-2024", null, null)));

    LicenseCustomerEntity created = repository.findCustomerByEmail("new@example.com")
        .orElseThrow();
    assertTrue(created.isAutoCreated());
    assertEquals(LegacyCrypto.sha1Hex("new@example.com"), created.getPasscodeSha1());
    LicenseMachineEntity machine = repository.findMachineBySerial("S5").orElseThrow();
    assertEquals("", machine.getLastPaidQuarter());
    assertEquals(1, machine.getHeartbeatCount());
    assertEquals("Main St", machine.getLocationName());
  }

  @Test
  void heartbeat_invalidEmail() {
    assertEquals(LegacyResponses.INVALID_CUSTOMER_EMAIL,
        service.heartbeat(new LegacyClientRequest("S5", "C", "nope", null, "", "", null, null)));
  }

  @Test
  void customerName() {
    customer("C", 1);
    assertEquals("Owner", service.getCustomerName(request("S1", "1")));
    assertEquals(LegacyResponses.INVALID_CUSTOMER_NAME, service.getCustomerName(
        new LegacyClientRequest("S1", "1", "x@y.com", null, "", "", null, null)));
  }

  @Test
  void extendMachine_capsAtFourQuartersAhead() {
    customer("A", 1);
    service.provision(request("S1", "0"), PASSCODE_SHA1);
    LicenseMachineEntity machine = repository.findMachineBySerial("S1").orElseThrow();

    assertThrows(IllegalArgumentException.class,
        () -> service.extendMachine(machine.getPersistentIdentity(), new ExtendRequest(4)));

    machine.setLastPaidQuarter("2Q2026");
    assertEquals(NEXT_YEAR_QUARTER, service.extendMachine(machine.getPersistentIdentity(),
        new ExtendRequest(4)).lastPaidQuarter());
  }

  @Test
  void createCustomer_storesSha1Passcode() {
    service.createCustomer(new CustomerRequest("Bar", "Bar@Example.com", "pw", null, null, null,
        "a", 3));
    LicenseCustomerEntity c = repository.findCustomerByEmail("bar@example.com").orElseThrow();
    assertEquals(LegacyCrypto.sha1Hex("pw"), c.getPasscodeSha1());
    assertEquals("A", c.getLicenseType());
    assertNull(c.getPhoneNumber());
  }

  // ---------------------------------------------------------------- helpers

  private LegacyClientRequest request(String serial, String wireType) {
    return new LegacyClientRequest(serial, LicenseTier.normalizeWireLicenseType(wireType), EMAIL,
        null, "", "", null, null);
  }

  private void customer(String type, int licenses) {
    LicenseCustomerEntity c = new LicenseCustomerEntity();
    c.setCustomerName("Owner");
    c.setEmail(EMAIL);
    c.setPasscodeSha1(PASSCODE_SHA1);
    c.setLicenseType(type);
    c.setAvailableLicenses(licenses);
    repository.saveCustomer(c);
  }

  /** Minimal in-memory repository (entities are shared, so saves are implicit). */
  private static final class InMemoryRepository implements LicenseServerRepository {
    final List<LicenseCustomerEntity> customers = new ArrayList<>();
    final List<LicenseMachineEntity> machines = new ArrayList<>();
    private int nextId = 1;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
      return work.get();
    }

    @Override
    public Optional<LicenseCustomerEntity> findCustomerById(int id) {
      return customers.stream().filter(c -> c.getPersistentIdentity() == id).findFirst();
    }

    @Override
    public Optional<LicenseCustomerEntity> findCustomerByEmail(String email) {
      return customers.stream()
          .filter(c -> c.getEmail().equalsIgnoreCase(email.trim().toLowerCase(Locale.ROOT)))
          .findFirst();
    }

    @Override
    public List<LicenseCustomerEntity> searchCustomers(String q, int limit) {
      return customers;
    }

    @Override
    public Optional<LicenseMachineEntity> findMachineById(int id) {
      return machines.stream().filter(m -> m.getPersistentIdentity() == id).findFirst();
    }

    @Override
    public Optional<LicenseMachineEntity> findMachineBySerial(String serial) {
      return machines.stream().filter(m -> m.getSerialNumber().equals(serial)).findFirst();
    }

    @Override
    public List<LicenseMachineEntity> findMachinesByCustomer(int customerId) {
      return machines.stream().filter(m -> m.getCustomerId() == customerId).toList();
    }

    @Override
    public LicenseCustomerEntity saveCustomer(LicenseCustomerEntity c) {
      if (c.getPersistentIdentity() == null) {
        c.setPersistentIdentity(nextId++);
        customers.add(c);
      }
      return c;
    }

    @Override
    public LicenseMachineEntity saveMachine(LicenseMachineEntity m) {
      if (m.getPersistentIdentity() == null) {
        m.setPersistentIdentity(nextId++);
        machines.add(m);
      }
      return m;
    }

    @Override
    public boolean consumeLicense(int customerId) {
      LicenseCustomerEntity c = findCustomerById(customerId).orElseThrow();
      if (c.getAvailableLicenses() <= 0) {
        return false;
      }
      c.setAvailableLicenses(c.getAvailableLicenses() - 1);
      return true;
    }
  }
}
