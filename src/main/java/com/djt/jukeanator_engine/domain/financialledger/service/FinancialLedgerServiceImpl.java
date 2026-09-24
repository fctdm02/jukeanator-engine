package com.djt.jukeanator_engine.domain.financialledger.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import com.djt.jukeanator_engine.domain.financialledger.config.FinancialLedgerProperties;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.MasterSyncOutboxEntry;
import com.djt.jukeanator_engine.domain.financialledger.event.JukeboxSplitFinalizedEvent;
import com.djt.jukeanator_engine.domain.financialledger.event.LocalFinancialTransactionRecordedEvent;
import com.djt.jukeanator_engine.domain.financialledger.exception.FinancialLedgerException;
import com.djt.jukeanator_engine.domain.financialledger.mapper.FinancialLedgerMapper;
import com.djt.jukeanator_engine.domain.financialledger.model.AbstractLocationTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCashTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocationMobileCreditUsageEntity;
import com.djt.jukeanator_engine.domain.financialledger.repository.FinancialLedgerRepository;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import com.djt.jukeanator_engine.domain.user.service.PricingService;
import com.djt.jukeanator_engine.domain.user.service.UserService;

public class FinancialLedgerServiceImpl implements FinancialLedgerService {

  private static final Logger log = LoggerFactory.getLogger(FinancialLedgerServiceImpl.class);

  private final FinancialLedgerRepository financialLedgerRepository;
  private final FinancialLedgerProperties financialLedgerProperties;
  private final UserService userService;
  private final PricingService pricingService;
  private final SongLibraryService songLibraryService;
  private final ApplicationEventPublisher eventPublisher;
  private final LocationService locationService;
  private final boolean slaveMode;

  private FinancialLedgerRootEntity ledgerRoot;

  public FinancialLedgerServiceImpl(FinancialLedgerRepository financialLedgerRepository,
      FinancialLedgerProperties financialLedgerProperties, UserService userService,
      PricingService pricingService, SongLibraryService songLibraryService,
      ApplicationEventPublisher eventPublisher, LocationService locationService,
      boolean slaveMode) {

    this.financialLedgerRepository = financialLedgerRepository;
    this.financialLedgerProperties = financialLedgerProperties;
    this.userService = userService;
    this.pricingService = pricingService;
    this.songLibraryService = songLibraryService;
    this.eventPublisher = eventPublisher;
    this.locationService = locationService;
    this.slaveMode = slaveMode;

    initialize();
  }

  private synchronized void initialize() {

    try {
      this.ledgerRoot = this.financialLedgerRepository.loadAggregateRoot("FinancialLedgerRootEntity");
    } catch (EntityDoesNotExistException ednee) {
      log.info("No existing financial ledger found -- starting a fresh one");
      this.ledgerRoot = new FinancialLedgerRootEntity();
    }

    // Master is location-agnostic and has no jukebox of its own (see SongLibraryServiceImpl's
    // initialize()), so it never opens a split period of its own either.
    LocationEntity ownLocation = songLibraryService.getOwnLocation();
    if (ownLocation == null) {
      log.info("Master instance -- no own location; no jukebox-split period opened.");
      return;
    }

    // Not persisted by either repository -- reconstructed here after any load, same as
    // SongLibraryServiceImpl wiring RootFolderEntity's parentLocation. Every period in this
    // instance's own ledger belongs to its own location.
    for (JukeboxSplitPeriodEntity period : ledgerRoot.getSplitPeriods()) {
      period.setParentLocation(ownLocation);
    }

    if (ledgerRoot.getCurrentPeriod() == null) {
      openNewPeriod(Instant.now());
      this.financialLedgerRepository.storeAggregateRoot(ledgerRoot);
    }
  }

  @Override
  public synchronized void recordLocalCashCredit(int amountDollars) {

    Integer persistentIdentity = financialLedgerRepository.nextPersistentIdentity();
    Integer ownLocationId = songLibraryService.getOwnLocationId();
    Instant timestamp = Instant.now();

    ledgerRoot.addLocalCashTransaction(new LocalCashTransactionEntity(persistentIdentity,
        amountDollars, timestamp, ownLocationId));

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);

