package com.djt.jukeanator_engine.domain.license.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.ResourceAccessException;

import com.djt.jukeanator_engine.domain.license.client.LicenseServerClient;
import com.djt.jukeanator_engine.domain.license.config.LicenseProperties;
import com.djt.jukeanator_engine.domain.license.event.LicenseStatusChangedEvent;
import com.djt.jukeanator_engine.domain.license.legacy.ExtendCode;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyCrypto;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyCtlFiles;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyResponses;
import com.djt.jukeanator_engine.domain.license.legacy.LicenseTier;
import com.djt.jukeanator_engine.domain.license.legacy.SubscriptionQuarter;
import com.djt.jukeanator_engine.domain.license.model.LicenseStatus;

class LicenseServiceImplTest {

  private static final String SID = "661793536";
  private static final String EMAIL = "owner@example.com";
  private static final ZoneId ZONE = ZoneId.of("America/Detroit");
  private static final Clock CLOCK =
      Clock.fixed(ZonedDateTime.of(2026, 11, 8, 12, 0, 0, 0, ZONE).toInstant(), ZONE);
  private static final SubscriptionQuarter CURRENT =
      SubscriptionQuarter.of(LocalDate.of(2026, 11, 8));

  @TempDir
  Path dir;

  private LicenseProperties properties;
  private FakeClient client;
  private List<LicenseStatusChangedEvent> events;
  private Map<String, String> persisted;
  private LicenseServiceImpl service;

  @BeforeEach
  void setUp() {
    properties = new LicenseProperties();
    properties.setDirectory(dir.toString());
    properties.setServerUrl("http://license.test");
    properties.setTimeZone(ZONE.getId());
    properties.setCustomerEmail(EMAIL);
    client = new FakeClient(properties);
    events = new ArrayList<>();
    persisted = new HashMap<>();
    MachineIdentityProvider identity = new MachineIdentityProvider() {
      @Override
      public List<String> candidateIds() {
        return List.of("{SOME-GUID}", SID);
      }

      @Override
      public String provisioningId() {
        return SID;
      }
    };
    service = new LicenseServiceImpl(properties, identity, client,
        e -> events.add((LicenseStatusChangedEvent) e), dir, null, CLOCK,
        (file, values) -> persisted.putAll(values));
  }

  @Test
  void noLicenseFile_isTrialAndNonCommercial() {
    LicenseStatus status = service.refreshLocal();
    assertEquals(LicenseTier.D, status.licensedTier());
    assertFalse(status.commercialEnabled());
    assertTrue(status.registrationNeeded());
    assertEquals(SID, status.machineId());
  }

  @Test
  void commercialKey_matchingSecondCandidate_enablesCommercial() throws IOException {
    writeKey(LicenseTier.C);
    LicenseStatus status = service.refreshLocal();
    assertEquals(LicenseTier.C, status.licensedTier());
    assertTrue(status.commercialEnabled());
    assertEquals(SID, status.machineId());
    assertTrue(events.get(events.size() - 1).current().commercialEnabled());
  }

  @Test
  void professionalKey_isNonCommercial() throws IOException {
    writeKey(LicenseTier.NC);
    assertFalse(service.refreshLocal().commercialEnabled());
  }

  @Test
  void demoteToNonCommercial_disablesCommercial() throws IOException {
    writeKey(LicenseTier.C);
    properties.setDemoteToNonCommercial(true);
    LicenseStatus status = service.refreshLocal();
    assertEquals(LicenseTier.NC, status.effectiveTier());
    assertFalse(status.commercialEnabled());
  }

  @Test
  void subscription_withValidExtendCode_isCommercial() throws IOException {
    writeKey(LicenseTier.CD);
    writeExtendCode(CURRENT.plus(2), 3000, SID, EMAIL);
    LicenseStatus status = service.refreshLocal();
    assertTrue(status.commercialEnabled());
    assertEquals(CURRENT.plus(2).toString(), status.subscriptionQuarter());
  }

  @Test
  void subscription_withoutExtendCode_isNotCommercial() throws IOException {
    writeKey(LicenseTier.CD);
    LicenseStatus status = service.refreshLocal();
    assertFalse(status.commercialEnabled());
    assertEquals("005", status.messageCode());
  }

  @Test
  void subscription_expiredQuarter_isNotCommercial() throws IOException {
    writeKey(LicenseTier.CR);
    writeExtendCode(CURRENT.plus(-1), 3000, SID, EMAIL);
    LicenseStatus status = service.refreshLocal();
    assertFalse(status.commercialEnabled());
    assertEquals("999", status.messageCode());
  }

