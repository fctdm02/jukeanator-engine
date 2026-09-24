package com.djt.jukeanator_engine.domain.financialledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.MasterSyncOutboxEntry;
import com.djt.jukeanator_engine.domain.financialledger.event.JukeboxSplitFinalizedEvent;
import com.djt.jukeanator_engine.domain.financialledger.exception.FinancialLedgerException;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCashTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.repository.FinancialLedgerRepository;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
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
    when(songLibraryService.getOwnLocation()).thenReturn(
        new LocationEntity(OWN_LOCATION_ID, "Own Location", null, null, "test-api-key-hash"));
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
    return newService(false);
  }

  private FinancialLedgerServiceImpl newService(boolean slaveMode) {
    return new FinancialLedgerServiceImpl(financialLedgerRepository, financialLedgerProperties,
        userService, pricingService, songLibraryService, eventPublisher, locationService,
        slaveMode);
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
  void constructor_bootstrapsOpenPeriodWithOwnLocationAsParent() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));

    newService();

    verify(financialLedgerRepository, times(1)).storeAggregateRoot(
        org.mockito.ArgumentMatchers.argThat(root -> root.getCurrentPeriod() != null
            && root.getCurrentPeriod().getParentLocation() != null
            && OWN_LOCATION_ID.equals(
                root.getCurrentPeriod().getParentLocation().getPersistentIdentity())));
  }

  @Test
  void constructor_doesNotOpenAnotherPeriod_whenLoadedRootAlreadyHasAnOpenOne() throws Exception {

    FinancialLedgerRootEntity existingRoot = new FinancialLedgerRootEntity();
    JukeboxSplitPeriodEntity existingPeriod =
        new JukeboxSplitPeriodEntity(Integer.valueOf(1), Instant.now());
    existingRoot.addSplitPeriod(existingPeriod);
    when(financialLedgerRepository.loadAggregateRoot(anyString())).thenReturn(existingRoot);

    newService();

    verify(financialLedgerRepository, never()).storeAggregateRoot(org.mockito.ArgumentMatchers.any());
    assertEquals(OWN_LOCATION_ID, existingPeriod.getParentLocation().getPersistentIdentity(),
        "A loaded period's transient parentLocation should be rewired to the own location");
  }

  @Test
  void constructor_opensNoPeriod_onMasterWithNoOwnLocation() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    when(songLibraryService.getOwnLocationId()).thenReturn(null);
    when(songLibraryService.getOwnLocation()).thenReturn(null);

    FinancialLedgerServiceImpl service = newService();

    verify(financialLedgerRepository, never()).storeAggregateRoot(org.mockito.ArgumentMatchers.any());
    assertTrue(service.getAllPeriods().isEmpty());
    assertThrows(FinancialLedgerException.class, service::addSplit);
  }

  // ── handleOwnLocationIdChangedEvent ─────────────────────────────────────

  @Test
  void handleOwnLocationIdChangedEvent_retagsOnlyTransactionsUnderThePreviousIdAndPersists()
      throws Exception {

    Integer otherLocationId = Integer.valueOf(99);
    Integer confirmedLocationId = Integer.valueOf(42);

    FinancialLedgerRootEntity existingRoot = new FinancialLedgerRootEntity();
    existingRoot.addSplitPeriod(new JukeboxSplitPeriodEntity(Integer.valueOf(1), Instant.now()));
    LocalCashTransactionEntity ownCash =
        new LocalCashTransactionEntity(Integer.valueOf(2), 1, Instant.now(), OWN_LOCATION_ID);
    LocalCashTransactionEntity otherCash =
        new LocalCashTransactionEntity(Integer.valueOf(3), 1, Instant.now(), otherLocationId);
    LocalCreditTransactionEntity ownCard =
        new LocalCreditTransactionEntity(Integer.valueOf(4), 1, Instant.now(), OWN_LOCATION_ID);
    existingRoot.addLocalCashTransaction(ownCash);
    existingRoot.addLocalCashTransaction(otherCash);
    existingRoot.addLocalCreditCardTransaction(ownCard);
    when(financialLedgerRepository.loadAggregateRoot(anyString())).thenReturn(existingRoot);

    FinancialLedgerServiceImpl service = newService();
    service.handleOwnLocationIdChangedEvent(
        new OwnLocationIdChangedEvent(OWN_LOCATION_ID, confirmedLocationId));

    assertEquals(confirmedLocationId, ownCash.getLocationId());
    assertEquals(confirmedLocationId, ownCard.getLocationId());
    assertEquals(otherLocationId, otherCash.getLocationId());
    verify(financialLedgerRepository, times(1)).storeAggregateRoot(existingRoot);
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
                UserSongCreditUsageType.QUEUE_ADD, afterPeriodStart, 1, 2, 80, "sync-1"),
            new UserSongCreditUsageDto("alice@example.com", OWN_LOCATION_ID, -10,
                UserSongCreditUsageType.QUEUE_ACTION, afterPeriodStart, null, null, 70, "sync-2")));

    JukeboxSplitPeriodDto current = service.getAllPeriods().get(0);
    assertEquals(new BigDecimal("10.00"), current.mobileTotal());
    assertEquals(new BigDecimal("10.00"), current.totalEarned());
    assertNull(current.amountDueOwner(), "Open period's owner/operator amounts stay unset until finalized");
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
            UserSongCreditUsageType.QUEUE_ADD, periodStart, null, null, 1, "sync-1")));

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

  @Test
  void addSplit_publishesJukeboxSplitFinalizedEvent_forTheFinalizedPeriod() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));

    FinancialLedgerServiceImpl service = newService();
    Integer finalizedPeriodId = service.getAllPeriods().get(0).persistentIdentity();

    service.addSplit();

    verify(eventPublisher).publishEvent(
        new JukeboxSplitFinalizedEvent(OWN_LOCATION_ID, finalizedPeriodId));
  }

  // ── slave-to-master outbox ───────────────────────────────────────────────

  @Test
  void getPendingMasterSync_returnsUnsyncedTransactionsAndFinalizedPeriods_butNeverTheOpenPeriod()
      throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));

    FinancialLedgerServiceImpl service = newService(true);
    service.recordLocalCashCredit(1);
    service.recordLocalCreditCardCredit(1);

    List<MasterSyncOutboxEntry> beforeSplit = service.getPendingMasterSync();
    assertEquals(List.of(MasterSyncOutboxEntry.Kind.CASH, MasterSyncOutboxEntry.Kind.CREDIT_CARD),
        beforeSplit.stream().map(MasterSyncOutboxEntry::kind).toList(),
        "The open period has no totals yet, so it must never be mirrored");

    service.addSplit();

    List<MasterSyncOutboxEntry> afterSplit = service.getPendingMasterSync();
    assertEquals(3, afterSplit.size());
    MasterSyncOutboxEntry periodEntry = afterSplit.get(2);
    assertEquals(MasterSyncOutboxEntry.Kind.SPLIT_PERIOD, periodEntry.kind());
    assertEquals(OWN_LOCATION_ID, periodEntry.locationId());
    JukeboxSplitPeriodSyncDto periodPayload = (JukeboxSplitPeriodSyncDto) periodEntry.payload();
    assertEquals(periodEntry.persistentIdentity(), periodPayload.sourcePeriodId());
    assertEquals(new BigDecimal("2.00"), periodPayload.totalEarned());
    assertEquals(OWN_LOCATION_ID, afterSplit.get(0).locationId());
    assertEquals(afterSplit.get(0).persistentIdentity(),
        ((LocalTransactionSyncDto) afterSplit.get(0).payload()).sourceTransactionId());
  }

  @Test
  void markSyncedToMaster_removesOnlyTheAcknowledgedEntriesFromTheOutbox_andPersists()
      throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));

    FinancialLedgerServiceImpl service = newService(true);
    service.recordLocalCashCredit(1);
    service.recordLocalCreditCardCredit(1);
    service.addSplit();

    List<MasterSyncOutboxEntry> pending = service.getPendingMasterSync();
    // Acknowledge the cash transaction and the finalized period, but not the card transaction --
    // e.g. its push failed mid-sweep.
    service.markSyncedToMaster(List.of(pending.get(0), pending.get(2)));

    List<MasterSyncOutboxEntry> stillPending = service.getPendingMasterSync();
    assertEquals(1, stillPending.size());
    assertEquals(MasterSyncOutboxEntry.Kind.CREDIT_CARD, stillPending.get(0).kind());
    verify(financialLedgerRepository, org.mockito.Mockito.atLeastOnce()).storeAggregateRoot(
        org.mockito.ArgumentMatchers.argThat(root -> root.getLocalCashTransactions().get(0)
            .getSyncedToMasterAt() != null));
  }

  @Test
  void getPendingMasterSync_attributesATransactionRecordedWithNoLocationToTheNowKnownOwnLocation()
      throws Exception {

    FinancialLedgerRootEntity existingRoot = new FinancialLedgerRootEntity();
    existingRoot.addLocalCashTransaction(
        new LocalCashTransactionEntity(Integer.valueOf(900), 1, Instant.now(), null));
    when(financialLedgerRepository.loadAggregateRoot(anyString())).thenReturn(existingRoot);

    List<MasterSyncOutboxEntry> pending = newService(true).getPendingMasterSync();

    assertEquals(1, pending.size());
    assertEquals(OWN_LOCATION_ID, pending.get(0).locationId());
  }

  // ── master-side split-period mirror intake ───────────────────────────────

  @Test
  void receiveSplitPeriodSync_storesTheMirroredPeriodTaggedWithItsLocation_andIsIdempotent()
      throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    when(songLibraryService.getOwnLocation()).thenReturn(null); // master
    when(locationService.verifyApiKey(Integer.valueOf(42), "key")).thenReturn(true);

    FinancialLedgerServiceImpl service = newService();
    JukeboxSplitPeriodSyncDto dto = new JukeboxSplitPeriodSyncDto(Integer.valueOf(5),
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"), 50,
        new BigDecimal("10.00"), new BigDecimal("4.00"), new BigDecimal("6.00"),
        new BigDecimal("20.00"), new BigDecimal("10.00"), new BigDecimal("10.00"));

    service.receiveSplitPeriodSync(Integer.valueOf(42), "key", dto);
    service.receiveSplitPeriodSync(Integer.valueOf(42), "key", dto);

    List<JukeboxSplitPeriodDto> periods = service.getAllPeriods();
    assertEquals(1, periods.size(), "A retried push must never duplicate the mirrored period");
    assertEquals(Integer.valueOf(42), periods.get(0).locationId());
    assertEquals(Integer.valueOf(5), periods.get(0).sourcePeriodId());
    assertEquals(new BigDecimal("20.00"), periods.get(0).totalEarned());
    verify(financialLedgerRepository, times(1)).storeAggregateRoot(
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void receiveSplitPeriodSync_rejectsWrongApiKey_andAnOpenPeriod() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    when(songLibraryService.getOwnLocation()).thenReturn(null); // master
    when(locationService.verifyApiKey(Integer.valueOf(42), "key")).thenReturn(true);

    FinancialLedgerServiceImpl service = newService();
    JukeboxSplitPeriodSyncDto finalized = new JukeboxSplitPeriodSyncDto(Integer.valueOf(5),
        Instant.now().minusSeconds(60), Instant.now(), 50, BigDecimal.ONE, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE);
    JukeboxSplitPeriodSyncDto open = new JukeboxSplitPeriodSyncDto(Integer.valueOf(6),
        Instant.now(), null, null, null, null, null, null, null, null);

    assertThrows(LocationServiceException.class,
        () -> service.receiveSplitPeriodSync(Integer.valueOf(42), "wrong-key", finalized));
    assertThrows(FinancialLedgerException.class,
        () -> service.receiveSplitPeriodSync(Integer.valueOf(42), "key", open));
    assertTrue(service.getAllPeriods().isEmpty());
  }

  // ── master-to-slave mobile credit usage mirror ───────────────────────────

  @Test
  void receiveMobileCreditUsage_isIdempotentOnSyncId_andCountsTowardTheSlavesMobileTotal()
      throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));

    FinancialLedgerServiceImpl service = newService(true);
    // Pinned at the open period's own startDate (inclusive) -- anything later would still be in the
    // future relative to the live summary's "now" upper bound.
    Instant afterPeriodStart = service.getOpenPeriodStartDate();

    // 30 credits at setUp()'s 3 credits/dollar = $10.00; the duplicate must not double-count.
    service.receiveMobileCreditUsage(new UserSongCreditUsageDto("alice@example.com",
        OWN_LOCATION_ID, -20, UserSongCreditUsageType.QUEUE_ADD, afterPeriodStart, 1, 2, 80,
        "sync-a"));
    service.receiveMobileCreditUsage(new UserSongCreditUsageDto("alice@example.com",
        OWN_LOCATION_ID, -10, UserSongCreditUsageType.QUEUE_ACTION, afterPeriodStart, null,
        null, 70, "sync-b"));
    service.receiveMobileCreditUsage(new UserSongCreditUsageDto("alice@example.com",
        OWN_LOCATION_ID, -20, UserSongCreditUsageType.QUEUE_ADD, afterPeriodStart, 1, 2, 80,
        "sync-a"));

    JukeboxSplitPeriodDto current = service.getAllPeriods().get(0);
    assertEquals(new BigDecimal("10.00"), current.mobileTotal());
    assertEquals(afterPeriodStart, service.getLatestMobileCreditUsageTimestamp());
    // In slave mode the mirrored copy is the source of truth -- the slave's own user store never
    // holds mobile/web spends.
    verify(userService, never()).getCreditLedgerForLocation(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void receiveMobileCreditUsage_ignoresAnotherLocationsSpend_andOneWithNoSyncId()
      throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));

    FinancialLedgerServiceImpl service = newService(true);
    // Pinned at the open period's own startDate (inclusive) -- anything later would still be in the
    // future relative to the live summary's "now" upper bound.
    Instant afterPeriodStart = service.getOpenPeriodStartDate();

    service.receiveMobileCreditUsage(new UserSongCreditUsageDto("alice@example.com",
        Integer.valueOf(999), -20, UserSongCreditUsageType.QUEUE_ADD, afterPeriodStart, 1, 2, 80,
        "sync-other-location"));
    service.receiveMobileCreditUsage(new UserSongCreditUsageDto("alice@example.com",
        OWN_LOCATION_ID, -20, UserSongCreditUsageType.QUEUE_ADD, afterPeriodStart, 1, 2, 80,
        null));

    assertEquals(BigDecimal.ZERO.setScale(2), service.getAllPeriods().get(0).mobileTotal());
    assertNull(service.getLatestMobileCreditUsageTimestamp());
  }

  @Test
  void getMobileCreditUsageForSync_verifiesApiKey_andOmitsSpendsWithNoSyncId() throws Exception {

    when(financialLedgerRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger on disk"));
    when(songLibraryService.getOwnLocation()).thenReturn(null); // master
    when(locationService.verifyApiKey(Integer.valueOf(42), "key")).thenReturn(true);
    Instant since = Instant.parse("2026-01-01T00:00:00Z");
    when(userService.getCreditLedgerForLocation(org.mockito.ArgumentMatchers.eq(Integer.valueOf(42)),
        org.mockito.ArgumentMatchers.eq(since), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(
            new UserSongCreditUsageDto("alice@example.com", Integer.valueOf(42), -2,
                UserSongCreditUsageType.QUEUE_ADD, since.plusSeconds(1), 1, 2, 8, "sync-a"),
            new UserSongCreditUsageDto("bob@example.com", Integer.valueOf(42), -2,
                UserSongCreditUsageType.QUEUE_ADD, since.plusSeconds(2), 1, 2, 8, null)));

    FinancialLedgerServiceImpl service = newService();

    List<UserSongCreditUsageDto> result =
        service.getMobileCreditUsageForSync(Integer.valueOf(42), "key", since);
    assertEquals(1, result.size());
    assertEquals("sync-a", result.get(0).syncId());

    assertThrows(LocationServiceException.class,
        () -> service.getMobileCreditUsageForSync(Integer.valueOf(42), "wrong-key", since));
  }
}
