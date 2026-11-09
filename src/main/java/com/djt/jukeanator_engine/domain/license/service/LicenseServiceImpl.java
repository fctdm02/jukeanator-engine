package com.djt.jukeanator_engine.domain.license.service;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClientException;

import com.djt.jukeanator_engine.domain.license.client.LicenseServerClient;
import com.djt.jukeanator_engine.domain.license.client.LicenseServerClient.ClientIdentity;
import com.djt.jukeanator_engine.domain.license.client.LicenseServerClient.ProvisionReply;
import com.djt.jukeanator_engine.domain.license.config.LicenseProperties;
import com.djt.jukeanator_engine.domain.license.event.LicenseStatusChangedEvent;
import com.djt.jukeanator_engine.domain.license.legacy.ExtendCode;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyCrypto;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyCtlFiles;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyResponses;
import com.djt.jukeanator_engine.domain.license.legacy.LicenseTier;
import com.djt.jukeanator_engine.domain.license.legacy.RevocationLists;
import com.djt.jukeanator_engine.domain.license.legacy.SubscriptionQuarter;
import com.djt.jukeanator_engine.domain.license.model.LicenseStatus;

/**
 * Implements the legacy MP3Jukeboxx license checks ({@code checkRuntime()},
 * {@code performSubscriptionValidation()}, {@code retrieveProvisionedLicenseKey()}) on the same
 * files and wire protocol, with these deliberate behavior fixes:
 *
 * <ul>
 * <li>a subscription "not paid" reply from the server wins over a still-valid local file (the
 * legacy client let the file override it at startup);</li>
 * <li>the hours written to a new extend code match its quarter (legacy wrote +4 quarters but only
 * 3 quarters of hours);</li>
 * <li>the startup "hours since last run" deduction works (it never ran in the legacy client);</li>
 * <li>a provisioned key is verified against this computer before it replaces the current one;</li>
 * <li>mismatches disable commercial operation and are shown to the operator instead of exiting.</li>
 * </ul>
 *
 * <p>All state changes are {@code synchronized}; the scheduler thread, startup and the UI may call
 * concurrently.
 */
public class LicenseServiceImpl implements LicenseService {

  private static final Logger log = LoggerFactory.getLogger(LicenseServiceImpl.class);

