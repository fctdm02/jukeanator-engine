package com.djt.jukeanator_engine.domain.licenseserver.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.djt.jukeanator_engine.domain.email.service.EmailService;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyResponses;
import com.djt.jukeanator_engine.domain.licenseserver.config.LicenseServerProperties;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LegacyClientRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos;
import com.djt.jukeanator_engine.domain.licenseserver.dto.ProvisionOutcome;
import com.djt.jukeanator_engine.domain.licenseserver.service.LicenseServerService;

/** Byte-level checks of the legacy wire format legacy MP3Jukeboxx clients compare against. */
class LegacyFrontControllerTest {

  private RecordingService service;
  private LegacyFrontController controller;

  @BeforeEach
  void setUp() {
    service = new RecordingService();
    LicenseServerProperties properties = new LicenseServerProperties();
    properties.setLatestEngineVersion("1.2.0");
    properties.setEngineDownloadUrl("https://www.jukeanator.com/temp/engine.zip");
    controller = new LegacyFrontController(service, properties, new NoEmail());
  }

  @Test
  void heartbeat_textReply_isUrlEncodedWithNewlineAndLegacyContentType() throws Exception {
    MockHttpServletResponse response = call("heartbeat", "serialNumber", "%7BABC%7D",
        "licenseType", "1", "customerEmail", "a%40b.com");

    assertEquals("text/html", response.getContentType().split(";")[0]);
    assertEquals("ISO-8859-1", response.getCharacterEncoding());
    assertEquals(LegacyResponses.OK + "\n",
        new String(response.getContentAsByteArray(), StandardCharsets.ISO_8859_1));
    // Container-decoded "%7BABC%7D" is decoded once more, like the legacy server did.
    assertEquals("{ABC}", service.last.serialNumber());
    assertEquals("C", service.last.licenseType());
    assertEquals("a@b.com", service.last.customerEmail());
  }

  @Test
  void provision_success_isRawSixteenBytes() throws Exception {
    MockHttpServletResponse response = call("provision", "serialNumber", "S1", "customerEmail",
        "a@b.com", "passcode", "abc");

    assertEquals("application/octet-stream", response.getContentType());
    assertEquals(16, response.getContentLength());
    assertArrayEquals(RecordingService.KEY, response.getContentAsByteArray());
    assertEquals("abc", service.lastPasscode);
  }

  @Test
  void provision_refusal_isEncodedText() throws Exception {
    service.refuse = true;
    MockHttpServletResponse response = call("provision", "serialNumber", "S1");
    assertEquals("No+more+available+licenses+for+given+customer+email%3A+a%40b.com\n",
        response.getContentAsString(StandardCharsets.ISO_8859_1));
  }

  @Test
  void commandIsCaseInsensitive_andPageCommandsRedirect() throws Exception {
    assertEquals(LegacyResponses.OK + "\n",
        call("HeartBeat", "customerEmail", "a@b.com").getContentAsString());
    assertEquals("/order.html", call("order").getRedirectedUrl());
    assertEquals("/", call("songusagereport").getRedirectedUrl());
    assertEquals("/", call(null).getRedirectedUrl());
  }

  @Test
  void engineVersion() throws Exception {
    assertEquals(LegacyResponses.encodeText("1.2.0|https://www.jukeanator.com/temp/engine.zip"),
        call("getengineversion").getContentAsString());
  }

  @Test
  void legacyJarMissing_reportsEpochDateAndZeroSize() {
    assertEquals("12-31-1969", controller.legacyJarBuildDate());
    assertEquals("0", controller.legacyJarBuildSize());
  }

  private MockHttpServletResponse call(String command, String... params) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/FrontController");
    if (command != null) {
      request.addParameter("command", command);
    }
    for (int i = 0; i < params.length; i += 2) {
      request.addParameter(params[i], params[i + 1]);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    controller.handle(request, response);
    return response;
  }

  private static final class RecordingService implements LicenseServerService {
    static final byte[] KEY = new byte[16];
    LegacyClientRequest last;
    String lastPasscode;
    boolean refuse;

    static {
      for (int i = 0; i < 16; i++) {
        KEY[i] = (byte) (i * 17);
      }
    }

    @Override
    public ProvisionOutcome provision(LegacyClientRequest request, String passcodeSha1) {
      last = request;
      lastPasscode = passcodeSha1;
      return refuse
          ? ProvisionOutcome.refused("No more available licenses for given customer email: "
              + "a@b.com")
          : ProvisionOutcome.key(KEY);
    }

    @Override
    public String checkSubscription(LegacyClientRequest request) {
      last = request;
      return "cafe";
    }

    @Override
    public String heartbeat(LegacyClientRequest request) {
      last = request;
      return LegacyResponses.OK;
    }

    @Override
    public String getCustomerName(LegacyClientRequest request) {
      return "Owner";
    }

    @Override
    public List<LicenseAdminDtos.CustomerDto> searchCustomers(String q) {
      return List.of();
    }

    @Override
    public LicenseAdminDtos.CustomerDto getCustomer(int customerId) {
      return null;
    }

    @Override
    public LicenseAdminDtos.CustomerDto createCustomer(LicenseAdminDtos.CustomerRequest r) {
      return null;
    }

    @Override
    public LicenseAdminDtos.CustomerDto updateCustomer(int id, LicenseAdminDtos.CustomerRequest r) {
      return null;
    }

    @Override
    public LicenseAdminDtos.CustomerDto addLicenses(int id, LicenseAdminDtos.AddLicensesRequest r) {
      return null;
    }

    @Override
    public LicenseAdminDtos.CustomerDto setCustomerRevoked(int id, boolean revoked) {
      return null;
    }

    @Override
    public LicenseAdminDtos.MachineDto updateMachine(int id, LicenseAdminDtos.MachineRequest r) {
      return null;
    }

    @Override
    public LicenseAdminDtos.MachineDto extendMachine(int id, LicenseAdminDtos.ExtendRequest r) {
      return null;
    }
  }

  private static final class NoEmail implements EmailService {
    @Override
    public void sendPurchaseReceiptEmail(String toAddress, String firstName, int credits,
        int bonusCredits, java.math.BigDecimal amountUsd, String paymentSource,
        String transactionId, java.time.Instant timestamp, int newBalance) {}

    @Override
    public void sendAccountVerificationEmail(String toAddress, String firstName,
        String verificationUrl, java.time.Instant expiresAt) {}

    @Override
    public void sendFinancialLedgerSplitSummaryEmail(
        com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto period) {}

    @Override
    public void sendContactFeedbackEmail(List<String> toAddresses, String visitorEmail,
        String comments) {}
  }
}
