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
import java.util.Map;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.location.controller.LocationController;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsResponseDto;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import com.djt.jukeanator_engine.domain.user.service.PaymentChargeResult;
import com.djt.jukeanator_engine.domain.user.service.PaymentGateway;
import com.djt.jukeanator_engine.domain.user.service.UserService;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;

/**
 * End-to-end coverage of the financial ledger across a master/slave pair, run against a live local
 * MySQL instance -- the same {@code app.mode=master}/{@code RANDOM_PORT}+{@code TestRestTemplate}
 * shape as {@code MasterSlaveLibrarySyncIntegrationTest}, extended to the financial side documented
 * in {@code docs/financial-ledger-refactor-and-sync.md}.
 *
 * <p>Covers what "rock solid" actually requires:
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
 * <li>Finalized jukebox-split periods mirror from a slave into {@code location_jukebox_split},
 * tagged by {@code parent_location_id} and idempotent on {@code source_period_id}.
 * <li>A slave's catch-up pull of mobile/web spends sees only its own location's, each carrying the
 * {@code sync_id} its slave-side copy is keyed on.
 * <li>A slave's user-activity batch lands in {@code user_activity} under the authenticated path's
 * location, idempotent on {@code activity_id}.
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

  @Test
  void finalizedSplitPeriods_mirroredOverHttp_landInLocationJukeboxSplitTaggedByLocation_andAreIdempotent()
      throws Exception {

    ProvisionedLocationDto locationA = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Split Tavern"), 42.33, -83.04));
    ProvisionedLocationDto locationB = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Split Lounge"), 40.0, -105.0));

    postSplitPeriod(locationA, 501, new BigDecimal("20.00"));
    // Same sourcePeriodId at a genuinely different location -- uniqueness is per-location.
    postSplitPeriod(locationB, 501, new BigDecimal("7.00"));
    // A retried push must be a safe no-op, not a duplicate row.
    postSplitPeriod(locationA, 501, new BigDecimal("20.00"));

    assertSplitPeriodRow(locationA.locationId(), 501, new BigDecimal("20.00"));
    assertSplitPeriodRow(locationB.locationId(), 501, new BigDecimal("7.00"));
  }

  @Test
  void mobileCreditUsagePull_overHttp_returnsOnlyThatLocationsSpends_eachWithASyncId()
      throws Exception {

    ProvisionedLocationDto locationA = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Pull Bar"), 41.0, -87.0));
    ProvisionedLocationDto locationB = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Other Bar"), 41.9, -87.6));

    String email = uniqueName("puller") + "@example.com";
    userService.register(new RegisterRequest("Pul", "Ler", email, "password123"));

    Instant since = Instant.now().minusSeconds(60);
    userService.chargeCreditsForQueueAction(email, Integer.valueOf(1), locationA.locationId());
    userService.chargeCreditsForQueueAction(email, Integer.valueOf(1), locationB.locationId());

    List<UserSongCreditUsageDto> pulled = getMobileCreditUsage(locationA, locationA, since);

    List<UserSongCreditUsageDto> mine =
        pulled.stream().filter(u -> email.equals(u.userEmail())).toList();
    assertEquals(1, mine.size(), "Location A's pull must never see location B's spend");
    assertEquals(locationA.locationId(), mine.get(0).locationId());
    assertNotNull(mine.get(0).syncId(), "Every new spend needs a cross-instance sync id");
    assertSyncIdPersisted(mine.get(0).syncId(), locationA.locationId());

    // Location B's credentials must never read location A's spends.
    HttpHeaders headers = locationHeaders(locationB);
    ResponseEntity<String> response = restTemplate.exchange(
        "/api/locations/{locationId}/financial-ledger/mobile-credit-usage?since={since}",
        HttpMethod.GET, new HttpEntity<>(headers), String.class, locationA.locationId(),
        since.toString());
    assertTrue(response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError(),
        "A pull for another location's id must be rejected -- got " + response.getStatusCode());
  }

  // Lives here rather than in its own class so it shares this class's master-mode Spring context
  // (and its Braintree override) instead of starting another one.
  @Test
  void userActivity_mirroredOverHttp_landsInUserActivityUnderThePathsLocation_andIsIdempotent()
      throws Exception {

    ProvisionedLocationDto location = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Activity Bar"), 41.0, -87.0));
    ProvisionedLocationDto other = locationService
        .registerLocation(new RegisterLocationRequest(uniqueName("Other Pub"), 41.5, -87.5));

    String firstId = UUID.randomUUID().toString();
    String secondId = UUID.randomUUID().toString();
    List<UserActivityRecord> batch = List.of(
        new UserActivityRecord(location.locationId(), UserActivitySource.SWING_UI, "LOCAL",
            UserActivityType.TAB_NAVIGATION, Instant.now(), Map.of("tabName", "GENRES"), firstId),
        // Claims another location -- the authenticated path's location must win.
        new UserActivityRecord(Integer.valueOf(999_999), UserActivitySource.SWING_UI, "LOCAL",
            UserActivityType.ARTIST_VIEWED, Instant.now(), Map.of(), secondId));

    assertEquals(HttpStatus.NO_CONTENT, postUserActivity(location, location, batch));
    // A retried batch (e.g. the acknowledgement was lost) must never duplicate a row.
    assertEquals(HttpStatus.NO_CONTENT, postUserActivity(location, location, batch));

    assertUserActivityRowCount(location.locationId(), firstId, 1);
    assertUserActivityRowCount(location.locationId(), secondId, 1);

    // Another location's credentials must never write into this location's activity.
    String thirdId = UUID.randomUUID().toString();
    HttpStatusCode rejected = postUserActivity(other, location, List.of(new UserActivityRecord(
        location.locationId(), UserActivitySource.SWING_UI, "LOCAL",
        UserActivityType.TAB_NAVIGATION, Instant.now(), Map.of(), thirdId)));
    assertTrue(rejected.is4xxClientError() || rejected.is5xxServerError(),
        "A push for another location's id must be rejected -- got " + rejected);
    assertUserActivityRowCount(location.locationId(), thirdId, 0);
  }

  // ── HTTP calls, exactly as FinancialLedgerSyncService (the slave) makes them ───────────

  private HttpStatusCode postUserActivity(ProvisionedLocationDto credentials,
      ProvisionedLocationDto location, List<UserActivityRecord> records) {

    HttpHeaders headers = locationHeaders(credentials);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return restTemplate.postForEntity("/api/locations/{locationId}/user-activity/sync",
        new HttpEntity<>(records, headers), String.class, location.locationId()).getStatusCode();
  }

  private static HttpHeaders locationHeaders(ProvisionedLocationDto location) {

    HttpHeaders headers = new HttpHeaders();
    headers.set(LocationController.LOCATION_ID_HEADER, String.valueOf(location.locationId()));
    headers.set(LocationController.LOCATION_API_KEY_HEADER, location.apiKey());
    return headers;
  }

  private void postSplitPeriod(ProvisionedLocationDto location, int sourcePeriodId,
      BigDecimal totalEarned) {

    HttpHeaders headers = locationHeaders(location);
    headers.setContentType(MediaType.APPLICATION_JSON);
    BigDecimal half = totalEarned.divide(BigDecimal.valueOf(2));
    HttpEntity<JukeboxSplitPeriodSyncDto> request = new HttpEntity<>(new JukeboxSplitPeriodSyncDto(
        sourcePeriodId, Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-02-01T00:00:00Z"), 50, totalEarned, BigDecimal.ZERO, BigDecimal.ZERO,
        totalEarned, half, totalEarned.subtract(half)), headers);

    ResponseEntity<Void> response = restTemplate.postForEntity(
        "/api/locations/{locationId}/financial-ledger/split-period", request, Void.class,
        location.locationId());

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
  }

  private List<UserSongCreditUsageDto> getMobileCreditUsage(ProvisionedLocationDto credentials,
      ProvisionedLocationDto location, Instant since) {

    ResponseEntity<List<UserSongCreditUsageDto>> response = restTemplate.exchange(
        "/api/locations/{locationId}/financial-ledger/mobile-credit-usage?since={since}",
        HttpMethod.GET, new HttpEntity<>(locationHeaders(credentials)),
        new ParameterizedTypeReference<List<UserSongCreditUsageDto>>() {}, location.locationId(),
        since.toString());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    return response.getBody();
  }

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

  private void assertSplitPeriodRow(Integer locationId, int sourcePeriodId,
      BigDecimal expectedTotalEarned) throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select total_earned, end_date from location_jukebox_split "
                + "where parent_location_id = ? and source_period_id = ?")) {

      statement.setInt(1, locationId);
      statement.setInt(2, sourcePeriodId);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "Expected a mirrored split period for locationId " + locationId);
        assertEquals(0, expectedTotalEarned.compareTo(rs.getBigDecimal("total_earned")));
        assertNotNull(rs.getTimestamp("end_date"));
        assertTrue(!rs.next(), "Expected exactly one mirrored split period per source id");
      }
    }
  }

  private void assertUserActivityRowCount(Integer locationId, String activityId,
      int expectedCount) throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select count(*) from user_activity where location_id = ? and activity_id = ?")) {

      statement.setInt(1, locationId);
      statement.setString(2, activityId);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next());
        assertEquals(expectedCount, rs.getInt(1));
      }
    }
  }

  private void assertSyncIdPersisted(String syncId, Integer expectedLocationId)
      throws SQLException {

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select location_id from user_song_credit_usage where sync_id = ?")) {

      statement.setString(1, syncId);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "Expected user_song_credit_usage to persist sync_id " + syncId);
        assertEquals(expectedLocationId.intValue(), rs.getInt("location_id"));
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