  static final String RUNTIME_STAMP_FILENAME = "License.STAMP";
  private static final String SUPPORT = "Please contact JukeANator support (tmyers1@yahoo.com).";
  private static final int WARNING_HOURS = 14 * 24;
  /** {@code getengineversion} reply: {@code <version>|<download url>}. */
  private static final Pattern ENGINE_VERSION_REPLY =
      Pattern.compile("^([0-9A-Za-z][0-9A-Za-z._+-]{0,63})\\|(\\S{0,2048})$");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy",
      Locale.US);

  private final LicenseProperties properties;
  private final MachineIdentityProvider identity;
  private final LicenseServerClient client;
  private final ApplicationEventPublisher events;
  private final Path dataDir;
  private final Path configFile;
  private final Clock clock;
  private final ConfigPersister configPersister;

  /** Persists {@code license.*} keys to the external config (null-safe no-op when unknown). */
  @FunctionalInterface
  public interface ConfigPersister {
    void persist(Path configFile, Map<String, String> values);
  }

  // ---------------------------------------------------------------- state

  private volatile LicenseStatus status = LicenseStatus.initial();

  private LicenseTier licensedTier = LicenseTier.D;
  private String machineId;
  private Path licenseDir;

  private ExtendCode extendCode;
  private boolean subscriptionExpired;
  private String messageCode;
  private String message;
  private boolean warning;
  private boolean revoked;
  private Instant lastServerCheckAt;
  private String latestEngineVersion;
  private String engineDownloadUrl;

  public LicenseServiceImpl(LicenseProperties properties, MachineIdentityProvider identity,
      LicenseServerClient client, ApplicationEventPublisher events, Path dataDir, Path configFile,
      Clock clock, ConfigPersister configPersister) {
    this.properties = requireNonNull(properties, "properties cannot be null");
    this.identity = requireNonNull(identity, "identity cannot be null");
    this.client = requireNonNull(client, "client cannot be null");
    this.events = requireNonNull(events, "events cannot be null");
    this.dataDir = requireNonNull(dataDir, "dataDir cannot be null");
    this.configFile = configFile;
    this.clock = requireNonNull(clock, "clock cannot be null");
    this.configPersister = requireNonNull(configPersister, "configPersister cannot be null");
  }

  // ================================================================ queries

  @Override
  public LicenseStatus getStatus() {
    return status;
  }

  @Override
  public boolean isCommercialEnabled() {
    return status.commercialEnabled();
  }

  @Override
  public String getCustomerEmail() {
    return trimToNull(properties.getCustomerEmail());
  }

  @Override
  public String getCustomerName() {
    return trimToNull(properties.getCustomerName());
  }

  @Override
  public String getLocationName() {
    return trimToNull(properties.getLocationName());
  }

  // ================================================================ checks

  @Override
  public synchronized LicenseStatus refreshLocal() {
    checkRuntime();
    if (licensedTier.isSubscription()) {
      applyRuntimeDelta();
      evaluateExtendCode(false);
    } else {
      clearSubscriptionState();
    }
    return publish();
  }

  /** Legacy {@code checkRuntime()}: find the license key and match it to this machine. */
  private void checkRuntime() {
    licenseDir = resolveLicenseDir();
    List<String> candidates = identity.candidateIds();
    licensedTier = LicenseTier.D;
    machineId = null;

    List<Path> keyFiles = new ArrayList<>();
    keyFiles.add(licenseDir.resolve(LegacyCtlFiles.LICENSE_KEY_FILENAME));
    for (int i = 1; i <= 10; i++) {
      keyFiles.add(licenseDir.resolve("JukeANator" + i + ".CTL"));
    }

    search:
    for (Path keyFile : keyFiles) {
      Optional<byte[]> key = LegacyCtlFiles.readLicenseKey(keyFile);
      if (key.isEmpty()) {
        continue;
      }
      for (String candidate : candidates) {
        Optional<LicenseTier> tier = matchTier(key.get(), candidate);
        if (tier.isPresent()) {
          licensedTier = tier.get();
          machineId = candidate;
          log.info("License {} matched: {} for machine id {}", keyFile.getFileName(),
              licensedTier.getDisplayName(), machineId);
          break search;
        }
      }
      log.warn("License file {} does not match this computer (machine ids {})", keyFile,
          candidates);
    }
    if (machineId == null) {
      machineId = identity.provisioningId();
    }
    revoked = RevocationLists.isRevokedMachine(machineId)
        || RevocationLists.isRevokedEmail(properties.getCustomerEmail());
  }

  private static Optional<LicenseTier> matchTier(byte[] key, String candidate) {
    for (LicenseTier tier : LicenseTier.MATCH_ORDER) {
      if (Arrays.equals(key, LegacyCrypto.licenseKeyBytes(tier, candidate))) {
        return Optional.of(tier);
      }
    }
    return Optional.empty();
  }

  @Override
  public synchronized LicenseStatus validateSubscription() {
    if (!licensedTier.isSubscription()) {
      return status;
    }
    String reply;
    try {
      reply = client.checkSubscription(clientIdentity());
    } catch (RuntimeException e) {
      log.info("License server unreachable for subscription check ({}); using {}", e.getMessage(),
          LegacyCtlFiles.EXTEND_CODE_FILENAME);
      evaluateExtendCode(true);
      return publish();
    }

    if (reply.startsWith("REVOKED")) {
      markServerExpired("010", "This license has been revoked. " + SUPPORT);
      return publish();
    }

    Optional<SubscriptionQuarter> paid = decodePaidQuarter(reply);
    if (paid == null) {
      // Not a subscription reply at all (e.g. unknown serial): legacy fell back to the file.
      log.warn("Unexpected subscription check reply [{}]; using {}", reply,
          LegacyCtlFiles.EXTEND_CODE_FILENAME);
      evaluateExtendCode(true);
      return publish();
    }
    lastServerCheckAt = clock.instant();

    if (paid.isEmpty()) {
      markServerExpired("003", "Your license subscription has expired. Please renew it to"
          + " restore commercial operation (bill acceptor and card reader). " + SUPPORT);
      return publish();
    }

    SubscriptionQuarter quarter = paid.get();
    int hours = quarter.hoursUntilExpiry(now());
    writeExtendCode(new ExtendCode(quarter.toString(), hours, machineId,
        nullToEmpty(properties.getCustomerEmail())));
    evaluateExtendCode(false);
    refreshCustomerName();
    return publish();
  }

  /**
   * Decrypts a subscription reply. Returns null if it is not a valid ciphertext, empty if it is
   * valid but carries no acceptable quarter (not paid), else the paid-through quarter.
   */
  private Optional<SubscriptionQuarter> decodePaidQuarter(String reply) {
    String plain;
    try {
      plain = LegacyCrypto.decryptHex(reply.trim());
    } catch (IllegalStateException e) {
      return null;
    }
    if (plain == null || plain.isEmpty()) {
      return null;
    }
    String prefix = LegacyResponses.urlEncode(machineId);
    SubscriptionQuarter current = SubscriptionQuarter.of(LocalDate.now(clock.withZone(zone())));
    for (int k = 0; k <= 4; k++) {
      SubscriptionQuarter q = current.plus(k);
      if (plain.equals(prefix + q)) {
        return Optional.of(q);
      }
    }
    log.info("Subscription check: server reply does not cover the current quarter ({})", plain);
    return Optional.empty();
  }

  /** The server says the subscription is not paid: that wins over any local file. */
  private void markServerExpired(String code, String text) {
    ExtendCode existing = readExtendCodeQuietly();
    String quarter = existing != null ? existing.expirationQuarter()
        : SubscriptionQuarter.of(LocalDate.now(clock.withZone(zone()))).plus(-1).toString();
    writeExtendCode(new ExtendCode(quarter, 0, machineId,
        nullToEmpty(properties.getCustomerEmail())));
    extendCode = readExtendCodeQuietly();
    subscriptionExpired = true;
    setMessage(code, text, false);
  }

  /**
   * Legacy {@code readExtendCodeFile()} checks (444/555/888/999, machine and email match). When
   * {@code offline}, the legacy offline codes (005/006/007) are used.
   */
  private void evaluateExtendCode(boolean offline) {
    clearSubscriptionState();
    ExtendCode code = readExtendCodeQuietly();
    if (code == null) {
      subscriptionExpired = true;
      setMessage("005", "Subscription mode requires a valid " + LegacyCtlFiles.EXTEND_CODE_FILENAME
          + " file" + (offline ? " and the license server could not be reached" : "")
          + ". Connect to the internet so the subscription can be checked. " + SUPPORT, false);
      return;
    }
    extendCode = code;

    if (!Objects.equals(code.machineId(), machineId)) {
      subscriptionExpired = true;
      setMessage("002", "The subscription file does not belong to this computer. " + SUPPORT,
          false);
      return;
    }
    String configuredEmail = trimToNull(properties.getCustomerEmail());
    String storedEmail = trimToNull(code.customerEmail());
    if (configuredEmail == null && storedEmail != null) {
      persist(Map.of("customer-email", storedEmail), p -> p.setCustomerEmail(storedEmail));
    } else if (storedEmail != null && !storedEmail.equalsIgnoreCase(configuredEmail)) {
      subscriptionExpired = true;
      setMessage("002", "The registered customer email (" + configuredEmail
          + ") does not match this computer's subscription. Please correct it. " + SUPPORT,
          false);
      return;
    }

    SubscriptionQuarter quarter;
    try {
      quarter = SubscriptionQuarter.parse(code.expirationQuarter());
    } catch (IllegalArgumentException e) {
      subscriptionExpired = true;
      setMessage("005", "The subscription file is damaged. " + SUPPORT, false);
      return;
    }
    ZonedDateTime now = now();
    String expiry = DATE.format(quarter.lastDay());

    if (code.hoursRemaining() <= 0 || quarter.isExpired(now)) {
      subscriptionExpired = true;
      setMessage(offline ? "007" : (code.hoursRemaining() <= 0 ? "444" : "999"),
          "Your license subscription expired on " + expiry + ". Please renew it to restore"
              + " commercial operation (bill acceptor and card reader). " + SUPPORT, false);
    } else if (code.hoursRemaining() <= WARNING_HOURS || quarter.isInWarningWindow(now)) {
      setMessage(offline ? "006" : (quarter.isInWarningWindow(now) ? "888" : "555"),
          "Your license subscription expires on " + expiry + ". Please renew it to keep"
              + " commercial operation. " + SUPPORT, true);
    }
  }

  /**
   * Fixed legacy "delta hours" check: deduct the hours the application was not running (beyond
   * one) since the hour counter was last advanced.
   */
  private void applyRuntimeDelta() {
    Path stamp = licenseDir.resolve(RUNTIME_STAMP_FILENAME);
    try {
      ExtendCode code = readExtendCodeQuietly();
      if (code != null && Files.exists(stamp)) {
        long delta = Duration.between(Files.getLastModifiedTime(stamp).toInstant(),
            clock.instant()).toHours();
        if (delta > 1) {
          int hours = Math.max(0, code.hoursRemaining() - (int) (delta - 1));
          log.info("Application was down ~{} hours; subscription hours {} -> {}", delta,
              code.hoursRemaining(), hours);
          writeExtendCode(code.withHoursRemaining(hours));
        }
      }
      touch(stamp);
    } catch (IOException e) {
      log.warn("Could not apply runtime delta using {}: {}", stamp, e.getMessage());
    }
  }

  @Override
  public synchronized void hourlyTick() {
    if (!licensedTier.isSubscription()) {
      return;
    }
    ExtendCode code = readExtendCodeQuietly();
    if (code != null && code.hoursRemaining() > 0) {
      writeExtendCode(code.withHoursRemaining(code.hoursRemaining() - 1));
    }
    try {
      touch(licenseDir.resolve(RUNTIME_STAMP_FILENAME));
    } catch (IOException e) {
      log.debug("Could not touch runtime stamp: {}", e.getMessage());
    }
    // Keep the server's verdict (not paid / revoked) until the next server check.
    boolean serverSaidExpired = "003".equals(messageCode) || "010".equals(messageCode);
    if (!serverSaidExpired) {
      evaluateExtendCode(false);
    }
    publish();
  }

  // ================================================================ provisioning

  @Override
  public synchronized LicenseStatus provision(String customerEmail, String passcode,
      String locationName) {

    String email = trimToNull(customerEmail);
    if (email == null || passcode == null || passcode.isBlank()) {
      throw new LicenseException("Please enter your registered email address and passcode.");
    }
    String sid = identity.provisioningId();
    if (sid == null) {
      throw new LicenseException("This computer's machine id could not be determined. " + SUPPORT);
    }
    String location = firstNonBlank(locationName, properties.getLocationName());

    ProvisionReply reply;
    try {
      reply = client.provision(email, LegacyCrypto.sha1Hex(passcode), sid, location);
    } catch (RestClientException e) {
      throw new LicenseException("The license server could not be reached. Please check the"
          + " internet connection and try again.", e);
    }
    if (!reply.hasKey()) {
      throw new LicenseException(reply.message() == null || reply.message().isBlank()
          ? "The license server refused the request. " + SUPPORT
          : reply.message());
    }
    LicenseTier tier = matchTier(reply.licenseKey(), sid).orElseThrow(() -> new LicenseException(
        "The license server returned a key that does not match this computer. " + SUPPORT));

    licenseDir = resolveLicenseDir();
    try {
      LegacyCtlFiles.writeLicenseKey(licenseDir.resolve(LegacyCtlFiles.LICENSE_KEY_FILENAME),
          reply.licenseKey());
    } catch (IOException e) {
      throw new LicenseException("The license key could not be saved to " + licenseDir + ": "
          + e.getMessage(), e);
    }
    log.info("Provisioned {} license for {} (machine id {})", tier.getDisplayName(), email, sid);

    Map<String, String> updates = new LinkedHashMap<>();
    updates.put("customer-email", email);
    if (location != null) {
      updates.put("location-name", location);
    }
    if (trimToNull(properties.getAutoProvisionEmail()) != null
        || trimToNull(properties.getAutoProvisionPasscode()) != null) {
      updates.put("auto-provision-email", null);
      updates.put("auto-provision-passcode", null);
    }
    persist(updates, p -> {
      p.setCustomerEmail(email);
      if (location != null) {
        p.setLocationName(location);
      }
      p.setAutoProvisionEmail(null);
      p.setAutoProvisionPasscode(null);
    });

    if (tier.isSubscription()) {
      SubscriptionQuarter quarter =
          SubscriptionQuarter.of(LocalDate.now(clock.withZone(zone()))).plus(4);
      writeExtendCode(new ExtendCode(quarter.toString(), quarter.hoursUntilExpiry(now()), sid,
          email));
    }
    refreshLocal();
    if (licensedTier.isSubscription()) {
      validateSubscription();
    } else {
      refreshCustomerName();
    }
    sendHeartbeatQuietly();
    return status;
  }

  @Override
  public synchronized LicenseStatus importLicenseFile(Path file) {
    String name = file.getFileName() == null ? "" : file.getFileName().toString();
    if (!name.toLowerCase(Locale.ROOT).endsWith(".ctl")
        || !name.toLowerCase(Locale.ROOT).contains("jukeanator")) {
      throw new LicenseException("Please choose your JukeANator.CTL license file.");
    }
    byte[] key = LegacyCtlFiles.readLicenseKey(file).orElseThrow(
        () -> new LicenseException(name + " is not a valid license file."));
    boolean matches = identity.candidateIds().stream()
        .anyMatch(id -> matchTier(key, id).isPresent());
    if (!matches) {
      throw new LicenseException(name + " was issued for a different computer. " + SUPPORT);
    }
    licenseDir = resolveLicenseDir();
    try {
      LegacyCtlFiles.writeLicenseKey(licenseDir.resolve(LegacyCtlFiles.LICENSE_KEY_FILENAME), key);
      Files.deleteIfExists(licenseDir.resolve(LegacyCtlFiles.EXTEND_CODE_FILENAME));
    } catch (IOException e) {
      throw new LicenseException("The license key could not be saved to " + licenseDir + ": "
          + e.getMessage(), e);
    }
    refreshLocal();
    if (licensedTier.isSubscription()) {
      validateSubscription();
    } else {
      refreshCustomerName();
    }
    return status;
  }

  @Override
  public synchronized LicenseStatus updateRegistrationDetails(String customerEmail,
      String customerName, String locationName) {
    Map<String, String> updates = new LinkedHashMap<>();
    String email = trimToNull(customerEmail);
    String name = trimToNull(customerName);
    String location = trimToNull(locationName);
    if (email != null) {
      updates.put("customer-email", email);
    }
    if (name != null) {
      updates.put("customer-name", name);
    }
    if (location != null) {
      updates.put("location-name", location);
    }
    if (!updates.isEmpty()) {
      persist(updates, p -> {
        if (email != null) {
          p.setCustomerEmail(email);
        }
        if (name != null) {
          p.setCustomerName(name);
        }
        if (location != null) {
          p.setLocationName(location);
        }
      });
    }
    refreshLocal();
    if (licensedTier.isSubscription()) {
      validateSubscription();
    } else if (email != null) {
      refreshCustomerName();
    }
    return status;
  }

  @Override
  public void autoProvisionIfConfigured() {
    String email = trimToNull(properties.getAutoProvisionEmail());
    String passcode = trimToNull(properties.getAutoProvisionPasscode());
    if (email == null || passcode == null || !status.isTrial()) {
      return;
    }
    try {
      provision(email, passcode, properties.getLocationName());
    } catch (LicenseException e) {
      log.warn("Automatic license provisioning failed: {}", e.getMessage());
      synchronized (this) {
        setMessage("004", "Automatic license provisioning failed: " + e.getMessage(), false);
        publish();
      }
    }
  }

  // ================================================================ server housekeeping

  @Override
  public void sendHeartbeat() {
    sendHeartbeatQuietly();
  }

  private void sendHeartbeatQuietly() {
    try {
      client.heartbeat(clientIdentity());
    } catch (RuntimeException e) {
      log.debug("Heartbeat failed: {}", e.getMessage());
    }
  }

  @Override
  public void checkEngineVersion() {
    try {
      String reply = client.getEngineVersion();
      Matcher m = reply == null ? null : ENGINE_VERSION_REPLY.matcher(reply.trim());
      if (m == null || !m.matches()) {
        // Empty (not configured) or not this protocol at all, e.g. the legacy jukeanator.com
        // site, which answers unknown commands with its HTML home page.
        log.debug("No engine version available from the license server");
        return;
      }
      synchronized (this) {
        latestEngineVersion = m.group(1);
        engineDownloadUrl = trimToNull(m.group(2));
        publish();
      }
    } catch (RuntimeException e) {
      log.debug("Engine version check failed: {}", e.getMessage());
    }
  }

  /**
   * The server's customer name wins over the configured one: it is what the customer registered
   * with, and the operator prompt only asks for a name when the server has none.
   */
  @Override
  public synchronized String refreshCustomerName() {
    String current = trimToNull(properties.getCustomerName());
    if (trimToNull(properties.getCustomerEmail()) == null) {
      return current;
    }
    try {
      String name = trimToNull(client.getCustomerName(clientIdentity()));
      // Ignore the legacy "invalid" replies and anything that is not a plain name (HTML etc.).
      if (name != null && !name.startsWith(LegacyResponses.INVALID_CUSTOMER_PREFIX)
          && name.length() <= 256 && name.indexOf('<') < 0 && !name.equals(current)) {
        log.info("Customer name from the license server: {}", name);
        persist(Map.of("customer-name", name), p -> p.setCustomerName(name));
        return name;
      }
    } catch (RuntimeException e) {
      log.debug("Customer name lookup failed: {}", e.getMessage());
    }
    return current;
  }

  private ClientIdentity clientIdentity() {
    return new ClientIdentity(licensedTier.getWireCode(), machineId,
        properties.getCustomerEmail(), properties.getCustomerName(),
        properties.getLocationName());
  }

  // ================================================================ status

  private LicenseStatus publish() {
    LicenseTier effective = licensedTier;
    if (revoked) {
      effective = LicenseTier.D;
      if (message == null) {
        setMessage("010", "This license has been revoked. " + SUPPORT, false);
      }
    }
    if (licensedTier.isSubscription() && subscriptionExpired) {
      effective = LicenseTier.D;
    }
    if (properties.isDemoteToNonCommercial() && effective.isCommercial()) {
      effective = LicenseTier.NC;
    }
    boolean registrationNeeded = licensedTier == LicenseTier.D
        || (licensedTier.isSubscription() && trimToNull(properties.getCustomerEmail()) == null);

    Instant expiresAt = null;
    if (extendCode != null && SubscriptionQuarter.isValid(extendCode.expirationQuarter())) {
      expiresAt = SubscriptionQuarter.parse(extendCode.expirationQuarter()).expiry(zone())
          .toInstant();
    }
    LicenseStatus next = new LicenseStatus(licensedTier, effective, machineId,
        licenseDir == null ? null : licenseDir.toString(), effective.isCommercial(),
        extendCode == null ? null : extendCode.expirationQuarter(),
        extendCode == null ? null : extendCode.hoursRemaining(), expiresAt, messageCode, message,
        warning, registrationNeeded, lastServerCheckAt, latestEngineVersion, engineDownloadUrl);

    LicenseStatus previous = status;
    status = next;
    if (!next.equals(previous)) {
      if (previous.commercialEnabled() != next.commercialEnabled()) {
        log.info("License: {} -> commercial operation {}", next.describe(),
            next.commercialEnabled() ? "ENABLED" : "DISABLED");
      }
      events.publishEvent(new LicenseStatusChangedEvent(previous, next));
    }
    return next;
  }

  private void clearSubscriptionState() {
    extendCode = null;
    subscriptionExpired = false;
    messageCode = null;
    message = null;
    warning = false;
  }

  private void setMessage(String code, String text, boolean isWarning) {
    messageCode = code;
    message = text;
    warning = isWarning;
  }

  // ================================================================ files and config

  /**
   * The configured folder, else the first legacy location holding a license key, else the first
   * that exists, else {@code app.data-dir}.
   */
  private Path resolveLicenseDir() {
    String configured = trimToNull(properties.getDirectory());
    if (configured != null) {
      return Path.of(configured);
    }
    List<Path> search = searchDirs();
    for (Path dir : search) {
      if (Files.isRegularFile(dir.resolve(LegacyCtlFiles.LICENSE_KEY_FILENAME))) {
        return dir;
      }
    }
    return search.stream().filter(Files::isDirectory).findFirst().orElse(dataDir);
  }

  /** Legacy search order: data directory, working directory, {@code C:\kiosk}. */
  List<Path> searchDirs() {
    List<Path> dirs = new ArrayList<>();
    dirs.add(dataDir);
    dirs.add(Path.of("").toAbsolutePath());
    if (System.getProperty("os.name", "").startsWith("Windows")) {
      dirs.add(Path.of("C:\\kiosk"));
    }
    return dirs;
  }

  private ExtendCode readExtendCodeQuietly() {
    List<Path> dirs = new ArrayList<>();
    if (licenseDir != null) {
      dirs.add(licenseDir);
    }
    if (trimToNull(properties.getDirectory()) == null) {
      dirs.addAll(searchDirs());
    }
    for (Path dir : dirs.stream().distinct().toList()) {
      Path file = dir.resolve(LegacyCtlFiles.EXTEND_CODE_FILENAME);
      if (Files.isRegularFile(file)) {
        try {
          return LegacyCtlFiles.readExtendCode(file);
        } catch (IOException e) {
          log.warn("Could not read {}: {}", file, e.getMessage());
          return null;
        }
      }
    }
    return null;
  }

  private void writeExtendCode(ExtendCode code) {
    Path file = (licenseDir != null ? licenseDir : resolveLicenseDir())
        .resolve(LegacyCtlFiles.EXTEND_CODE_FILENAME);
    try {
      LegacyCtlFiles.writeExtendCode(file, code);
    } catch (IOException e) {
      log.error("Could not write {}: {}", file, e.getMessage());
      setMessage("001", "The subscription file could not be written to " + file
          + ". Please check the folder permissions. " + SUPPORT, false);
    }
  }

  private static void touch(Path file) throws IOException {
    if (Files.exists(file)) {
      Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
    } else {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Files.createFile(file);
    }
  }

  private void persist(Map<String, String> values, Consumer<LicenseProperties> inMemory) {
    inMemory.accept(properties);
    configPersister.persist(configFile, values);
  }

  private ZoneId zone() {
    String z = trimToNull(properties.getTimeZone());
    return z == null ? ZoneId.systemDefault() : ZoneId.of(z);
  }

  private ZonedDateTime now() {
    return ZonedDateTime.now(clock.withZone(zone()));
  }

  private static String firstNonBlank(String a, String b) {
    String t = trimToNull(a);
    return t != null ? t : trimToNull(b);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}
