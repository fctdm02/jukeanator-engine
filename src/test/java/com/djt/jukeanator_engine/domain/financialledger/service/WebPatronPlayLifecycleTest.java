package com.djt.jukeanator_engine.domain.financialledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.financialledger.config.FinancialLedgerProperties;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalMobileTransactionDto;
import com.djt.jukeanator_engine.domain.financialledger.event.MobilePurchaseRecordedEvent;
import com.djt.jukeanator_engine.domain.financialledger.repository.FinancialLedgerRepository;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.service.GeoFenceService;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.controller.SongQueueController;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsResponseDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import com.djt.jukeanator_engine.domain.user.event.LocationSongCreditUsageRecordedEvent;
import com.djt.jukeanator_engine.domain.user.event.PurchaseCompletedEvent;
import com.djt.jukeanator_engine.domain.user.exception.InsufficientCreditsException;
import com.djt.jukeanator_engine.domain.user.exception.InvalidCredentialsException;
import com.djt.jukeanator_engine.domain.user.exception.QueueAccessDeniedException;
import com.djt.jukeanator_engine.domain.user.model.UserAddFundsTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserLocationAccountEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;
import com.djt.jukeanator_engine.domain.user.service.CreditPackageCatalog;
import com.djt.jukeanator_engine.domain.user.service.PaymentChargeResult;
import com.djt.jukeanator_engine.domain.user.service.PaymentGateway;
import com.djt.jukeanator_engine.domain.user.service.PricingConfig;
import com.djt.jukeanator_engine.domain.user.service.PricingService;
import com.djt.jukeanator_engine.domain.user.service.UserService;
import com.djt.jukeanator_engine.domain.user.service.UserServiceImpl;

/**
 * The whole life of a Web/Mobile UI patron at a real location, from sign-up to the bar owner's
 * split, through the real production classes -- {@link SongQueueController}, {@link
 * UserServiceImpl} and {@link FinancialLedgerServiceImpl} -- with only their storage, the payment
 * processor and the song queue itself stood in for. Fast and deterministic: no Spring context, no
 * database, no network.
 *
 * <p>The production deployment is a master (web accounts, credits, Add Funds) serving patrons at a
 * slave (the bar's jukebox and its own financial ledger). Master's purchases and spends reach the
 * slave the way {@code MobileTransactionSlaveNotifier} and {@code MobileCreditUsageSlaveNotifier}
 * push them -- synchronously here -- into the slave's {@link FinancialLedgerServiceImpl}, whose
 * split periods are what the bar owner is paid from.
 *
 * <p>The rules this locks in:
 * <ul>
 * <li>A new account holds no credits anywhere; every credit spent at a location was bought for
 * that location through Add Funds, and is usable only there.</li>
 * <li>An operation the balance at that location does not cover is refused before it runs --
 * nothing is queued, moved or charged -- so no play is ever free.</li>
 * <li>A location's mobile revenue is the Add Funds purchases made for it: each recorded once, in
 * full, when paid -- like a swipe on its own card reader -- however the credits are later
 * spent.</li>
 * <li>Every spend is still recorded against its location as an audit trail, for exactly the
 * credits the balance lost, and mirrored only to that location.</li>
 * <li>Closing an account keeps its financial records.</li>
 * </ul>
 */
class WebPatronPlayLifecycleTest {

  /** The bar the patron is sitting in. */
  private static final Integer BAR = Integer.valueOf(42);
  private static final Integer OTHER_BAR = Integer.valueOf(43);

  private static final String PATRON = "patron@example.com";
  private static final String ADMIN = "admin@example.com";
  private static final int ALBUM_ID = 3;

  /**
   * The bar's pricing, as in application.yml: priorityCostMultiplier 2, creditsPerDollar 3 (the
   * kiosk's bill-acceptor rate), webCostMultiplier 2. A normal play costs 2 credits, a priority-3
   * play (jumping a priority-2 song) (3 - 1) * 2 * 2 = 8, and moving a priority-1 song
   * max(1, 1 * 3) * 2 = 6.
   */
  private static final PricingConfig BAR_PRICING = new PricingConfig(2, 3, 3, 10, 2, false);

