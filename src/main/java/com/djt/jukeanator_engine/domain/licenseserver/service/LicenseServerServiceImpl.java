package com.djt.jukeanator_engine.domain.licenseserver.service;

import static java.util.Objects.requireNonNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.djt.jukeanator_engine.domain.common.exception.ResourceNotFoundException;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyCrypto;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyResponses;
import com.djt.jukeanator_engine.domain.license.legacy.LicenseTier;
import com.djt.jukeanator_engine.domain.license.legacy.RevocationLists;
import com.djt.jukeanator_engine.domain.license.legacy.SubscriptionQuarter;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LegacyClientRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.AddLicensesRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.CustomerDto;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.CustomerRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.ExtendRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.MachineDto;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.MachineRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.ProvisionOutcome;
import com.djt.jukeanator_engine.domain.licenseserver.model.ClientKind;
import com.djt.jukeanator_engine.domain.licenseserver.model.LicenseCustomerEntity;
import com.djt.jukeanator_engine.domain.licenseserver.model.LicenseMachineEntity;
import com.djt.jukeanator_engine.domain.licenseserver.repository.LicenseServerRepository;

/**
 * Implements the legacy jukeanator.com license commands on the new schema. The order of checks
 * and every reply text follow the legacy {@code *ServerCommand} classes, so legacy clients see no
 * difference. Deliberate fixes versus legacy:
 *
 * <ul>
 * <li>license consumption is an atomic conditional decrement (legacy read-then-wrote);</li>
 * <li>provisioning no longer overwrites the location/build columns with the key file path and
 * quarter;</li>
 * <li>no {@code .CTL} files are written on the server (the key is recomputed on demand);</li>
 * <li>revocation is data ({@code revoked} flags) plus the legacy hardcoded lists;</li>
 * <li>a missing email/serial returns a reply instead of a 500 (legacy NullPointerException).</li>
 * </ul>
 *
 * <p>Methods that mutate customers/machines are {@code synchronized}: traffic is low and a single
 * server instance runs, so this cheaply prevents two concurrent first-contact requests for the
 * same serial from racing on its unique constraint.
 */
public class LicenseServerServiceImpl implements LicenseServerService {

  private static final Logger log = LoggerFactory.getLogger(LicenseServerServiceImpl.class);

  private static final int SEARCH_LIMIT = 500;

  private final LicenseServerRepository repository;
  private final ZoneId zone;
  private final Clock clock;

  public LicenseServerServiceImpl(LicenseServerRepository repository, ZoneId zone, Clock clock) {
    this.repository = requireNonNull(repository, "repository cannot be null");
    this.zone = requireNonNull(zone, "zone cannot be null");
    this.clock = requireNonNull(clock, "clock cannot be null");
  }

  // ================================================================ legacy commands

  @Override
  public synchronized ProvisionOutcome provision(LegacyClientRequest request,
      String passcodeSha1) {

    String email = trimToNull(request.customerEmail());
    String serial = trimToNull(request.serialNumber());

    if (email != null && RevocationLists.isRevokedEmail(email)) {
      return refuse(request, "REVOKED: Unpaid subscription for customer email: " + email);
    }
    if (email == null || passcodeSha1 == null || serial == null) {
      return refuse(request, "customerEmail and serialNumber are null");
    }

    Optional<LicenseCustomerEntity> found = repository.findCustomerByEmail(email)
        .filter(c -> c.getPasscodeSha1().equalsIgnoreCase(passcodeSha1.trim()));
    if (found.isEmpty()) {
      return refuse(request, "Incorrect credentials given for customer email: " + email);
    }
    LicenseCustomerEntity customer = found.get();
    if (customer.isRevoked()) {
      return refuse(request, "REVOKED: Unpaid subscription for customer email: " + email);
    }

    String nextYearQuarter = currentQuarter().plus(4).toString();
    Instant now = clock.instant();

    Optional<LicenseMachineEntity> existing = repository.findMachineBySerial(serial);
    LicenseMachineEntity machine = existing.orElseGet(() -> {
      LicenseMachineEntity m = new LicenseMachineEntity();
      m.setCustomerId(customer.getPersistentIdentity());
      m.setSerialNumber(serial);
      m.setLastPaidQuarter(nextYearQuarter);
      m.setFirstSeenAt(now);
      return m;
    });
    if (machine.isRevoked() || RevocationLists.isRevokedMachine(serial)) {
      return refuse(request, "REVOKED: Unpaid subscription for customer email: " + email);
    }

    boolean paidForThisPeriod = existing.isPresent()
        && nextYearQuarter.equals(machine.getLastPaidQuarter());

    if (!paidForThisPeriod) {
      if (customer.getAvailableLicenses() <= 0
          || !repository.consumeLicense(customer.getPersistentIdentity())) {
        return refuse(request, "No more available licenses for given customer email: " + email);
      }
      machine.setCustomerId(customer.getPersistentIdentity());
      machine.setLastPaidQuarter(nextYearQuarter);
    }

    LicenseTier tier = LicenseTier.fromLetterForKeyGeneration(customer.getLicenseType());
    byte[] key = LegacyCrypto.licenseKeyBytes(tier, serial);

    applyClientDetails(machine, request);
    machine.setLastLicenseIssuedAt(now);
    repository.saveMachine(machine);

    log.info("Provisioned {} license for customer [{}] serial [{}] (paidForThisPeriod={})",
        tier.getDisplayName(), email, serial, paidForThisPeriod);
    return ProvisionOutcome.key(key);
  }

