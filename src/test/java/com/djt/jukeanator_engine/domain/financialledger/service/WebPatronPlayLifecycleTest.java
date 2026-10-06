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
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;
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
 * slave (the bar's jukebox and its own financial ledger). Master's spends reach the slave the way
 * {@code MobileCreditUsageSlaveNotifier} pushes them -- synchronously here -- into the slave's
 * {@link FinancialLedgerServiceImpl}, whose split periods are what the bar owner is paid from.
 *
 * <p>The rules this locks in:
 * <ul>
 * <li>A new account holds no credits; every credit spent was bought through Add Funds.</li>
 * <li>An operation the balance does not cover is refused before it runs -- nothing is queued,
 * moved or charged -- so no play is ever free.</li>
 * <li>Every spend is recorded against the location it was made at, for exactly the credits the
 * balance lost, and mirrored only to that location.</li>
 * <li>A location's mobile revenue is the credits spent there divided by that location's own
 * {@code creditsPerDollar} -- the kiosk's bill-acceptor rate -- however much the patron paid for
 * them. This is the agreed basis for paying the bar owner.</li>
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
   * kiosk's bill-acceptor rate), webCostMultiplier 2. A normal play costs 2 credits, a priority-2
   * play 2 * 2 * 2 = 8, and moving a priority-1 song max(1, 1 * 3) * 2 = 6.
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

  /** Spends master pushed toward any location other than {@link #BAR}. */
  private final List<UserSongCreditUsageDto> pushedElsewhere = new CopyOnWriteArrayList<>();
  private final List<Object> masterEvents = new CopyOnWriteArrayList<>();
  private final Map<Integer, List<SongQueueEntryDto>> slaveQueues = new ConcurrentHashMap<>();
  private final AtomicInteger nextChargeId = new AtomicInteger(1);

  @BeforeEach
  void setUp() throws Exception {

    pricingService = mock(PricingService.class);
    when(pricingService.resolvePricingConfig(any())).thenReturn(BAR_PRICING);
    when(pricingService.resolvePricingConfig(OTHER_BAR)).thenReturn(OTHER_BAR_PRICING);

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

    // ── 2. Add Funds: $7.00 buys 12 + 1 bonus = 13 credits, at no location ────────────────
    AddFundsResponseDto purchase =
        masterUserService.addFunds(PATRON, new AddFundsRequest("pkg-7", "nonce-1"));

    verify(paymentGateway).charge(new BigDecimal("7.00"), "nonce-1");
    assertEquals(Integer.valueOf(13), purchase.numCredits());
    assertEquals(12, purchase.creditsAdded());
    assertEquals(1, purchase.bonusCreditsAdded());
    assertEquals("txn-1", purchase.transactionId());
    assertEquals(0, new BigDecimal("4.33").compareTo(purchase.balanceUsd()),
        "13 credits at the kiosk rate of 3 per dollar");
    assertEquals(1, eventsOfType(PurchaseCompletedEvent.class).size());
    assertEquals(0, new BigDecimal("0.00").compareTo(openPeriod(barLedger).mobileTotal()),
        "buying credits is not revenue for any location until they are spent there");

    // ── 3. Playing at the bar, until the balance runs out ─────────────────────────────────
    play(BAR, 1, 1, false); // normal play: 2 credits
    assertEquals(11, balance());
    play(BAR, 2, 2, true); // priority-2 play: 8 credits
    assertEquals(3, balance());

    // Moving song 1 up costs 6; only 3 are held -- refused, and the queue is untouched.
    assertThrows(InsufficientCreditsException.class, () -> moveUp(BAR, 1));
    verify(masterSongQueue, never()).moveSongUpInQueue(any(), any());
    assertEquals(3, balance());

    play(BAR, 3, 1, false);
    assertEquals(1, balance());
    assertThrows(InsufficientCreditsException.class, () -> play(BAR, 4, 1, false));
    assertEquals(1, balance(), "a refused play is never charged");

    assertEquals(3, queuedAt(BAR).size(), "only the three paid plays reached the jukebox");
    assertTrue(queuedAt(BAR).stream().allMatch(entry -> PATRON.equals(entry.username())));

    // ── 4. Topping up, and playing at a second bar ────────────────────────────────────────
    masterUserService.addFunds(PATRON, new AddFundsRequest("pkg-7", "nonce-2"));
    assertEquals(14, balance());
    moveUp(BAR, 1); // 6 credits
    assertEquals(8, balance());
    play(OTHER_BAR, 9, 1, false); // 1 credit at the other bar's pricing
    assertEquals(7, balance());
    verify(paymentGateway, times(2)).charge(any(BigDecimal.class), anyString());

    // ── 5. Master's record accounts for every credit bought ───────────────────────────────
    List<UserSongCreditUsageDto> barSpends = spendsAt(BAR);
    assertEquals(List.of(-2, -8, -2, -6), barSpends.stream().map(UserSongCreditUsageDto::amount).toList());
    assertEquals(List.of(UserSongCreditUsageType.QUEUE_ADD, UserSongCreditUsageType.QUEUE_ADD,
        UserSongCreditUsageType.QUEUE_ADD, UserSongCreditUsageType.QUEUE_ACTION),
        barSpends.stream().map(UserSongCreditUsageDto::type).toList());
    assertEquals(List.of(11, 3, 1, 8),
        barSpends.stream().map(UserSongCreditUsageDto::resultingBalance).toList());
    assertEquals(List.of(1, 2, 3), barSpends.stream().limit(3)
        .map(UserSongCreditUsageDto::songId).toList());
    assertTrue(barSpends.stream().allMatch(spend -> PATRON.equals(spend.userEmail())));

    List<UserSongCreditUsageDto> otherBarSpends = spendsAt(OTHER_BAR);
    assertEquals(List.of(-1), otherBarSpends.stream().map(UserSongCreditUsageDto::amount).toList());

    UserEntity patron = storedUser(PATRON);
    List<UserAddFundsTransactionEntity> purchases =
        new ArrayList<>(patron.getUserAddFundsTransactions());
    assertEquals(2, purchases.size());
    assertTrue(purchases.stream().allMatch(
        p -> new BigDecimal("7.00").compareTo(p.getAmountUsd()) == 0));
    assertEquals(List.of("txn-1", "txn-2"), purchases.stream()
        .map(UserAddFundsTransactionEntity::getPaymentTransactionId).sorted().toList());

    int creditsBought = purchases.stream()
        .mapToInt(p -> p.getCreditsAwarded() + p.getBonusCredits()).sum();
    int creditsSpent = -patron.getUserSongCreditUsages().stream().mapToInt(u -> u.getAmount()).sum();
    assertEquals(26, creditsBought);
    assertEquals(19, creditsSpent);
    assertEquals(creditsBought - creditsSpent, balance(),
        "the balance is exactly what was bought minus what was spent");

    // ── 6. The bar's own ledger holds exactly the bar's spends ────────────────────────────
    assertEquals(barSpends.stream().map(UserSongCreditUsageDto::syncId).toList(),
        mirroredToBar().stream().map(UserSongCreditUsageDto::syncId).toList());
    assertEquals(List.of(OTHER_BAR),
        pushedElsewhere.stream().map(UserSongCreditUsageDto::locationId).toList());

    // 18 credits spent at the bar, at the bar's kiosk rate of 3 credits per dollar = $6.00 --
    // even though the patron paid $7.00 per 13 credits. The bar owner is paid the kiosk-equivalent
    // value of what was played there.
    assertMoney("6.00", openPeriod(barLedger).mobileTotal());

    // ── 7. A re-delivered push (live push + catch-up pull) is never counted twice ─────────
    for (UserSongCreditUsageDto spend : barSpends) {
      barLedger.receiveMobileCreditUsage(spend);
    }
    assertMoney("6.00", openPeriod(barLedger).mobileTotal());

    // ── 8. The patron closes their account: the financial records stay ────────────────────
    masterUserService.deleteAccount(PATRON);

    assertThrows(InvalidCredentialsException.class,
        () -> masterUserService.login(new LoginRequest(PATRON, "secret123")));
    assertEquals(barSpends.stream().map(UserSongCreditUsageDto::syncId).toList(),
        spendsAt(BAR).stream().map(UserSongCreditUsageDto::syncId).toList(),
        "the bar's revenue on master must survive the patron leaving");
    assertMoney("6.00", openPeriod(barLedger).mobileTotal());

    // ── 9. The operator also takes $5 at the kiosk, then settles the split ────────────────
    barLedger.recordLocalCashCredit(5);
    barLedger.addSplit();

    JukeboxSplitPeriodDto settled = finalizedPeriods(barLedger).get(0);
    assertMoney("5.00", settled.cashTotal());
    assertMoney("0.00", settled.cardTotal());
    assertMoney("6.00", settled.mobileTotal());
    assertMoney("11.00", settled.totalEarned());
    assertEquals(Integer.valueOf(50), settled.splitPercentageToOwner());
    assertMoney("5.50", settled.amountDueOwner());
    assertMoney("5.50", settled.amountDueOperator());

    assertMoney("0.00", openPeriod(barLedger).mobileTotal());
  }

  @Test
  void theSameEmailRegisteringAgainAfterClosing_startsFromNothing() throws Exception {

    masterUserService.register(new RegisterRequest("Pat", "Ron", PATRON, "secret123"));
    masterUserService.addFunds(PATRON, new AddFundsRequest("pkg-7", "nonce-1"));
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

    masterUserService.addFunds(ADMIN, new AddFundsRequest("pkg-7", "nonce-1"));
    masterController.addSongToQueue(BAR, new AddSongToQueueRequest(null, ALBUM_ID, 1, 1, false),
        admin(), new MockHttpServletRequest());

    assertEquals(Integer.valueOf(11), masterUserService.getProfile(ADMIN).numCredits());
    assertMoney("0.67", openPeriod(barLedger).mobileTotal()); // 2 credits / 3 per dollar
  }

  @Test
  void aDoubleTapWithCreditsForOnePlay_queuesExactlyOneSong() throws Exception {

    masterUserService.register(new RegisterRequest("Pat", "Ron", PATRON, "secret123"));
    masterUserService.addFunds(PATRON, new AddFundsRequest("pkg-7", "nonce-1")); // 13 credits

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
      // A priority-2 play costs 8: 13 credits cover one, not two.
      Future<SongQueueEntryDto> firstTap = executor.submit(() -> play(BAR, 1, 2, true));
      assertTrue(firstAddInFlight.await(10, TimeUnit.SECONDS));
      Future<SongQueueEntryDto> secondTap = executor.submit(() -> play(BAR, 1, 2, true));

      firstTap.get(10, TimeUnit.SECONDS);
      ExecutionException secondFailure =
          assertThrows(ExecutionException.class, () -> secondTap.get(10, TimeUnit.SECONDS));
      assertInstanceOf(InsufficientCreditsException.class, secondFailure.getCause());
    } finally {
      executor.shutdownNow();
    }

    assertEquals(1, adds.get(), "the second tap must never reach the jukebox");
    assertEquals(5, balance());
    assertEquals(List.of(-8), spendsAt(BAR).stream().map(UserSongCreditUsageDto::amount).toList());
    assertMoney("2.67", openPeriod(barLedger).mobileTotal()); // 8 credits / 3 per dollar
  }

  // ─────────────────────────────────────────────────────────────────────────
  // The slave itself, and a standalone jukebox
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void aPatronCannotPlayForFreeByQueueingOnTheBarsSlaveDirectly() throws Exception {

    // A slave never charges web users -- credits are master's -- so its own web page must not
    // queue a patron's song at all.
    UserRootEntity slaveUsers = new UserRootEntity();
    slaveUsers.addUser(new UserEntity(1, "Pat", "Ron", PATRON, "x", 0, UserRole.ROLE_USER));
    slaveUsers.addUser(new UserEntity(2, "Ada", "Min", ADMIN, "x", 0, UserRole.ROLE_ADMIN));
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
    assertTrue(slaveUsers.getUserByEmailAddressNullIfNotExists(ADMIN).getUserSongCreditUsages()
        .isEmpty(), "nothing is charged or recorded on a slave");
  }

  @Test
  void aStandaloneJukebox_countsEachPlayOnItsOwnQueueOnce_inItsOwnMobileTotal() throws Exception {

    // A patron already holding 10 credits (a standalone instance has no Add Funds of its own).
    UserRootEntity users = new UserRootEntity();
    users.addUser(new UserEntity(1, "Pat", "Ron", PATRON, "x", 10, UserRole.ROLE_USER));
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

    UserEntity patron = users.getUserByEmailAddressNullIfNotExists(PATRON);
    assertEquals(4, patron.getNumCredits(), "3 plays at 2 credits each, each charged once");
    assertTrue(patron.getUserSongCreditUsages().stream().allMatch(u -> BAR.equals(u.getLocationId())),
        "a play on the jukebox's own queue is that location's revenue");
    assertMoney("2.00", openPeriod(ledger).mobileTotal()); // 6 credits / 3 per dollar
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

  private int balance() {
    return masterUserService.getProfile(PATRON).numCredits();
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
   * Master's event bus. A location-attributed spend is pushed to that location's slave, as
   * {@code MobileCreditUsageSlaveNotifier} does.
   */
  private void publishOnMaster(Object event) {
    masterEvents.add(event);
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
    assertEquals(0, new BigDecimal(expected).compareTo(actual),
        "expected $" + expected + " but was $" + actual);
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