  /** The other bar's: webCostMultiplier 1 -- a normal play there costs 1 credit. */
  private static final PricingConfig OTHER_BAR_PRICING = new PricingConfig(2, 4, 3, 10, 1, false);

  private PricingService pricingService;
  private UserRepository masterUserRepository;
  private PaymentGateway paymentGateway;
  private SongQueueService masterSongQueue;
  private UserServiceImpl masterUserService;
  private SongQueueController masterController;

  /** The bar's own (slave) ledger, which the bar owner's split is computed from. */
  private FinancialLedgerServiceImpl barLedger;

  /** Master's ledger, where Web/Mobile purchases are recorded before being mirrored down. */
  private FinancialLedgerServiceImpl masterLedger;

  /** Spends master pushed toward any location other than {@link #BAR}. */
  private final List<UserSongCreditUsageDto> pushedElsewhere = new CopyOnWriteArrayList<>();
  /** Purchases master pushed toward any location other than {@link #BAR}. */
  private final List<LocalMobileTransactionDto> purchasesPushedElsewhere =
      new CopyOnWriteArrayList<>();
  private final List<Object> masterEvents = new CopyOnWriteArrayList<>();
  private final Map<Integer, List<SongQueueEntryDto>> slaveQueues = new ConcurrentHashMap<>();
  private final AtomicInteger nextChargeId = new AtomicInteger(1);