  @Override
  public synchronized String checkSubscription(LegacyClientRequest request) {

    // Legacy AscapServerCommand ran every heartbeat side effect first.
    recordHeartbeat(request);

    String serial = trimToNull(request.serialNumber());
    if (serial == null) {
      return LegacyResponses.ERROR;
    }
    Optional<LicenseMachineEntity> machine = repository.findMachineBySerial(serial);
    if (machine.isEmpty()) {
      return LegacyResponses.ERROR;
    }
    if (isRevoked(machine.get())) {
      log.info("Subscription check for revoked serial [{}]", serial);
      return LegacyResponses.REVOKED;
    }
    String plain = LegacyResponses.urlEncode(serial + machine.get().getLastPaidQuarter());
    return LegacyCrypto.encryptHexStrict(plain);
  }

  @Override
  public synchronized String heartbeat(LegacyClientRequest request) {
    if (!request.hasValidEmail()) {
      return LegacyResponses.INVALID_CUSTOMER_EMAIL;
    }
    recordHeartbeat(request);
    return LegacyResponses.OK;
  }

  @Override
  public String getCustomerName(LegacyClientRequest request) {
    if (!request.hasValidEmail()) {
      return LegacyResponses.INVALID_CUSTOMER_EMAIL;
    }
    return repository.findCustomerByEmail(request.customerEmail())
        .map(LicenseCustomerEntity::getCustomerName)
        .orElse(LegacyResponses.INVALID_CUSTOMER_NAME);
  }

  /**
   * Legacy HeartbeatServerCommand side effects: auto-create an unknown customer (passcode = SHA-1
   * of the email, zero licenses), register an unknown serial (subscription tiers get next year's
   * quarter), refresh location/build, and now also the heartbeat columns.
   */
  private void recordHeartbeat(LegacyClientRequest request) {

    String serial = trimToNull(request.serialNumber());
    if (!request.hasValidEmail() || serial == null) {
      // Without an email the legacy server could not attribute the machine; still refresh the
      // heartbeat of an already-known serial.
      if (serial != null) {
        repository.findMachineBySerial(serial).ifPresent(m -> {
          applyClientDetails(m, request);
          touch(m);
          repository.saveMachine(m);
        });
      }
      return;
    }

    String email = request.customerEmail().trim();
    LicenseCustomerEntity customer = repository.findCustomerByEmail(email).orElseGet(() -> {
      LicenseCustomerEntity c = new LicenseCustomerEntity();
      c.setCustomerName(firstNonBlank(request.customerName(), email));
      c.setEmail(email);
      c.setPasscodeSha1(LegacyCrypto.sha1Hex(email));
      c.setLicenseType(LicenseTier.fromLetter(request.licenseType())
          .filter(t -> t.getLetter() != null)
          .orElse(LicenseTier.L)
          .getLetter());
      c.setAvailableLicenses(0);
      c.setAutoCreated(true);
      log.info("Auto-created customer [{}] from heartbeat (legacy behavior)", email);
      return repository.saveCustomer(c);
    });

    LicenseMachineEntity machine = repository.findMachineBySerial(serial).orElseGet(() -> {
      LicenseMachineEntity m = new LicenseMachineEntity();
      m.setCustomerId(customer.getPersistentIdentity());
      m.setSerialNumber(serial);
      boolean subscription = "R".equals(request.licenseType())
          || "A".equals(request.licenseType());
      m.setLastPaidQuarter(subscription
          ? currentQuarter().plus(4).toString()
          : "");
      m.setFirstSeenAt(clock.instant());
      return m;
    });

    applyClientDetails(machine, request);
    touch(machine);
    repository.saveMachine(machine);
  }

  private void touch(LicenseMachineEntity machine) {
    machine.setLastHeartbeatAt(clock.instant());
    machine.setHeartbeatCount(machine.getHeartbeatCount() + 1);
    if (machine.getFirstSeenAt() == null) {
      machine.setFirstSeenAt(clock.instant());
    }
  }

