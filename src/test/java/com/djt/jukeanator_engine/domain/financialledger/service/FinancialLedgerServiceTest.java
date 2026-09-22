package com.djt.jukeanator_engine.domain.financialledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.financialledger.config.FinancialLedgerProperties;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.repository.FinancialLedgerRepository;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;
import com.djt.jukeanator_engine.domain.user.service.PricingConfig;
import com.djt.jukeanator_engine.domain.user.service.PricingService;
import com.djt.jukeanator_engine.domain.user.service.UserService;

/**
 * Covers {@link FinancialLedgerServiceImpl} against fully mocked dependencies (fast,
 * deterministic, no Spring context needed) -- same style as {@code LocationServiceTest}.
 *
 * @author tmyers
 */
class FinancialLedgerServiceTest {

  private static final Integer OWN_LOCATION_ID = Integer.valueOf(7);

  private FinancialLedgerRepository financialLedgerRepository;
  private FinancialLedgerProperties financialLedgerProperties;
  private UserService userService;
  private PricingService pricingService;
  private SongLibraryService songLibraryService;
  private ApplicationEventPublisher eventPublisher;
  private LocationService locationService;

  private final AtomicInteger nextId = new AtomicInteger(1);

  @BeforeEach
  void setUp() {

    financialLedgerRepository = mock(FinancialLedgerRepository.class);
    financialLedgerProperties = new FinancialLedgerProperties();
    userService = mock(UserService.class);
    pricingService = mock(PricingService.class);
    songLibraryService = mock(SongLibraryService.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    locationService = mock(LocationService.class);

    when(songLibraryService.getOwnLocationId()).thenReturn(OWN_LOCATION_ID);
    when(financialLedgerRepository.nextPersistentIdentity())
        .thenAnswer(invocation -> Integer.valueOf(nextId.getAndIncrement()));

    // Every getAllPeriods()/addSplit() call computes a live mobile sub-total, which needs a
    // creditsPerDollar rate even in tests that aren't specifically exercising mobile credits --
    // individual tests below override this with their own rate where the value matters.
    when(pricingService.resolvePricingConfig(OWN_LOCATION_ID))
        .thenReturn(new PricingConfig(2, 3, 3, 10, 2, false));
    when(userService.getCreditLedgerForLocation(org.mockito.ArgumentMatchers.eq(OWN_LOCATION_ID),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
  }

  private FinancialLedgerServiceImpl newService() {
    return new FinancialLedgerServiceImpl(financialLedgerRepository, financialLedgerProperties,
        userService, pricingService, songLibraryService, eventPublisher, locationService);
  }

  // ── bootstrap ────────────────────────────────────────────────────────────

  @Test
  void constructor_bootstrapsOneOpenPeriod_whenNoLedgerPersistedYet() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));

    newService();

    verify(financialLedgerRepository, times(1)).storeAggregateRoot(
        org.mockito.ArgumentMatchers.argThat(root -> root.getCurrentPeriod() != null
            && root.getSplitPeriods().size() == 1));
  }

  @Test
  void constructor_doesNotOpenAnotherPeriod_whenLoadedRootAlreadyHasAnOpenOne() throws Exception {

    FinancialLedgerRootEntity existingRoot = new FinancialLedgerRootEntity();
    existingRoot.addSplitPeriod(new JukeboxSplitPeriodEntity(Integer.valueOf(1), Instant.now()));
    when(financialLedgerRepository.loadAggregateRoot(anyString())).thenReturn(existingRoot);

    newService();

    verify(financialLedgerRepository, never()).storeAggregateRoot(org.mockito.ArgumentMatchers.any());
  }

  // ── recordLocalCashCredit / recordLocalCreditCardCredit ─────────────────

  @Test
  void recordLocalCashCredit_appendsTransactionTaggedWithOwnLocationAndPersists() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    FinancialLedgerServiceImpl service = newService();

    service.recordLocalCashCredit(1);

