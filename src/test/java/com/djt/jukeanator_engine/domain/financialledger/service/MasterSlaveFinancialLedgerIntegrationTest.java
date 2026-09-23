package com.djt.jukeanator_engine.domain.financialledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.location.controller.LocationController;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsResponseDto;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import com.djt.jukeanator_engine.domain.user.service.PaymentChargeResult;
import com.djt.jukeanator_engine.domain.user.service.PaymentGateway;
import com.djt.jukeanator_engine.domain.user.service.UserService;

/**
 * End-to-end coverage of the financial ledger across a master/slave pair, run against a live local
 * MySQL instance -- the same {@code app.mode=master}/{@code RANDOM_PORT}+{@code TestRestTemplate}
 * shape as {@code MasterSlaveLibrarySyncIntegrationTest}, extended to the financial side documented
 * in {@code docs/financial-ledger-refactor-and-sync.md}.
 *
 * <p>Covers three things "rock solid" actually requires:
 * <ol>
 * <li>Local cash/credit-card transactions genuinely mirror from a slave to master over real HTTP
 * (the {@code location-id}/{@code location-api-key}-authenticated {@code
 * FinancialLedgerSyncController} endpoints), landing in {@code location_transaction} tagged by
 * {@code location_id} and isolated per location, and the mirror is idempotent -- a retried push
 * (same {@code sourceTransactionId}) never duplicates a row.
 * <li>A wrong API key never reaches {@link FinancialLedgerServiceImpl} or persists anything.
 * <li>Add-Funds transactions (real money -> song credits) and song-credit-usage transactions
 * (song credits -> a queued song at a location) are genuinely separate ledgers now: the former has
 * no {@code location_id} at all (see {@code UserAddFundsTransactionEntity}'s javadoc), the latter
 * is correctly isolated per location via {@code UserService.getCreditLedgerForLocation} -- the
 * reconciliation the user asked for.
 * </ol>
 *
 * <p>{@code app.mode=master} wires the real {@code BraintreePaymentGateway} (see {@code
 * AppConfig}), which would otherwise make this test perform a real network call against Braintree
 * with fake sandbox credentials -- {@link FakePaymentGatewayConfig} overrides it with a
 * same-process fake that always succeeds, exactly the boundary {@code UserServiceTest}'s own mocked
 * {@code PaymentGateway} already stands in for, just wired through Spring instead of Mockito here.
 *
 * @author tmyers
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.mode=master", "app.repository-type=jpa" })
@Import(MasterSlaveFinancialLedgerIntegrationTest.FakePaymentGatewayConfig.class)
class MasterSlaveFinancialLedgerIntegrationTest extends AbstractServiceIntegrationTest {

  @Autowired
  private LocationService locationService;

  @Autowired
  private UserService userService;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private DataSource dataSource;

  @Test
  void localCashAndCreditCardTransactions_mirroredOverHttp_landInLocationTransactionTaggedByLocation_andAreIdempotent()
      throws Exception {

    ProvisionedLocationDto locationA = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Corner Tavern"), 42.33, -83.04));
    ProvisionedLocationDto locationB = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Rock On Third"), 40.0, -105.0));

    postLocalTransaction(locationA, "local-cash", 1001, 1, Instant.now());
    postLocalTransaction(locationA, "local-credit-card", 2001, 1, Instant.now());
    // Same sourceTransactionId as locationA's cash push, but a genuinely different location --
    // the (locationId, sourceTransactionId) uniqueness must be scoped per-location, not global.
    postLocalTransaction(locationB, "local-cash", 1001, 1, Instant.now());

    assertLocationTransactionCount(locationA.locationId(), "CASH", 1);
    assertLocationTransactionCount(locationA.locationId(), "CREDIT_CARD", 1);
    assertLocationTransactionCount(locationB.locationId(), "CASH", 1);

    // A retried push (e.g. after a network blip) with the exact same sourceTransactionId must be
    // a safe no-op, not a duplicate row -- the whole point of carrying it.
    postLocalTransaction(locationA, "local-cash", 1001, 1, Instant.now());
    assertLocationTransactionCount(locationA.locationId(), "CASH", 1);
  }

  @Test
  void receiveLocalCashSync_overHttp_rejectsWrongApiKey_andNeverPersists() throws Exception {

    ProvisionedLocationDto location = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Locked Bar"), 1.0, 2.0));

    HttpHeaders headers = new HttpHeaders();
    headers.set(LocationController.LOCATION_API_KEY_HEADER, "not-the-real-key");
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<LocalTransactionSyncDto> request = new HttpEntity<>(
        new LocalTransactionSyncDto(1, 1, Instant.now()), headers);

    ResponseEntity<String> response = restTemplate.postForEntity(
        "/api/locations/{locationId}/financial-ledger/local-cash", request, String.class,
        location.locationId());

    assertTrue(response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError(),
        "A sync request with the wrong api key must never reach FinancialLedgerServiceImpl -- got "
            + response.getStatusCode());
    assertLocationTransactionRowCount(location.locationId(), 0);
  }

  @Test
  void addFundsAndSongCreditUsage_areGenuinelySeparateLedgers_oneNeverLocationAttributedTheOtherAlwaysIs()
      throws Exception {

    ProvisionedLocationDto locationA = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Downtown Bar"), 41.0, -87.0));
    ProvisionedLocationDto locationB = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Uptown Bar"), 41.9, -87.6));

    String email = uniqueName("patron") + "@example.com";
    userService.register(new RegisterRequest("Pat", "Ron", email, "password123"));

    // ── real money -> song credits, never location-attributed ──────────────
    AddFundsResponseDto addFundsResult =
        userService.addFunds(email, new AddFundsRequest("pkg-7", "fake-nonce"));
    assertNotNull(addFundsResult.transactionId());

    assertAddFundsTransactionRow(email, "pkg-7", 12, 1);

    // ── song credits -> queueing a song at a specific location ─────────────
    userService.chargeCreditsForQueueAction(email, Integer.valueOf(1), locationA.locationId());
    userService.chargeCreditsForQueueAction(email, Integer.valueOf(1), locationB.locationId());

    Instant from = Instant.now().minusSeconds(60);
    Instant to = Instant.now().plusSeconds(60);
    List<UserSongCreditUsageDto> ledgerA =
        userService.getCreditLedgerForLocation(locationA.locationId(), from, to);
    List<UserSongCreditUsageDto> ledgerB =
        userService.getCreditLedgerForLocation(locationB.locationId(), from, to);

    assertEquals(1, ledgerA.size(), "Location A's ledger must not see location B's spend");
    assertEquals(1, ledgerB.size(), "Location B's ledger must not see location A's spend");
    assertEquals(email, ledgerA.get(0).userEmail());

    // The two spends never touched the Add-Funds ledger -- it still has exactly the one entry
    // from earlier, proving the split is real, not just a filter on one shared table.
    assertAddFundsTransactionRow(email, "pkg-7", 12, 1);
  }

  // ── HTTP calls, exactly as FinancialLedgerSyncService (the slave) makes them ───────────

  private void postLocalTransaction(ProvisionedLocationDto location, String pathSegment,
      int sourceTransactionId, int amountDollars, Instant timestamp) {

    HttpHeaders headers = new HttpHeaders();
    headers.set(LocationController.LOCATION_ID_HEADER, String.valueOf(location.locationId()));
    headers.set(LocationController.LOCATION_API_KEY_HEADER, location.apiKey());
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<LocalTransactionSyncDto> request = new HttpEntity<>(
        new LocalTransactionSyncDto(sourceTransactionId, amountDollars, timestamp), headers);

    ResponseEntity<Void> response = restTemplate.postForEntity(
        "/api/locations/{locationId}/financial-ledger/{pathSegment}", request, Void.class,
        location.locationId(), pathSegment);

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
  }

  // ── fixtures ────────────────────────────────────────────────────────────

  private static String uniqueName(String baseName) {
    return baseName + " " + System.nanoTime();
  }

  // ── raw-JDBC assertions against the live MySQL schema ──────────────────

  private void assertLocationTransactionCount(Integer locationId, String transactionType,
      int expectedCount) throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select count(*) from location_transaction where location_id = ? "
                + "and transaction_type = ?")) {

      statement.setInt(1, locationId);
      statement.setString(2, transactionType);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next());
        assertEquals(expectedCount, rs.getInt(1));
      }
    }
  }

  private void assertLocationTransactionRowCount(Integer locationId, int expectedCount)
      throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select count(*) from location_transaction where location_id = ?")) {

      statement.setInt(1, locationId);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next());
        assertEquals(expectedCount, rs.getInt(1));
      }
    }
  }

  private void assertAddFundsTransactionRow(String email, String expectedPackageId,
      int expectedCreditsAwarded, int expectedBonusCredits) throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select f.package_id, f.credits_awarded, f.bonus_credits "
                + "from user_add_funds_transaction f join user_account u on u.persistent_identity = f.user_id "
                + "where u.email_address = ?")) {

      statement.setString(1, email);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "Expected exactly one Add-Funds row for " + email);
        assertEquals(expectedPackageId, rs.getString("package_id"));
        assertEquals(expectedCreditsAwarded, rs.getInt("credits_awarded"));
        assertEquals(expectedBonusCredits, rs.getInt("bonus_credits"));
        assertTrue(!rs.next(), "Expected only one Add-Funds row for " + email);
      }
    }
  }

  /**
   * Overrides {@code AppConfig}'s real {@code BraintreePaymentGateway} (wired unconditionally
   * whenever {@code app.mode=master}) with a same-process fake that always succeeds -- this test
   * must never make a real network call to Braintree, and the fake sandbox credentials in {@code
   * application-test.yml} wouldn't authenticate against it anyway.
   */
  @TestConfiguration
  static class FakePaymentGatewayConfig {

    @Bean
    @Primary
    PaymentGateway fakePaymentGateway() {
      return new PaymentGateway() {

        @Override
        public String generateClientToken() {
          return "fake-client-token";
        }

        @Override
        public PaymentChargeResult charge(BigDecimal amount, String paymentMethodNonce) {
          return PaymentChargeResult.success("fake-txn-" + UUID.randomUUID(), "TestGateway");
        }
      };
    }
  }
}