  @Test
  void subscription_noHoursLeft_isNotCommercial() throws IOException {
    writeKey(LicenseTier.CD);
    writeExtendCode(CURRENT.plus(2), 0, SID, EMAIL);
    assertEquals("444", service.refreshLocal().messageCode());
  }

  @Test
  void subscription_otherMachine_isNotCommercial() throws IOException {
    writeKey(LicenseTier.CD);
    writeExtendCode(CURRENT.plus(2), 3000, "someone-else", EMAIL);
    LicenseStatus status = service.refreshLocal();
    assertFalse(status.commercialEnabled());
    assertEquals("002", status.messageCode());
  }

  @Test
  void serverPaidQuarter_rewritesExtendCode() throws IOException {
    writeKey(LicenseTier.CD);
    writeExtendCode(CURRENT, 10, SID, EMAIL);
    service.refreshLocal();
    client.subscriptionReply = LegacyCrypto.encryptHexStrict(
        LegacyResponses.urlEncode(SID + CURRENT.plus(4)));

    LicenseStatus status = service.validateSubscription();

    assertTrue(status.commercialEnabled());
    assertEquals(CURRENT.plus(4).toString(), status.subscriptionQuarter());
    ExtendCode written = LegacyCtlFiles.readExtendCode(dir.resolve("ExtendCode.CTL"));
    assertEquals(CURRENT.plus(4).toString(), written.expirationQuarter());
    assertTrue(written.hoursRemaining() > 3 * 2000);
  }

  @Test
  void serverNotPaid_winsOverValidLocalFile() throws IOException {
    writeKey(LicenseTier.CD);
    writeExtendCode(CURRENT.plus(3), 6000, SID, EMAIL);
    assertTrue(service.refreshLocal().commercialEnabled());
    client.subscriptionReply = LegacyCrypto.encryptHexStrict(
        LegacyResponses.urlEncode(SID + CURRENT.plus(-1)));

    LicenseStatus status = service.validateSubscription();

    assertFalse(status.commercialEnabled());
    assertEquals("003", status.messageCode());
    assertEquals(0, LegacyCtlFiles.readExtendCode(dir.resolve("ExtendCode.CTL"))
        .hoursRemaining());
  }

  @Test
  void serverUnreachable_fallsBackToLocalFile() throws IOException {
    writeKey(LicenseTier.CD);
    writeExtendCode(CURRENT.plus(2), 3000, SID, EMAIL);
    service.refreshLocal();
    client.failNetwork = true;
    assertTrue(service.validateSubscription().commercialEnabled());
  }

  @Test
  void provision_writesVerifiedKeyAndExtendCode() throws IOException {
    client.provisionReply = new LicenseServerClient.ProvisionReply(
        LegacyCrypto.licenseKeyBytes(LicenseTier.CD, SID), null);
    client.subscriptionReply = LegacyCrypto.encryptHexStrict(
        LegacyResponses.urlEncode(SID + CURRENT.plus(4)));

    LicenseStatus status = service.provision(EMAIL, "secret", "Joe's Bar");

    assertEquals(LicenseTier.CD, status.licensedTier());
    assertTrue(status.commercialEnabled());
    assertEquals(LegacyCrypto.sha1Hex("secret"), client.lastPasscode);
    assertArrayEquals(LegacyCrypto.licenseKeyBytes(LicenseTier.CD, SID),
        LegacyCtlFiles.readLicenseKey(dir.resolve("JukeANator.CTL")).orElseThrow());
    assertEquals("Joe's Bar", persisted.get("location-name"));
  }

  @Test
  void provision_refusedByServer_reportsServerMessage() {
    client.provisionReply = new LicenseServerClient.ProvisionReply(null,
        "No more available licenses for given customer email: " + EMAIL);
    LicenseException e = assertThrows(LicenseException.class,
        () -> service.provision(EMAIL, "secret", null));
    assertTrue(e.getMessage().startsWith("No more available licenses"));
  }

  @Test
  void provision_keyForAnotherMachine_isRejectedAndNotWritten() {
    client.provisionReply = new LicenseServerClient.ProvisionReply(
        LegacyCrypto.licenseKeyBytes(LicenseTier.C, "other"), null);
    assertThrows(LicenseException.class, () -> service.provision(EMAIL, "secret", null));
    assertTrue(LegacyCtlFiles.readLicenseKey(dir.resolve("JukeANator.CTL")).isEmpty());
  }