    // Harmless on standalone/master -- only a slave's FinancialLedgerSyncService listens.
    eventPublisher.publishEvent(new LocalFinancialTransactionRecordedEvent(
        LocalFinancialTransactionRecordedEvent.Kind.CASH, ownLocationId, persistentIdentity,
        amountDollars, timestamp));
  }

  @Override
  public synchronized void recordLocalCreditCardCredit(int amountDollars) {

    Integer persistentIdentity = financialLedgerRepository.nextPersistentIdentity();
    Integer ownLocationId = songLibraryService.getOwnLocationId();
    Instant timestamp = Instant.now();

    ledgerRoot.addLocalCreditCardTransaction(new LocalCreditTransactionEntity(persistentIdentity,
        amountDollars, timestamp, ownLocationId));

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);

    eventPublisher.publishEvent(new LocalFinancialTransactionRecordedEvent(
        LocalFinancialTransactionRecordedEvent.Kind.CREDIT_CARD, ownLocationId, persistentIdentity,
        amountDollars, timestamp));
  }

  @Override
  public synchronized void receiveLocalCashSync(Integer locationId, String apiKey,
      LocalTransactionSyncDto dto) throws LocationServiceException {

    requireValidLocation(locationId, apiKey);

    if (ledgerRoot.hasLocalCashTransactionFromSource(locationId, dto.sourceTransactionId())) {
      return; // already mirrored -- safe no-op on a retried push
    }

    Integer persistentIdentity = financialLedgerRepository.nextPersistentIdentity();
    ledgerRoot.addLocalCashTransaction(new LocalCashTransactionEntity(persistentIdentity,
        dto.amountDollars(), dto.timestamp(), locationId, dto.sourceTransactionId()));

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);
  }

  @Override
  public synchronized void receiveLocalCreditCardSync(Integer locationId, String apiKey,
      LocalTransactionSyncDto dto) throws LocationServiceException {

    requireValidLocation(locationId, apiKey);

    if (ledgerRoot.hasLocalCreditCardTransactionFromSource(locationId, dto.sourceTransactionId())) {
      return; // already mirrored -- safe no-op on a retried push
    }

    Integer persistentIdentity = financialLedgerRepository.nextPersistentIdentity();
    ledgerRoot.addLocalCreditCardTransaction(new LocalCreditTransactionEntity(persistentIdentity,
        dto.amountDollars(), dto.timestamp(), locationId, dto.sourceTransactionId()));

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);
  }

  @Override
  public synchronized void receiveSplitPeriodSync(Integer locationId, String apiKey,
      JukeboxSplitPeriodSyncDto dto) throws LocationServiceException {

    requireValidLocation(locationId, apiKey);

    if (dto.endDate() == null) {
      throw new FinancialLedgerException(
          "Only finalized jukebox-split periods can be mirrored -- sourcePeriodId: "
              + dto.sourcePeriodId() + ", locationId: " + locationId);
    }

    if (ledgerRoot.hasSplitPeriodFromSource(locationId, dto.sourcePeriodId())) {
      return; // already mirrored -- safe no-op on a retried push
    }

    Integer persistentIdentity = financialLedgerRepository.nextPersistentIdentity();
    ledgerRoot.addSplitPeriod(JukeboxSplitPeriodEntity.mirroredFrom(persistentIdentity,
        locationId, dto.sourcePeriodId(), dto.startDate(), dto.endDate(),
        dto.splitPercentageToOwner(), dto.cashTotal(), dto.cardTotal(), dto.mobileTotal(),
        dto.totalEarned(), dto.amountDueOwner(), dto.amountDueOperator()));

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);
  }

  @Override
  public synchronized List<MasterSyncOutboxEntry> getPendingMasterSync() {

    // A transaction recorded before this slave's own location was established carries no
    // locationId -- attribute it to the (now known) own location rather than never mirroring it.
    Integer ownLocationId = songLibraryService.getOwnLocationId();

    List<MasterSyncOutboxEntry> entries = new ArrayList<>();
    for (LocalCashTransactionEntity transaction : ledgerRoot.getLocalCashTransactions()) {
      addPendingTransaction(entries, MasterSyncOutboxEntry.Kind.CASH, transaction, ownLocationId);
    }
    for (LocalCreditTransactionEntity transaction : ledgerRoot.getLocalCreditCardTransactions()) {
      addPendingTransaction(entries, MasterSyncOutboxEntry.Kind.CREDIT_CARD, transaction,
          ownLocationId);
    }
    for (JukeboxSplitPeriodEntity period : ledgerRoot.getSplitPeriods()) {
      Integer locationId = period.getLocationId() != null ? period.getLocationId() : ownLocationId;
      if (period.isPendingMasterSync() && locationId != null) {
        entries.add(new MasterSyncOutboxEntry(MasterSyncOutboxEntry.Kind.SPLIT_PERIOD, locationId,
            period.getPersistentIdentity(), FinancialLedgerMapper.toSyncDto(period)));
      }
    }
    return entries;
  }

  private static void addPendingTransaction(List<MasterSyncOutboxEntry> entries,
      MasterSyncOutboxEntry.Kind kind, AbstractLocationTransactionEntity transaction,
      Integer ownLocationId) {

    Integer locationId =
        transaction.getLocationId() != null ? transaction.getLocationId() : ownLocationId;
    if (transaction.isPendingMasterSync() && locationId != null) {
      entries.add(new MasterSyncOutboxEntry(kind, locationId, transaction.getPersistentIdentity(),
          FinancialLedgerMapper.toSyncDto(transaction)));
    }
  }

  @Override
  public synchronized void markSyncedToMaster(List<MasterSyncOutboxEntry> entries) {

    if (entries.isEmpty()) {
      return;
    }

    Instant now = Instant.now();
    for (MasterSyncOutboxEntry entry : entries) {
      Integer id = entry.persistentIdentity();
      switch (entry.kind()) {
        case CASH -> ledgerRoot.getLocalCashTransactions().stream()
            .filter(t -> id.equals(t.getPersistentIdentity()))
            .forEach(t -> t.markSyncedToMaster(now));
        case CREDIT_CARD -> ledgerRoot.getLocalCreditCardTransactions().stream()
            .filter(t -> id.equals(t.getPersistentIdentity()))
            .forEach(t -> t.markSyncedToMaster(now));
        case SPLIT_PERIOD -> ledgerRoot.getSplitPeriods().stream()
            .filter(p -> id.equals(p.getPersistentIdentity()))
            .forEach(p -> p.markSyncedToMaster(now));
      }
    }

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);
  }

  @Override
  public List<UserSongCreditUsageDto> getMobileCreditUsageForSync(Integer locationId,
      String apiKey, Instant since) throws LocationServiceException {

    requireValidLocation(locationId, apiKey);

    return userService.getCreditLedgerForLocation(locationId, since, Instant.now()).stream()
        .filter(usage -> usage.syncId() != null)
        .toList();
  }

  @Override
  public synchronized void receiveMobileCreditUsage(UserSongCreditUsageDto usage) {

    if (usage.syncId() == null) {
      log.warn("Skipping mobile credit usage with no syncId -- it can't be mirrored idempotently");
      return;
    }

    // Master only ever sends this slave its own location's spends; anything else is a
    // misrouted push and must never be attributed to this location.
    Integer ownLocationId = songLibraryService.getOwnLocationId();
    if (ownLocationId == null || !ownLocationId.equals(usage.locationId())) {
      log.warn("Skipping mobile credit usage " + usage.syncId() + " for locationId "
          + usage.locationId() + " -- this instance's own locationId is " + ownLocationId);
      return;
    }

    if (ledgerRoot.hasMobileCreditUsageFromSource(usage.syncId())) {
      return; // already mirrored -- safe no-op on a live push plus catch-up pull
    }

    Integer persistentIdentity = financialLedgerRepository.nextPersistentIdentity();
    ledgerRoot.addMobileCreditUsage(new LocationMobileCreditUsageEntity(persistentIdentity,
        ownLocationId, usage.syncId(), usage.userEmail(), usage.amount(), usage.type(),
        usage.timestamp(), usage.songAlbumId(), usage.songId()));

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);
  }

  @Override
  public synchronized Instant getOpenPeriodStartDate() {

    JukeboxSplitPeriodEntity currentPeriod = ledgerRoot.getCurrentPeriod();
    return currentPeriod != null ? currentPeriod.getStartDate() : null;
  }

  @Override
  public synchronized Instant getLatestMobileCreditUsageTimestamp() {
    return ledgerRoot.getLatestMobileCreditUsageTimestamp();
  }

  /**
   * Re-tags this instance's in-memory local transactions from the previous own location id to the
   * confirmed one and persists them -- under JPA the rows were already re-pointed by {@code
   * LocationRepositoryJpaImpl.changeLocationId}, so without this the next store would merge the
   * previous id back over them (and under the filesystem repository, nothing else re-tags them).
   * Split periods need no re-tagging -- their transient parentLocation is the very LocationEntity
   * instance LocationService just re-keyed in place.
   */
  @EventListener
  @Override
  public synchronized void handleOwnLocationIdChangedEvent(OwnLocationIdChangedEvent event) {

    ledgerRoot.changeLocationId(event.previousLocationId(), event.confirmedLocationId());
    financialLedgerRepository.storeAggregateRoot(ledgerRoot);
  }

  // Re-verifies locationId+apiKey even though the security filter chain already authenticated
  // *some* location's credentials -- it never confirms those credentials belong to *this* path's
  // locationId. Mirrors LocationServiceImpl.requireValidLocation exactly.
  private void requireValidLocation(Integer locationId, String apiKey) {

    if (!locationService.verifyApiKey(locationId, apiKey)) {
      throw new LocationServiceException("Invalid locationId/apiKey for locationId: " + locationId);
    }
  }

  @Override
  public synchronized List<JukeboxSplitPeriodDto> getAllPeriods() {

    List<JukeboxSplitPeriodDto> result = new ArrayList<>();
    for (JukeboxSplitPeriodEntity period : ledgerRoot.getSplitPeriods()) {
      result.add(period.isOpen() ? liveSummaryOf(period) : FinancialLedgerMapper.toDto(period));
    }
    return result;
  }

  @Override
  public synchronized void addSplit() {

    JukeboxSplitPeriodEntity currentPeriod = ledgerRoot.getCurrentPeriod();
    if (currentPeriod == null) {
      throw new FinancialLedgerException(
          "No open jukebox-split period -- addSplit() is not supported on a master instance");
    }
    Instant now = Instant.now();

    PeriodTotals totals = computeTotals(currentPeriod.getStartDate(), now);
    int splitPercentageToOwner = financialLedgerProperties.getJukeboxSplitPercentage();

    BigDecimal amountDueOwner = totals.total()
        .multiply(BigDecimal.valueOf(splitPercentageToOwner))
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    BigDecimal amountDueOperator = totals.total().subtract(amountDueOwner);

    currentPeriod.finalizePeriod(now, splitPercentageToOwner, totals.cash(), totals.card(),
        totals.mobile(), totals.total(), amountDueOwner, amountDueOperator);

    // Strictly after `now` (never equal to it) so the new period's own startDate can never tie
    // with the just-closed period's endDate -- both boundaries are inclusive-at-from (see
    // FinancialLedgerRootEntity's local-transaction "Since" methods and computeMobileTotal
    // below), so a shared instant would otherwise double-count whatever landed exactly on it.
    openNewPeriod(now.plusNanos(1));

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);

    // Harmless on standalone -- only a slave's FinancialLedgerSyncService listens.
    eventPublisher.publishEvent(new JukeboxSplitFinalizedEvent(currentPeriod.getLocationId(),
        currentPeriod.getPersistentIdentity()));
  }

  private void openNewPeriod(Instant startDate) {

    Integer persistentIdentity = financialLedgerRepository.nextPersistentIdentity();
    JukeboxSplitPeriodEntity period = new JukeboxSplitPeriodEntity(persistentIdentity, startDate);
    period.setParentLocation(songLibraryService.getOwnLocation());
    ledgerRoot.addSplitPeriod(period);
  }

  private JukeboxSplitPeriodDto liveSummaryOf(JukeboxSplitPeriodEntity openPeriod) {

    PeriodTotals totals = computeTotals(openPeriod.getStartDate(), Instant.now());

    return new JukeboxSplitPeriodDto(openPeriod.getPersistentIdentity(),
        openPeriod.getStartDate(), null, null, totals.cash(), totals.card(), totals.mobile(),
        totals.total(), null, null, openPeriod.getLocationId(), null, null);
  }

  private PeriodTotals computeTotals(Instant from, Instant to) {

    BigDecimal cash = BigDecimal.ZERO;
    for (LocalCashTransactionEntity transaction : ledgerRoot.getLocalCashTransactionsSince(from)) {
      if (!transaction.getTimestamp().isAfter(to)) {
        cash = cash.add(BigDecimal.valueOf(transaction.getAmountDollars()));
      }
    }

    BigDecimal card = BigDecimal.ZERO;
    for (LocalCreditTransactionEntity transaction :
        ledgerRoot.getLocalCreditCardTransactionsSince(from)) {
      if (!transaction.getTimestamp().isAfter(to)) {
        card = card.add(BigDecimal.valueOf(transaction.getAmountDollars()));
      }
    }

    BigDecimal mobile = computeMobileTotal(from, to);

    return new PeriodTotals(cash.setScale(2, RoundingMode.HALF_UP),
        card.setScale(2, RoundingMode.HALF_UP), mobile.setScale(2, RoundingMode.HALF_UP),
        cash.add(card).add(mobile).setScale(2, RoundingMode.HALF_UP));
  }

  private BigDecimal computeMobileTotal(Instant from, Instant to) {

    Integer ownLocationId = songLibraryService.getOwnLocationId();
    if (ownLocationId == null) {
      return BigDecimal.ZERO;
    }

    // Both sources are inclusive at both ends -- safe here because addSplit() guarantees adjacent
    // periods' boundaries never actually coincide (the new period's startDate is always strictly
    // after the closing period's endDate), so a mobile credit can never fall within both periods'
    // [from, to] ranges at once.
    int creditsUsed;
    if (slaveMode) {
      // A slave's own user store never records mobile/web spends (user accounts and credits are
      // master-owned) -- master mirrors them down into this ledger instead.
      creditsUsed = ledgerRoot.getMobileCreditUsagesBetween(ownLocationId, from, to).stream()
          .filter(u -> u.getAmount() < 0)
          .mapToInt(u -> -u.getAmount())
          .sum();
    } else {
      creditsUsed = userService.getCreditLedgerForLocation(ownLocationId, from, to).stream()
          .filter(t -> t.amount() < 0)
          .mapToInt(t -> -t.amount())
          .sum();
    }

    int creditsPerDollar = pricingService.resolvePricingConfig(ownLocationId).creditsPerDollar();
    if (creditsPerDollar <= 0) {
      return BigDecimal.ZERO;
    }

    return BigDecimal.valueOf(creditsUsed)
        .divide(BigDecimal.valueOf(creditsPerDollar), 2, RoundingMode.HALF_UP);
  }

  private record PeriodTotals(BigDecimal cash, BigDecimal card, BigDecimal mobile,
      BigDecimal total) {
  }
}