  private static void applyClientDetails(LicenseMachineEntity machine,
      LegacyClientRequest request) {
    String location = trimToNull(request.locationName());
    if (location != null) {
      machine.setLocationName(location);
    }
    String build = trimToNull(request.buildDate());
    if (build != null) {
      machine.setBuildDate(build);
    }
    if (request.isEngineClient()) {
      machine.setClientKind(ClientKind.ENGINE);
      machine.setAppVersion(request.appVersion());
      machine.setAppMode(trimToNull(request.appMode()));
    } else {
      machine.setClientKind(ClientKind.LEGACY);
    }
  }

  private boolean isRevoked(LicenseMachineEntity machine) {
    if (machine.isRevoked() || RevocationLists.isRevokedMachine(machine.getSerialNumber())) {
      return true;
    }
    return repository.findCustomerById(machine.getCustomerId())
        .map(c -> c.isRevoked() || RevocationLists.isRevokedEmail(c.getEmail()))
        .orElse(false);
  }

  private static ProvisionOutcome refuse(LegacyClientRequest request, String message) {
    log.info("Provisioning refused for serial [{}]: {}", request.serialNumber(), message);
    return ProvisionOutcome.refused(message);
  }

  // ================================================================ administration

  @Override
  public List<CustomerDto> searchCustomers(String q) {
    return repository.searchCustomers(q, SEARCH_LIMIT).stream().map(this::toDto).toList();
  }

  @Override
  public CustomerDto getCustomer(int customerId) {
    return toDto(loadCustomer(customerId));
  }

  @Override
  public synchronized CustomerDto createCustomer(CustomerRequest request) {
    String email = requireText(request.email(), "email").toLowerCase(Locale.ROOT);
    if (repository.findCustomerByEmail(email).isPresent()) {
      throw new IllegalArgumentException("A customer with email " + email + " already exists");
    }
    LicenseCustomerEntity c = new LicenseCustomerEntity();
    c.setEmail(email);
    c.setCustomerName(requireText(request.customerName(), "customerName"));
    c.setPasscodeSha1(LegacyCrypto.sha1Hex(requireText(request.passcode(), "passcode")));
    c.setLicenseType(requireLicenseLetter(request.licenseType()));
    c.setAvailableLicenses(request.availableLicenses() == null ? 0
        : requireNonNegative(request.availableLicenses(), "availableLicenses"));
    c.setPhoneNumber(trimToNull(request.phoneNumber()));
    c.setAddress(trimToNull(request.address()));
    c.setNotes(trimToNull(request.notes()));
    return toDto(repository.saveCustomer(c));
  }

  @Override
  public synchronized CustomerDto updateCustomer(int customerId, CustomerRequest request) {
    LicenseCustomerEntity c = loadCustomer(customerId);
    if (request.email() != null) {
      String email = requireText(request.email(), "email").toLowerCase(Locale.ROOT);
      repository.findCustomerByEmail(email)
          .filter(other -> !other.getPersistentIdentity().equals(c.getPersistentIdentity()))
          .ifPresent(other -> {
            throw new IllegalArgumentException(
                "A customer with email " + email + " already exists");
          });
      c.setEmail(email);
    }
    if (request.customerName() != null) {
      c.setCustomerName(requireText(request.customerName(), "customerName"));
    }
    if (request.passcode() != null && !request.passcode().isBlank()) {
      c.setPasscodeSha1(LegacyCrypto.sha1Hex(request.passcode()));
      c.setAutoCreated(false);
    }
    if (request.licenseType() != null) {
      c.setLicenseType(requireLicenseLetter(request.licenseType()));
    }
    if (request.availableLicenses() != null) {
      c.setAvailableLicenses(requireNonNegative(request.availableLicenses(), "availableLicenses"));
    }
    if (request.phoneNumber() != null) {
      c.setPhoneNumber(trimToNull(request.phoneNumber()));
    }
    if (request.address() != null) {
      c.setAddress(trimToNull(request.address()));
    }
    if (request.notes() != null) {
      c.setNotes(trimToNull(request.notes()));
    }
    return toDto(repository.saveCustomer(c));
  }

  @Override
  public synchronized CustomerDto addLicenses(int customerId, AddLicensesRequest request) {
    LicenseCustomerEntity c = loadCustomer(customerId);
    int total = c.getAvailableLicenses() + request.count();
    c.setAvailableLicenses(requireNonNegative(total, "availableLicenses"));
    return toDto(repository.saveCustomer(c));
  }