    List<JukeboxSplitPeriodDto> periods = service.getAllPeriods();
    assertEquals(1, periods.size());
    assertEquals(BigDecimal.valueOf(1).setScale(2), periods.get(0).cashTotal());
    assertEquals(BigDecimal.ZERO.setScale(2), periods.get(0).cardTotal());
    verify(financialLedgerRepository, times(2)).storeAggregateRoot(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void recordLocalCreditCardCredit_appendsTransactionTaggedWithOwnLocationAndPersists()
      throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    FinancialLedgerServiceImpl service = newService();

    service.recordLocalCreditCardCredit(1);

    List<JukeboxSplitPeriodDto> periods = service.getAllPeriods();
    assertEquals(BigDecimal.ZERO.setScale(2), periods.get(0).cashTotal());
    assertEquals(BigDecimal.valueOf(1).setScale(2), periods.get(0).cardTotal());
  }

  // ── getAllPeriods: live totals for the open period ──────────────────────

  @Test
  void getAllPeriods_computesLiveMobileTotal_fromNegativeAmountsOnlyAtCreditsPerDollarRate()
      throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));

    // Bootstrap the service (and its current period) first, so the fixture timestamps below can
    // be pinned safely after the period's own startDate -- a timestamp taken before the period
    // even started wouldn't reflect a real "spent during this period" scenario.
    FinancialLedgerServiceImpl service = newService();
    Instant afterPeriodStart = service.getAllPeriods().get(0).startDate().plusSeconds(1);

    // 30 credits spent (two QUEUE_ADD/QUEUE_ACTION-style negative entries) at 3 credits/dollar =
    // $10.00. Funds merely added (UserAddFundsTransactionEntity) never appear in this ledger at
    // all now -- getCreditLedgerForLocation only ever returns song-credit-usage entries, so there
    // is no longer a positive-amount row to filter out here. (creditsPerDollar=3 comes from
    // setUp()'s default PricingConfig stub.)
    when(userService.getCreditLedgerForLocation(org.mockito.ArgumentMatchers.eq(OWN_LOCATION_ID),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(
            new UserSongCreditUsageDto("alice@example.com", OWN_LOCATION_ID, -20,
                UserSongCreditUsageType.QUEUE_ADD, afterPeriodStart, 1, 2, 80),
            new UserSongCreditUsageDto("alice@example.com", OWN_LOCATION_ID, -10,
                UserSongCreditUsageType.QUEUE_ACTION, afterPeriodStart, null, null, 70)));

    JukeboxSplitPeriodDto current = service.getAllPeriods().get(0);
    assertEquals(new BigDecimal("10.00"), current.mobileTotal());
    assertEquals(new BigDecimal("10.00"), current.totalEarned());
    assertNull(current.amountDueOwner(), "Open period's owner/operator amounts stay unset until finalized");
  }

  @Test
  void getAllPeriods_mobileTotalIsZero_whenOwnLocationIdUnknown() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    when(songLibraryService.getOwnLocationId()).thenReturn(null);

    FinancialLedgerServiceImpl service = newService();

    JukeboxSplitPeriodDto current = service.getAllPeriods().get(0);
    assertEquals(BigDecimal.ZERO.setScale(2), current.mobileTotal());
  }

  @Test
  void getAllPeriods_includesAMobileSpendTiedExactlyAtThePeriodsOwnStartDate() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    when(pricingService.resolvePricingConfig(OWN_LOCATION_ID))
        .thenReturn(new PricingConfig(2, 3, 3, 10, 2, false));

    FinancialLedgerServiceImpl service = newService();
    Instant periodStart = service.getAllPeriods().get(0).startDate();

    // UserService.getCreditLedgerForLocation is inclusive at `from`, and nothing prevents a
    // legitimate spend from landing exactly on a period's own startDate (most obviously its very
    // first moment) -- addSplit() is what actually prevents double-counting across a split (by
    // guaranteeing the new period's startDate is always strictly after the old one's endDate), not
    // this query's own boundary semantics, so a tie here should simply be counted normally.
    when(userService.getCreditLedgerForLocation(org.mockito.ArgumentMatchers.eq(OWN_LOCATION_ID),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new UserSongCreditUsageDto("alice@example.com", OWN_LOCATION_ID, -9,
            UserSongCreditUsageType.QUEUE_ADD, periodStart, null, null, 1)));

    JukeboxSplitPeriodDto current = service.getAllPeriods().get(0);
    assertEquals(new BigDecimal("3.00"), current.mobileTotal());
  }

  @Test
  void addSplit_newPeriodStartsStrictlyAfterTheClosingPeriodsEndDate_soBoundariesNeverTie()
      throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));

    FinancialLedgerServiceImpl service = newService();
    service.addSplit();

    List<JukeboxSplitPeriodDto> periods = service.getAllPeriods();
    Instant finalizedEndDate = periods.get(0).endDate();
    Instant newPeriodStartDate = periods.get(1).startDate();

    assertTrue(newPeriodStartDate.isAfter(finalizedEndDate),
        "A tied boundary would let one transaction be counted into (or excluded from) both "
            + "periods depending on comparison direction -- see the local-transaction and mobile "
            + "total computations, both of which assume adjacent periods never share an instant");
  }

  // ── addSplit ─────────────────────────────────────────────────────────────

  @Test
  void addSplit_locksInTotalsAndSplitPercentage_thenOpensAFreshZeroPeriod() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    financialLedgerProperties.setJukeboxSplitPercentage(50);

    FinancialLedgerServiceImpl service = newService();
    service.recordLocalCashCredit(1);
    service.recordLocalCashCredit(1);
    service.recordLocalCreditCardCredit(1);

    service.addSplit();

    List<JukeboxSplitPeriodDto> periods = service.getAllPeriods();
    assertEquals(2, periods.size(), "Finalized period plus a fresh open one");

    // getAllPeriods() returns oldest-first (see its javadoc): the just-finalized period (earlier
    // startDate) sorts before the freshly-opened current one (startDate = now).
    JukeboxSplitPeriodDto finalized = periods.get(0);
    assertEquals(new BigDecimal("2.00"), finalized.cashTotal());
    assertEquals(new BigDecimal("1.00"), finalized.cardTotal());
    assertEquals(new BigDecimal("3.00"), finalized.totalEarned());
    assertEquals(Integer.valueOf(50), finalized.splitPercentageToOwner());
    assertEquals(new BigDecimal("1.50"), finalized.amountDueOwner());
    assertEquals(new BigDecimal("1.50"), finalized.amountDueOperator());
    assertEquals(finalized.amountDueOwner().add(finalized.amountDueOperator()),
        finalized.totalEarned());

    JukeboxSplitPeriodDto current = periods.get(1);
    assertEquals(BigDecimal.ZERO.setScale(2), current.cashTotal());
    assertEquals(BigDecimal.ZERO.setScale(2), current.cardTotal());
    assertNull(current.amountDueOwner());
  }

  @Test
  void addSplit_roundsOwnerAmountHalfUp_andOperatorGetsExactRemainder() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    financialLedgerProperties.setJukeboxSplitPercentage(33);

    FinancialLedgerServiceImpl service = newService();
    // $1 total at 33% -> $0.33 owner (HALF_UP), $0.67 operator -- exact remainder, not a second
    // independent rounding, so the two always sum back to the total to the cent.
    service.recordLocalCashCredit(1);

    service.addSplit();

    // getAllPeriods() returns oldest-first -- the finalized period is index 0.
    JukeboxSplitPeriodDto finalized = service.getAllPeriods().get(0);
    assertEquals(new BigDecimal("0.33"), finalized.amountDueOwner());
    assertEquals(new BigDecimal("0.67"), finalized.amountDueOperator());
    assertTrue(finalized.amountDueOwner().add(finalized.amountDueOperator())
        .compareTo(finalized.totalEarned()) == 0);
  }
}