  @BeforeEach
  void setUp() throws Exception {

    pricingService = mock(PricingService.class);
    when(pricingService.resolvePricingConfig(any())).thenReturn(BAR_PRICING);
    when(pricingService.resolvePricingConfig(OTHER_BAR)).thenReturn(OTHER_BAR_PRICING);
    when(pricingService.resolveCreditPackages(any()))
        .thenReturn(CreditPackageCatalog.DEFAULT_PACKAGES);

    // Opened first, so the bar's open split period starts before any spend.
    barLedger = newLedger(BAR, mock(UserService.class), true);

    masterUserRepository = mock(UserRepository.class);
    when(masterUserRepository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no users yet"));

    paymentGateway = mock(PaymentGateway.class);
    when(paymentGateway.charge(any(BigDecimal.class), anyString())).thenAnswer(invocation ->
        PaymentChargeResult.success("txn-" + nextChargeId.getAndIncrement(), "Visa •••• 1111"));

    // Master owns no location of its own: getOwnLocationId() is null (stubbed explicitly, since an
    // unstubbed Integer method on a Mockito mock returns 0).
    SongLibraryService masterSongLibrary = mock(SongLibraryService.class);
    when(masterSongLibrary.getOwnLocationId()).thenReturn(null);

    masterUserService = new UserServiceImpl(masterUserRepository, new PlainTextPasswordEncoder(),
        mock(JwtUtil.class), this::publishOnMaster, masterSongLibrary, pricingService, false,
        paymentGateway);
    masterLedger = newMasterLedger(masterUserService);
    masterUserService.setFinancialLedgerServiceSupplier(() -> masterLedger);

    // Master forwards every queue operation to the location's slave; the slaves' queues are
    // simulated here.
    masterSongQueue = mock(SongQueueService.class);
    when(masterSongQueue.addSongToQueue(any(), any())).thenAnswer(
        invocation -> queueOnSlave(invocation.getArgument(0), invocation.getArgument(1)));
    when(masterSongQueue.getQueuedSongs(any()))
        .thenAnswer(invocation -> queuedAt(invocation.getArgument(0)));
    when(masterSongQueue.moveSongUpInQueue(any(), any())).thenReturn(1);

    masterController = new SongQueueController(masterSongQueue, masterUserService,
        masterSongLibrary, mock(GeoFenceService.class));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // The production deployment: master + the bar's slave
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void patronAtABar_fromSignUpToTheBarOwnersSplit() throws Exception {

    // ── 1. Sign up: a new account holds no credits, so nothing can be played yet ──────────
    masterUserService.register(new RegisterRequest("Pat", "Ron", PATRON, "secret123"));
    assertEquals(0, balance());

    InsufficientCreditsException refused =
        assertThrows(InsufficientCreditsException.class, () -> play(BAR, 1, 1, false));
    assertEquals(2, refused.getCost());
    assertEquals(0, refused.getBalance());
    verify(masterSongQueue, never()).addSongToQueue(any(), any());

    // ── 2. Add Funds at the bar: $7.00 buys 12 + 2 bonus = 14 credits, usable only here ───
    AddFundsResponseDto purchase =
        masterUserService.addFunds(PATRON, new AddFundsRequest(BAR, "pkg-7", "nonce-1"));

    verify(paymentGateway).charge(new BigDecimal("7.00"), "nonce-1");
    assertEquals(Integer.valueOf(14), purchase.numCredits());
    assertEquals(BAR, purchase.locationId());
    assertEquals(12, purchase.creditsAdded());
    assertEquals(2, purchase.bonusCreditsAdded());
    assertEquals("txn-1", purchase.transactionId());
    assertEquals(1, eventsOfType(PurchaseCompletedEvent.class).size());
    assertEquals(0, balanceAt(OTHER_BAR), "credits bought for the bar are not usable elsewhere");
    assertMoney("7.00", openPeriod(barLedger).mobileTotal(),
        "the whole purchase is the bar's revenue the moment it is paid, like a card swipe");

    // ── 3. Playing at the bar, until the balance runs out ─────────────────────────────────
    play(BAR, 1, 1, false); // normal play: 2 credits
    assertEquals(12, balance());
    play(BAR, 2, 3, true); // priority-3 play: 8 credits
    assertEquals(4, balance());

    // Moving song 1 up costs 6; only 4 are held -- refused, and the queue is untouched.
    assertThrows(InsufficientCreditsException.class, () -> moveUp(BAR, 1));
    verify(masterSongQueue, never()).moveSongUpInQueue(any(), any());
    assertEquals(4, balance());

    play(BAR, 3, 1, false);
    assertEquals(2, balance());
    play(BAR, 4, 1, false);
    assertEquals(0, balance(), "the package's 14 credits buy whole plays, none left over");
    assertThrows(InsufficientCreditsException.class, () -> play(BAR, 5, 1, false));
    assertEquals(0, balance(), "a refused play is never charged");

    assertEquals(4, queuedAt(BAR).size(), "only the four paid plays reached the jukebox");
    assertTrue(queuedAt(BAR).stream().allMatch(entry -> PATRON.equals(entry.username())));
    assertMoney("7.00", openPeriod(barLedger).mobileTotal(),
        "spending the credits moves no money -- it was counted when paid");

    // ── 4. Topping up, and playing at a second bar ────────────────────────────────────────
    masterUserService.addFunds(PATRON, new AddFundsRequest(BAR, "pkg-7", "nonce-2"));
    assertEquals(14, balance());
    moveUp(BAR, 1); // 6 credits
    assertEquals(8, balance());

    // The bar's credits buy nothing at the other bar: it needs its own purchase.
    assertThrows(InsufficientCreditsException.class, () -> play(OTHER_BAR, 9, 1, false));
    masterUserService.addFunds(PATRON, new AddFundsRequest(OTHER_BAR, "pkg-7", "nonce-3"));
    play(OTHER_BAR, 9, 1, false); // 1 credit at the other bar's pricing
    assertEquals(13, balanceAt(OTHER_BAR));
    assertEquals(8, balance(), "the bar's balance is untouched by playing elsewhere");
    verify(paymentGateway, times(3)).charge(any(BigDecimal.class), anyString());

    // ── 5. Master's record accounts for every credit bought ───────────────────────────────
    List<UserSongCreditUsageDto> barSpends = spendsAt(BAR);
    assertEquals(List.of(-2, -8, -2, -2, -6),
        barSpends.stream().map(UserSongCreditUsageDto::amount).toList());
    assertEquals(List.of(UserSongCreditUsageType.QUEUE_ADD, UserSongCreditUsageType.QUEUE_ADD,
        UserSongCreditUsageType.QUEUE_ADD, UserSongCreditUsageType.QUEUE_ADD,
        UserSongCreditUsageType.QUEUE_ACTION),
        barSpends.stream().map(UserSongCreditUsageDto::type).toList());
    assertEquals(List.of(12, 4, 2, 0, 8),
        barSpends.stream().map(UserSongCreditUsageDto::resultingBalance).toList());
    assertEquals(List.of(1, 2, 3, 4), barSpends.stream().limit(4)
        .map(UserSongCreditUsageDto::songId).toList());
    assertTrue(barSpends.stream().allMatch(spend -> PATRON.equals(spend.userEmail())));

    List<UserSongCreditUsageDto> otherBarSpends = spendsAt(OTHER_BAR);
    assertEquals(List.of(-1), otherBarSpends.stream().map(UserSongCreditUsageDto::amount).toList());

    UserLocationAccountEntity atBar = storedUser(PATRON).getLocationAccountNullIfNotExists(BAR);
    List<UserAddFundsTransactionEntity> purchases =
        new ArrayList<>(atBar.getAddFundsTransactions());
    assertEquals(2, purchases.size());
    assertTrue(purchases.stream().allMatch(
        p -> new BigDecimal("7.00").compareTo(p.getAmountUsd()) == 0 && BAR.equals(p.getLocationId())));
    assertEquals(List.of("txn-1", "txn-2"), purchases.stream()
        .map(UserAddFundsTransactionEntity::getPaymentTransactionId).sorted().toList());

    int creditsBought = purchases.stream()
        .mapToInt(p -> p.getCreditsAwarded() + p.getBonusCredits()).sum();
    int creditsSpent = -atBar.getSongCreditUsages().stream().mapToInt(u -> u.getAmount()).sum();
    assertEquals(28, creditsBought);
    assertEquals(20, creditsSpent);
    assertEquals(creditsBought - creditsSpent, balance(),
        "the bar's balance is exactly what was bought there minus what was spent there");

    // ── 6. The bar's own ledger holds exactly the bar's purchases and spends ──────────────
    assertEquals(List.of("txn-1", "txn-2"), purchasesMirroredToBar().stream()
        .map(LocalMobileTransactionDto::paymentReference).toList());
    assertEquals(List.of(OTHER_BAR), purchasesPushedElsewhere.stream()
        .map(LocalMobileTransactionDto::locationId).toList());
    assertEquals(barSpends.stream().map(UserSongCreditUsageDto::syncId).toList(),
        mirroredToBar().stream().map(UserSongCreditUsageDto::syncId).toList());
    assertEquals(List.of(OTHER_BAR),
        pushedElsewhere.stream().map(UserSongCreditUsageDto::locationId).toList());

    // Two $7.00 purchases were made for the bar: its mobile revenue is $14.00, whatever was
    // played with them. The $7.00 bought for the other bar is not the bar's.
    assertMoney("14.00", openPeriod(barLedger).mobileTotal());

    // ── 7. A re-delivered push (live push + catch-up pull) is never counted twice ─────────
    for (LocalMobileTransactionDto pushed : purchasesMirroredToBar()) {
      barLedger.receiveMobileTransaction(pushed);
    }
    for (UserSongCreditUsageDto spend : barSpends) {
      barLedger.receiveMobileCreditUsage(spend);
    }
    assertMoney("14.00", openPeriod(barLedger).mobileTotal());

    // ── 8. The patron closes their account: the financial records stay ────────────────────
    masterUserService.deleteAccount(PATRON);

    assertThrows(InvalidCredentialsException.class,
        () -> masterUserService.login(new LoginRequest(PATRON, "secret123")));
    assertEquals(barSpends.stream().map(UserSongCreditUsageDto::syncId).toList(),
        spendsAt(BAR).stream().map(UserSongCreditUsageDto::syncId).toList(),
        "the bar's spend records on master must survive the patron leaving");
    assertMoney("14.00", openPeriod(barLedger).mobileTotal());

    // ── 9. The operator also takes $5 at the kiosk, then settles the split ────────────────
    barLedger.recordLocalCashCredit(5);
    barLedger.addSplit();

    JukeboxSplitPeriodDto settled = finalizedPeriods(barLedger).get(0);
    assertMoney("5.00", settled.cashTotal());
    assertMoney("0.00", settled.cardTotal());
    assertMoney("14.00", settled.mobileTotal());
    assertMoney("19.00", settled.totalEarned());
    assertEquals(Integer.valueOf(50), settled.splitPercentageToOwner());
    assertMoney("9.50", settled.amountDueOwner());
    assertMoney("9.50", settled.amountDueOperator());

    assertMoney("0.00", openPeriod(barLedger).mobileTotal());
  }

  @Test
  void theSameEmailRegisteringAgainAfterClosing_startsFromNothing() throws Exception {

    masterUserService.register(new RegisterRequest("Pat", "Ron", PATRON, "secret123"));
    masterUserService.addFunds(PATRON, new AddFundsRequest(BAR, "pkg-7", "nonce-1"));
    masterUserService.deleteAccount(PATRON);

    masterUserService.register(new RegisterRequest("Pat", "Ron", PATRON, "new-secret123"));

    assertEquals(0, balance(), "a closed account's unspent credits never carry over");
    assertThrows(InsufficientCreditsException.class, () -> play(BAR, 1, 1, false));
    masterUserService.login(new LoginRequest(PATRON, "new-secret123"));
  }

  @Test
  void anAdminWebUserPaysToPlayLikeAnyPatron() throws Exception {

    masterUserService.addAdminUser(new RegisterRequest("Ada", "Min", ADMIN, "secret123"));

    assertThrows(InsufficientCreditsException.class,
        () -> masterController.addSongToQueue(BAR, new AddSongToQueueRequest(null, ALBUM_ID, 1,
            1, false), admin(), new MockHttpServletRequest()));
    verify(masterSongQueue, never()).addSongToQueue(any(), any());

    masterUserService.addFunds(ADMIN, new AddFundsRequest(BAR, "pkg-7", "nonce-1"));
    masterController.addSongToQueue(BAR, new AddSongToQueueRequest(null, ALBUM_ID, 1, 1, false),
        admin(), new MockHttpServletRequest());

    assertEquals(12, masterUserService.getNumCredits(ADMIN, BAR));
    assertMoney("7.00", openPeriod(barLedger).mobileTotal()); // the purchase, in full
  }

  @Test
  void aDoubleTapWithCreditsForOnePlay_queuesExactlyOneSong() throws Exception {

    masterUserService.register(new RegisterRequest("Pat", "Ron", PATRON, "secret123"));
    masterUserService.addFunds(PATRON, new AddFundsRequest(BAR, "pkg-7", "nonce-1")); // 14 credits

    // The first add is still in flight at the slave when the second tap arrives.
    CountDownLatch firstAddInFlight = new CountDownLatch(1);
    AtomicInteger adds = new AtomicInteger();
    doAnswer(invocation -> {
      if (adds.incrementAndGet() == 1) {
        firstAddInFlight.countDown();
        Thread.sleep(300);
      }
      return queueOnSlave(invocation.getArgument(0), invocation.getArgument(1));
    }).when(masterSongQueue).addSongToQueue(any(), any());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      // A priority-3 play costs 8: 14 credits cover one, not two.
      Future<SongQueueEntryDto> firstTap = executor.submit(() -> play(BAR, 1, 3, true));
      assertTrue(firstAddInFlight.await(10, TimeUnit.SECONDS));
      Future<SongQueueEntryDto> secondTap = executor.submit(() -> play(BAR, 1, 3, true));

      firstTap.get(10, TimeUnit.SECONDS);
      ExecutionException secondFailure =
          assertThrows(ExecutionException.class, () -> secondTap.get(10, TimeUnit.SECONDS));
      assertInstanceOf(InsufficientCreditsException.class, secondFailure.getCause());
    } finally {
      executor.shutdownNow();
    }

    assertEquals(1, adds.get(), "the second tap must never reach the jukebox");
    assertEquals(6, balance());
    assertEquals(List.of(-8), spendsAt(BAR).stream().map(UserSongCreditUsageDto::amount).toList());
    assertMoney("7.00", openPeriod(barLedger).mobileTotal()); // the one purchase, in full
  }

  // ─────────────────────────────────────────────────────────────────────────
  // The slave itself, and a standalone jukebox
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void aPatronCannotPlayForFreeByQueueingOnTheBarsSlaveDirectly() throws Exception {

    // A slave never charges web users -- credits are master's -- so its own web page must not
    // queue a patron's song at all.
    UserRootEntity slaveUsers = new UserRootEntity();
    slaveUsers.addUser(new UserEntity(1, "Pat", "Ron", PATRON, "x", UserRole.ROLE_USER));
    slaveUsers.addUser(new UserEntity(2, "Ada", "Min", ADMIN, "x", UserRole.ROLE_ADMIN));
    UserRepository slaveUserRepository = mock(UserRepository.class);
    when(slaveUserRepository.loadAggregateRoot(anyString())).thenReturn(slaveUsers);
    SongLibraryService slaveSongLibrary = ownLocation(BAR);
    UserServiceImpl slaveUserService = new UserServiceImpl(slaveUserRepository,
        new PlainTextPasswordEncoder(), mock(JwtUtil.class), event -> {}, slaveSongLibrary,
        pricingService, true, paymentGateway);
    SongQueueService slaveSongQueue = mock(SongQueueService.class);
    when(slaveSongQueue.addSongToQueue(any(), any())).thenAnswer(invocation -> {
      SongQueueEntryDto entry = queueOnSlave(invocation.getArgument(0), invocation.getArgument(1));
      AddSongToQueueRequest request = invocation.getArgument(1);
      slaveUserService.handleSongAddedToQueueEvent(
          new SongAddedToQueueEvent(entry, request.priorityPlay()));
      return entry;
    });
    SongQueueController slaveController = new SongQueueController(slaveSongQueue,
        slaveUserService, slaveSongLibrary, mock(GeoFenceService.class));

    assertThrows(QueueAccessDeniedException.class, () -> slaveController.addSongToQueue(BAR,
        new AddSongToQueueRequest(null, ALBUM_ID, 1, 1, false), patron(),
        new MockHttpServletRequest()));
    assertThrows(QueueAccessDeniedException.class, () -> slaveController.moveSongUpInQueue(BAR,
        new ChangeSongQueueRequest(ALBUM_ID, 1), patron(), new MockHttpServletRequest()));
    verify(slaveSongQueue, never()).addSongToQueue(any(), any());
    verify(slaveSongQueue, never()).moveSongUpInQueue(any(), any());

    // The operator, as an admin, can still manage the jukebox from its own web page.
    slaveController.addSongToQueue(BAR, new AddSongToQueueRequest(null, ALBUM_ID, 1, 1, false),
        admin(), new MockHttpServletRequest());
    assertEquals(1, queuedAt(BAR).size());
    assertTrue(slaveUsers.getUserByEmailAddressNullIfNotExists(ADMIN).getLocationAccounts()
        .isEmpty(), "nothing is charged or recorded on a slave");
  }

  @Test
  void aStandaloneJukebox_chargesEachPlayOnItsOwnQueueOnce_butSpendingIsNotRevenue()
      throws Exception {

    // A patron already holding 10 credits here (a standalone instance has no Add Funds of its
    // own).
    UserRootEntity users = new UserRootEntity();
    UserEntity holder = new UserEntity(1, "Pat", "Ron", PATRON, "x", UserRole.ROLE_USER);
    holder.getOrCreateLocationAccount(BAR).setNumCredits(10);
    users.addUser(holder);
    UserRepository userRepository = mock(UserRepository.class);
    when(userRepository.loadAggregateRoot(anyString())).thenReturn(users);
    SongLibraryService songLibrary = ownLocation(BAR);
    UserServiceImpl userService = new UserServiceImpl(userRepository,
        new PlainTextPasswordEncoder(), mock(JwtUtil.class), event -> {}, songLibrary,
        pricingService, false, paymentGateway);

    // The jukebox's own queue: an add publishes SongAddedToQueueEvent, which UserServiceImpl
    // listens for and charges from.
    SongQueueService songQueue = mock(SongQueueService.class);
    when(songQueue.addSongToQueue(any(), any())).thenAnswer(invocation -> {
      SongQueueEntryDto entry = queueOnSlave(invocation.getArgument(0), invocation.getArgument(1));
      AddSongToQueueRequest request = invocation.getArgument(1);
      userService.handleSongAddedToQueueEvent(
          new SongAddedToQueueEvent(entry, request.priorityPlay()));
      return entry;
    });
    SongQueueController controller = new SongQueueController(songQueue, userService, songLibrary,
        mock(GeoFenceService.class));
    FinancialLedgerServiceImpl ledger = newLedger(BAR, userService, false);

    for (int songId = 1; songId <= 3; songId++) {
      controller.addSongToQueue(BAR, new AddSongToQueueRequest(null, ALBUM_ID, songId, 1, false),
          patron(), new MockHttpServletRequest());
    }

    UserLocationAccountEntity account =
        users.getUserByEmailAddressNullIfNotExists(PATRON).getLocationAccountNullIfNotExists(BAR);
    assertEquals(4, account.getNumCredits(), "3 plays at 2 credits each, each charged once");
    assertEquals(3, account.getSongCreditUsages().size());
    assertTrue(account.getSongCreditUsages().stream().allMatch(u -> BAR.equals(u.getLocationId())),
        "a play on the jukebox's own queue is recorded against that location");
    assertMoney("0.00", openPeriod(ledger).mobileTotal(),
        "mobile revenue is purchases, never spends -- and nothing was bought here");
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private SongQueueEntryDto play(Integer locationId, int songId, int priority,
      boolean priorityPlay) {
    // The body's username is ignored -- the server queues as the authenticated patron.
    return masterController.addSongToQueue(locationId,
        new AddSongToQueueRequest("someone-else", ALBUM_ID, songId, priority, priorityPlay),
        patron(), new MockHttpServletRequest());
  }

  private Integer moveUp(Integer locationId, int songId) {
    return masterController.moveSongUpInQueue(locationId,
        new ChangeSongQueueRequest(ALBUM_ID, songId), patron(), new MockHttpServletRequest());
  }

  /** The patron's balance at {@link #BAR}. */
  private int balance() {
    return balanceAt(BAR);
  }

  private int balanceAt(Integer locationId) {
    return masterUserService.getNumCredits(PATRON, locationId);
  }

  private List<LocalMobileTransactionDto> purchasesMirroredToBar() {
    return eventsOfType(MobilePurchaseRecordedEvent.class).stream()
        .map(MobilePurchaseRecordedEvent::transaction)
        .filter(transaction -> BAR.equals(transaction.locationId()))
        .toList();
  }

  private List<UserSongCreditUsageDto> spendsAt(Integer locationId) {
    return masterUserService.getCreditLedgerForLocation(locationId,
        Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
  }

  private List<UserSongCreditUsageDto> mirroredToBar() {
    return eventsOfType(LocationSongCreditUsageRecordedEvent.class).stream()
        .map(LocationSongCreditUsageRecordedEvent::usage)
        .filter(usage -> BAR.equals(usage.locationId()))
        .toList();
  }

  private <T> List<T> eventsOfType(Class<T> type) {
    return masterEvents.stream().filter(type::isInstance).map(type::cast).toList();
  }

  /** The user as master last stored them. */
  private UserEntity storedUser(String emailAddress) {
    ArgumentCaptor<UserRootEntity> root = ArgumentCaptor.forClass(UserRootEntity.class);
    verify(masterUserRepository, atLeastOnce()).storeAggregateRoot(root.capture());
    return root.getValue().getUserByEmailAddressNullIfNotExists(emailAddress);
  }

  /**
   * Master's event bus. A purchase or a spend is pushed to that location's slave, as {@code
   * MobileTransactionSlaveNotifier} and {@code MobileCreditUsageSlaveNotifier} do.
   */
  private void publishOnMaster(Object event) {
    masterEvents.add(event);
    if (event instanceof MobilePurchaseRecordedEvent recorded) {
      if (BAR.equals(recorded.transaction().locationId())) {
        barLedger.receiveMobileTransaction(recorded.transaction());
      } else {
        purchasesPushedElsewhere.add(recorded.transaction());
      }
    }
    if (event instanceof LocationSongCreditUsageRecordedEvent recorded) {
      if (BAR.equals(recorded.usage().locationId())) {
        barLedger.receiveMobileCreditUsage(recorded.usage());
      } else {
        pushedElsewhere.add(recorded.usage());
      }
    }
  }

  private SongQueueEntryDto queueOnSlave(Integer locationId, AddSongToQueueRequest request) {
    SongDto song = new SongDto(null, null, null, "Artist", request.albumId(), "Album", null,
        request.songId(), "Song " + request.songId(), 1, 0);
    SongQueueEntryDto entry =
        new SongQueueEntryDto(request.username(), song, request.priority(), null);
    slaveQueues.computeIfAbsent(locationId, id -> new CopyOnWriteArrayList<>()).add(entry);
    return entry;
  }

  private List<SongQueueEntryDto> queuedAt(Integer locationId) {
    return slaveQueues.getOrDefault(locationId, List.of());
  }

  private FinancialLedgerServiceImpl newLedger(Integer locationId, UserService userService,
      boolean slaveMode) throws Exception {

    FinancialLedgerRepository repository = mock(FinancialLedgerRepository.class);
    when(repository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger yet"));
    AtomicInteger nextId = new AtomicInteger(1);
    when(repository.nextPersistentIdentity())
        .thenAnswer(invocation -> Integer.valueOf(nextId.getAndIncrement()));

    // Default jukebox split: 50% to the bar owner.
    return new FinancialLedgerServiceImpl(repository, new FinancialLedgerProperties(),
        userService, pricingService, ownLocation(locationId),
        mock(ApplicationEventPublisher.class), mock(LocationService.class), slaveMode);
  }

  /** Master's ledger: no own location, so no split period -- it records purchases only. */
  private FinancialLedgerServiceImpl newMasterLedger(UserService userService) throws Exception {

    FinancialLedgerRepository repository = mock(FinancialLedgerRepository.class);
    when(repository.loadAggregateRoot(anyString()))
        .thenThrow(new EntityDoesNotExistException("no ledger yet"));
    AtomicInteger nextId = new AtomicInteger(1000);
    when(repository.nextPersistentIdentity())
        .thenAnswer(invocation -> Integer.valueOf(nextId.getAndIncrement()));
    SongLibraryService noOwnLocation = mock(SongLibraryService.class);
    when(noOwnLocation.getOwnLocationId()).thenReturn(null);
    when(noOwnLocation.getOwnLocation()).thenReturn(null);

    return new FinancialLedgerServiceImpl(repository, new FinancialLedgerProperties(),
        userService, pricingService, noOwnLocation, this::publishOnMaster,
        mock(LocationService.class), false);
  }

  private static SongLibraryService ownLocation(Integer locationId) {
    SongLibraryService songLibrary = mock(SongLibraryService.class);
    when(songLibrary.getOwnLocationId()).thenReturn(locationId);
    when(songLibrary.getOwnLocation())
        .thenReturn(new LocationEntity(locationId, "The Bar", null, null, "api-key-hash"));
    return songLibrary;
  }

  private static JukeboxSplitPeriodDto openPeriod(FinancialLedgerServiceImpl ledger) {
    List<JukeboxSplitPeriodDto> open =
        ledger.getAllPeriods().stream().filter(p -> p.endDate() == null).toList();
    assertEquals(1, open.size());
    return open.get(0);
  }

  private static List<JukeboxSplitPeriodDto> finalizedPeriods(FinancialLedgerServiceImpl ledger) {
    return ledger.getAllPeriods().stream().filter(p -> p.endDate() != null).toList();
  }

  private static void assertMoney(String expected, BigDecimal actual) {
    assertMoney(expected, actual, "");
  }

  private static void assertMoney(String expected, BigDecimal actual, String message) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual),
        message + " -- expected $" + expected + " but was $" + actual);
  }

  private static Authentication patron() {
    return new UsernamePasswordAuthenticationToken(PATRON, null,
        List.of(new SimpleGrantedAuthority(UserRole.ROLE_USER.name())));
  }

  private static Authentication admin() {
    return new UsernamePasswordAuthenticationToken(ADMIN, null,
        List.of(new SimpleGrantedAuthority(UserRole.ROLE_ADMIN.name())));
  }

  /** Real password checks without bcrypt's deliberate slowness. */
  private static final class PlainTextPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
      return "plain:" + rawPassword;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
      return ("plain:" + rawPassword).equals(encodedPassword);
    }
  }
}
