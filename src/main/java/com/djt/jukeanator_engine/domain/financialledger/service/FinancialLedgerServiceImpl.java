package com.djt.jukeanator_engine.domain.financialledger.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.financialledger.config.FinancialLedgerProperties;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.mapper.FinancialLedgerMapper;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCashTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.repository.FinancialLedgerRepository;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.user.dto.CreditTransactionDto;
import com.djt.jukeanator_engine.domain.user.service.PricingService;
import com.djt.jukeanator_engine.domain.user.service.UserService;

public class FinancialLedgerServiceImpl implements FinancialLedgerService {

  private static final Logger log = LoggerFactory.getLogger(FinancialLedgerServiceImpl.class);

  private final FinancialLedgerRepository financialLedgerRepository;
  private final FinancialLedgerProperties financialLedgerProperties;
  private final UserService userService;
  private final PricingService pricingService;
  private final SongLibraryService songLibraryService;

  private FinancialLedgerRootEntity ledgerRoot;

  public FinancialLedgerServiceImpl(FinancialLedgerRepository financialLedgerRepository,
      FinancialLedgerProperties financialLedgerProperties, UserService userService,
      PricingService pricingService, SongLibraryService songLibraryService) {

    this.financialLedgerRepository = financialLedgerRepository;
    this.financialLedgerProperties = financialLedgerProperties;
    this.userService = userService;
    this.pricingService = pricingService;
    this.songLibraryService = songLibraryService;

    initialize();
  }

  private synchronized void initialize() {

    try {
      this.ledgerRoot = this.financialLedgerRepository.loadAggregateRoot("FinancialLedgerRootEntity");
    } catch (EntityDoesNotExistException ednee) {
      log.info("No existing financial ledger found -- starting a fresh one");
      this.ledgerRoot = new FinancialLedgerRootEntity();
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

    ledgerRoot.addLocalCashTransaction(new LocalCashTransactionEntity(persistentIdentity,
        amountDollars, Instant.now(), ownLocationId));

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);
  }

  @Override
  public synchronized void recordLocalCreditCardCredit(int amountDollars) {

    Integer persistentIdentity = financialLedgerRepository.nextPersistentIdentity();
    Integer ownLocationId = songLibraryService.getOwnLocationId();

    ledgerRoot.addLocalCreditCardTransaction(new LocalCreditTransactionEntity(persistentIdentity,
        amountDollars, Instant.now(), ownLocationId));

    financialLedgerRepository.storeAggregateRoot(ledgerRoot);
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
  }

  private void openNewPeriod(Instant startDate) {

    Integer persistentIdentity = financialLedgerRepository.nextPersistentIdentity();
    ledgerRoot.addSplitPeriod(new JukeboxSplitPeriodEntity(persistentIdentity, startDate));
  }

  private JukeboxSplitPeriodDto liveSummaryOf(JukeboxSplitPeriodEntity openPeriod) {

    PeriodTotals totals = computeTotals(openPeriod.getStartDate(), Instant.now());

    return new JukeboxSplitPeriodDto(openPeriod.getPersistentIdentity(),
        openPeriod.getStartDate(), null, null, totals.cash(), totals.card(), totals.mobile(),
        totals.total(), null, null);
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

    // UserService.getCreditLedgerForLocation is inclusive at both ends -- safe here because
    // addSplit() guarantees adjacent periods' boundaries never actually coincide (the new
    // period's startDate is always strictly after the closing period's endDate), so a mobile
    // credit can never fall within both periods' [from, to] ranges at once.
    List<CreditTransactionDto> ledger = userService.getCreditLedgerForLocation(ownLocationId, from, to);

    int creditsUsed = ledger.stream()
        .filter(t -> t.amount() < 0)
        .mapToInt(t -> -t.amount())
        .sum();

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