  @Override
  public synchronized CustomerDto setCustomerRevoked(int customerId, boolean revoked) {
    LicenseCustomerEntity c = loadCustomer(customerId);
    c.setRevoked(revoked);
    return toDto(repository.saveCustomer(c));
  }

  @Override
  public synchronized MachineDto updateMachine(int machineId, MachineRequest request) {
    LicenseMachineEntity m = loadMachine(machineId);
    if (request.lastPaidQuarter() != null) {
      String q = request.lastPaidQuarter().trim();
      if (!q.isEmpty()) {
        requireAcceptableQuarter(SubscriptionQuarter.parse(q));
      }
      m.setLastPaidQuarter(q);
    }
    if (request.locationName() != null) {
      m.setLocationName(trimToNull(request.locationName()));
    }
    if (request.customerId() != null) {
      loadCustomer(request.customerId());
      m.setCustomerId(request.customerId());
    }
    if (request.revoked() != null) {
      m.setRevoked(request.revoked());
    }
    return toDto(repository.saveMachine(m));
  }

  @Override
  public synchronized MachineDto extendMachine(int machineId, ExtendRequest request) {
    if (request.quarters() <= 0) {
      throw new IllegalArgumentException("quarters must be positive");
    }
    LicenseMachineEntity m = loadMachine(machineId);
    SubscriptionQuarter current = currentQuarter();
    SubscriptionQuarter base = current;
    if (SubscriptionQuarter.isValid(m.getLastPaidQuarter())) {
      SubscriptionQuarter paid = SubscriptionQuarter.parse(m.getLastPaidQuarter());
      if (paid.compareTo(current) > 0) {
        base = paid;
      }
    }
    SubscriptionQuarter extended = base.plus(request.quarters());
    requireAcceptableQuarter(extended);
    m.setLastPaidQuarter(extended.toString());
    return toDto(repository.saveMachine(m));
  }

  /**
   * Clients only accept a paid-through quarter between the current quarter and four quarters
   * ahead (legacy client rule); anything later would make every subscription check fail.
   */
  private SubscriptionQuarter currentQuarter() {
    return SubscriptionQuarter.of(LocalDate.now(clock.withZone(zone)));
  }

  private void requireAcceptableQuarter(SubscriptionQuarter q) {
    SubscriptionQuarter max = currentQuarter().plus(4);
    if (q.compareTo(max) > 0) {
      throw new IllegalArgumentException("Paid-through quarter " + q + " is beyond " + max
          + "; clients reject quarters more than four quarters ahead");
    }
  }

  private LicenseCustomerEntity loadCustomer(int id) {
    return repository.findCustomerById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Customer " + id + " not found"));
  }

  private LicenseMachineEntity loadMachine(int id) {
    return repository.findMachineById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Machine " + id + " not found"));
  }

  private CustomerDto toDto(LicenseCustomerEntity c) {
    List<MachineDto> machines = c.getPersistentIdentity() == null ? List.of()
        : repository.findMachinesByCustomer(c.getPersistentIdentity()).stream()
            .map(this::toDto).toList();
    return new CustomerDto(c.getPersistentIdentity(), c.getCustomerName(), c.getEmail(),
        c.getPhoneNumber(), c.getAddress(), c.getNotes(), c.getLicenseType(),
        c.getAvailableLicenses(), c.isRevoked(), c.isAutoCreated(), c.getDateAdded(), machines);
  }

  private MachineDto toDto(LicenseMachineEntity m) {
    return new MachineDto(m.getPersistentIdentity(), m.getCustomerId(), m.getSerialNumber(),
        m.getLastPaidQuarter(), m.getLocationName(), m.getBuildDate(), m.isRevoked(),
        m.getClientKind().name(), m.getAppVersion(), m.getAppMode(), m.getFirstSeenAt(),
        m.getLastHeartbeatAt(), m.getHeartbeatCount(), m.getLastLicenseIssuedAt());
  }

  // ================================================================ helpers

  private static String requireLicenseLetter(String letter) {
    return LicenseTier.fromLetter(letter == null ? null : letter.trim().toUpperCase(Locale.ROOT))
        .map(LicenseTier::getLetter)
        .orElseThrow(() -> new IllegalArgumentException(
            "licenseType must be one of R, A, C, P, L"));
  }

  private static String requireText(String value, String field) {
    String v = trimToNull(value);
    if (v == null) {
      throw new IllegalArgumentException(field + " is required");
    }
    return v;
  }

  private static int requireNonNegative(int value, String field) {
    if (value < 0) {
      throw new IllegalArgumentException(field + " cannot be negative");
    }
    return value;
  }

  private static String firstNonBlank(String a, String b) {
    String t = trimToNull(a);
    return t != null ? t : b;
  }

  private static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}