  @Test
  void hourlyTick_decrementsHours() throws IOException {
    writeKey(LicenseTier.CD);
    writeExtendCode(CURRENT.plus(2), 100, SID, EMAIL);
    service.refreshLocal();
    service.hourlyTick();
    assertEquals(99, LegacyCtlFiles.readExtendCode(dir.resolve("ExtendCode.CTL"))
        .hoursRemaining());
  }

  @Test
  void engineVersion_validReply_isRecorded() {
    client.engineVersionReply = "1.2.0|https://www.jukeanator.com/temp/engine.zip";
    service.checkEngineVersion();
    assertEquals("1.2.0", service.getStatus().latestEngineVersion());
    assertEquals("https://www.jukeanator.com/temp/engine.zip",
        service.getStatus().engineDownloadUrl());
  }

  @Test
  void engineVersion_legacySiteHtml_isIgnored() {
    // The legacy jukeanator.com site answers unknown commands with its HTML home page.
    client.engineVersionReply = "<html><script>if (a || b) {}</script>width: 100%;</html>";
    service.checkEngineVersion();
    assertEquals(null, service.getStatus().latestEngineVersion());
  }

  @Test
  void provision_nonSubscription_savesServerCustomerName() {
    client.provisionReply = new LicenseServerClient.ProvisionReply(
        LegacyCrypto.licenseKeyBytes(LicenseTier.C, SID), null);
    client.customerNameReply = "Joe Owner";

    service.provision(EMAIL, "secret", null);

    assertEquals("Joe Owner", persisted.get("customer-name"));
    assertEquals("Joe Owner", service.getCustomerName());
  }

  @Test
  void refreshCustomerName_serverNameReplacesConfiguredName() {
    properties.setCustomerName("Typed Name");
    client.customerNameReply = "Registered Name";
    assertEquals("Registered Name", service.refreshCustomerName());
    assertEquals("Registered Name", persisted.get("customer-name"));
  }

  @Test
  void refreshCustomerName_invalidOrUnreachable_keepsConfiguredName() {
    properties.setCustomerName("Typed Name");
    client.customerNameReply = LegacyResponses.INVALID_CUSTOMER_NAME;
    assertEquals("Typed Name", service.refreshCustomerName());
    client.customerNameReply = null;
    assertEquals("Typed Name", service.refreshCustomerName());
    assertFalse(persisted.containsKey("customer-name"));
  }

  @Test
  void refreshCustomerName_withoutEmail_doesNotAsk() {
    properties.setCustomerEmail(null);
    client.customerNameReply = "Should Not Be Used";
    assertEquals(null, service.refreshCustomerName());
    assertFalse(persisted.containsKey("customer-name"));
  }

  // ---------------------------------------------------------------- helpers

  private void writeKey(LicenseTier tier) throws IOException {
    LegacyCtlFiles.writeLicenseKey(dir.resolve("JukeANator.CTL"),
        LegacyCrypto.licenseKeyBytes(tier, SID));
  }

  private void writeExtendCode(SubscriptionQuarter q, int hours, String sid, String email)
      throws IOException {
    LegacyCtlFiles.writeExtendCode(dir.resolve("ExtendCode.CTL"),
        new ExtendCode(q.toString(), hours, sid, email));
  }

  private static final class FakeClient extends LicenseServerClient {
    String subscriptionReply = LegacyResponses.ERROR;
    /** null simulates an unreachable server. */
    String customerNameReply = "Owner";
    ProvisionReply provisionReply;
    String lastPasscode;
    boolean failNetwork;
    String engineVersionReply = "";

    FakeClient(LicenseProperties properties) {
      super(properties, "test", "standalone");
    }

    @Override
    public ProvisionReply provision(String customerEmail, String passcodeSha1,
        String serialNumber, String locationName) {
      lastPasscode = passcodeSha1;
      return provisionReply;
    }

    @Override
    public String checkSubscription(ClientIdentity identity) {
      if (failNetwork) {
        throw new ResourceAccessException("offline");
      }
      return subscriptionReply;
    }

    @Override
    public String heartbeat(ClientIdentity identity) {
      return LegacyResponses.OK;
    }

    @Override
    public String getCustomerName(ClientIdentity identity) {
      if (customerNameReply == null) {
        throw new ResourceAccessException("offline");
      }
      return customerNameReply;
    }

    @Override
    public String getEngineVersion() {
      return engineVersionReply;
    }
  }
}
