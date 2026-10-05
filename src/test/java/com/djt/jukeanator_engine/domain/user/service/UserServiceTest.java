package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.InvalidPrincipalException;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsResponseDto;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.PlaylistSummaryDto;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserHomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.event.LocationSongCreditUsageRecordedEvent;
import com.djt.jukeanator_engine.domain.user.event.PurchaseCompletedEvent;
import com.djt.jukeanator_engine.domain.user.event.UserCreditsChangedEvent;
import com.djt.jukeanator_engine.domain.user.exception.InsufficientCreditsException;
import com.djt.jukeanator_engine.domain.user.exception.InvalidCredentialsException;
import com.djt.jukeanator_engine.domain.user.exception.PaymentException;
import com.djt.jukeanator_engine.domain.user.exception.QueueAccessDeniedException;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageEntity;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserAddFundsTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;

/**
 * Covers every method declared on {@link UserService}. Most cases run against
 * {@link #userServiceImpl}, a locally constructed instance with fully mocked dependencies (fast,
 * deterministic, no Spring context / real password hashing / real JWTs needed). The auth-flow
 * happy path ({@code register}/{@code login}/{@code getProfile}) is instead exercised end-to-end
 * against the Spring-managed {@link #userService} bean in {@link #lifecycle()}, since that path
 * benefits from real password encoding and JWT generation.
 *
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml
public class UserServiceTest extends AbstractServiceIntegrationTest {

  private static final String REGISTERED_EMAIL = "jane.doe@example.com";
  private static final String REGISTERED_PASSWORD_HASH = "hashed-password";

  @Autowired
  private UserService userService;

  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private JwtUtil jwtUtil;
  private ApplicationEventPublisher eventPublisher;
  private SongLibraryService songLibraryService;
  private PricingService pricingService;
  private UserRootEntity userRoot;
  private UserServiceImpl userServiceImpl;
  private PaymentGateway paymentGateway;

  @BeforeEach
  void setUp() throws EntityDoesNotExistException {

    userRepository = mock(UserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    jwtUtil = mock(JwtUtil.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    songLibraryService = mock(SongLibraryService.class);
    pricingService = mock(PricingService.class);
    // Matches application-test.yml's user-interface credit-config block.
    when(pricingService.resolvePricingConfig(any()))
        .thenReturn(new PricingConfig(2, 3, 3, 10, 2, false));

    paymentGateway = mock(PaymentGateway.class);

    userRoot = new UserRootEntity();
    userRoot.addUser(new UserEntity(Integer.valueOf(1), "Jane", "Doe", REGISTERED_EMAIL,
        REGISTERED_PASSWORD_HASH, Integer.valueOf(6), UserRole.ROLE_USER));

    when(userRepository.loadAggregateRoot(anyString())).thenReturn(userRoot);

    userServiceImpl = new UserServiceImpl(userRepository, passwordEncoder, jwtUtil, eventPublisher,
        songLibraryService, pricingService, false, paymentGateway);
  }

  private UserEntity registeredUser() {
    return userRoot.getUserByEmailAddressNullIfNotExists(REGISTERED_EMAIL);
  }

  /** Minimal {@link SongFileEntity} whose {@code getAlbum().getId()} and own
   *  {@code getId()} resolve to the given ids, matching what
   *  {@link UserEntity#addSongToPlaylist} needs to build a {@link SongIdentifier}. */
  private static SongFileEntity buildSong(int albumId, int songId) {
    AlbumFolderEntity album = new AlbumFolderEntity(null, "Album" + albumId);
    album.setId(albumId);
    SongFileEntity song = new SongFileEntity(album, "Song" + songId + ".mp3");
    song.setId(songId);
    return song;
  }

  private static SongDto buildSongDto(int albumId, int songId) {
    return new SongDto(null, null, null, "Artist", albumId, "Album", null, songId, "Song", 1, 0);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // AUTH — register / addAdminUser / login / getProfile
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void shouldInitializeService() {
    assertNotNull(userService, "userService should be injected");
  }

  @Test
  void lifecycle() throws Exception {

    // Use a unique email address per run so the test is idempotent across repeated
    // executions against a persistent (non-rolled-back) datastore.
    String emailAddress = "jane.doe+" + java.util.UUID.randomUUID() + "@example.com";

    // Register a new user
    RegisterRequest registerRequest =
        new RegisterRequest("Jane", "Doe", emailAddress, "password123");
    AuthResponse registerResponse = userService.register(registerRequest);
    assertNotNull(registerResponse, "registerResponse should not be null");
    assertNotNull(registerResponse.token(), "token should not be null");
    assertEquals(emailAddress, registerResponse.emailAddress());
    assertEquals("ROLE_USER", registerResponse.role());

    // Registering the same email address again should fail
    assertThrows(UserServiceException.class, () -> userService.register(registerRequest));

    // Log in with the registered user's credentials
    LoginRequest loginRequest = new LoginRequest(emailAddress, "password123");
    AuthResponse loginResponse = userService.login(loginRequest);
    assertNotNull(loginResponse, "loginResponse should not be null");
    assertNotNull(loginResponse.token(), "token should not be null");
    assertEquals(emailAddress, loginResponse.emailAddress());

    // Logging in with an incorrect password should fail
    LoginRequest badLoginRequest = new LoginRequest(emailAddress, "wrongPassword");
    assertThrows(InvalidCredentialsException.class, () -> userService.login(badLoginRequest));

    // Logging in with an unknown email address should fail
    LoginRequest unknownLoginRequest = new LoginRequest("unknown@example.com", "password123");
    assertThrows(InvalidCredentialsException.class, () -> userService.login(unknownLoginRequest));

    // Get the profile for the registered user
    UserProfileDto profile = userService.getProfile(emailAddress);
    assertNotNull(profile, "profile should not be null");
    assertEquals("Jane", profile.firstName());
    assertEquals("Doe", profile.lastName());
    assertEquals(emailAddress, profile.emailAddress());

    // Getting a profile for an unknown email address should fail
    assertThrows(InvalidPrincipalException.class, () -> userService.getProfile("unknown@example.com"));
  }

  @Test
  void addAdminUser_createsUserWithAdminRoleAndPersists() {

    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("admin-token");

    RegisterRequest request = new RegisterRequest("Ada", "Min", "ada.min@example.com", "password123");

    AuthResponse response = userServiceImpl.addAdminUser(request);

    assertNotNull(response);
    assertEquals("admin-token", response.token());
    assertEquals("ada.min@example.com", response.emailAddress());
    assertEquals(UserRole.ROLE_ADMIN.name(), response.role());

    UserEntity created = userRoot.getUserByEmailAddressNullIfNotExists("ada.min@example.com");
    assertNotNull(created, "admin user should be added to the user root");
    assertEquals(UserRole.ROLE_ADMIN, created.getRole());

    verify(userRepository).storeAggregateRoot(userRoot);
    verify(jwtUtil).generateToken("ada.min@example.com", UserRole.ROLE_ADMIN.name());
  }

  @Test
  void addAdminUser_throwsWhenEmailAlreadyRegistered() {

    RegisterRequest request = new RegisterRequest("Ada", "Min", REGISTERED_EMAIL, "password123");

    assertThrows(UserServiceException.class, () -> userServiceImpl.addAdminUser(request));
  }

  @Test
  void addAdminUser_doesNotDowngradeRegularRegisterToAdmin() {

    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("user-token");

    RegisterRequest request = new RegisterRequest("Reg", "User", "reg.user@example.com", "password123");

    userServiceImpl.register(request);

    UserEntity created = userRoot.getUserByEmailAddressNullIfNotExists("reg.user@example.com");
    assertEquals(UserRole.ROLE_USER, created.getRole());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ACCOUNT MANAGEMENT — changePassword / deleteAccount / addFunds / updateProfile
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void changePassword_updatesHashWhenCurrentPasswordMatchesAndPersists() {

    when(passwordEncoder.matches("oldPass", REGISTERED_PASSWORD_HASH)).thenReturn(true);
    when(passwordEncoder.encode("newPass")).thenReturn("new-hashed-password");

    userServiceImpl.changePassword(REGISTERED_EMAIL, new ChangePasswordRequest("oldPass", "newPass"));

    assertEquals("new-hashed-password", registeredUser().getPasswordHash());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void changePassword_throwsWhenCurrentPasswordIncorrect() {

    when(passwordEncoder.matches("wrongPass", REGISTERED_PASSWORD_HASH)).thenReturn(false);

    assertThrows(UserServiceException.class, () -> userServiceImpl.changePassword(REGISTERED_EMAIL,
        new ChangePasswordRequest("wrongPass", "newPass")));
  }

  @Test
  void changePassword_throwsForUnknownEmailAddress() {

    assertThrows(InvalidPrincipalException.class, () -> userServiceImpl
        .changePassword("unknown@example.com", new ChangePasswordRequest("old", "new")));
  }

  @Test
  void deleteAccountRemovesRegisteredUserAndPersistsRoot() {

    userServiceImpl.deleteAccount(REGISTERED_EMAIL);

    assertNull(userRoot.getUserByEmailAddressNullIfNotExists(REGISTERED_EMAIL),
        "user should no longer be present in the user root");
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void deleteAccount_closesTheAccountButRetainsItsFinancialRecords() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));
    userServiceImpl.addFunds(REGISTERED_EMAIL, new AddFundsRequest("pkg-7", "nonce"));
    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, Integer.valueOf(42));
    userServiceImpl.addSearchHistory(REGISTERED_EMAIL, "Billy Idol");
    UserEntity user = registeredUser();
    Integer userId = user.getPersistentIdentity();

    userServiceImpl.deleteAccount(REGISTERED_EMAIL);

    assertNull(userRoot.getUserByEmailAddressNullIfNotExists(REGISTERED_EMAIL));
    assertEquals(1, userRoot.getUsers().size(), "the account is closed, not removed");
    UserEntity closed = userRoot.getUsers().iterator().next();
    assertEquals(userId, closed.getPersistentIdentity(), "same row, so its records stay linked");
    assertTrue(closed.isClosed());
    assertTrue(closed.getEmailAddress().endsWith(UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN));
    assertEquals("Closed", closed.getFirstName());
    assertEquals("Account", closed.getLastName());
    assertTrue(closed.getSearchHistory().isEmpty());
    assertTrue(closed.getPlaylists().isEmpty());

    // The financial records: the Braintree charge stays reconcilable, and location 42's mobile
    // revenue for the spend is unchanged.
    assertEquals("txn-123",
        closed.getUserAddFundsTransactions().iterator().next().getPaymentTransactionId());
    List<UserSongCreditUsageDto> ledger = userServiceImpl.getCreditLedgerForLocation(
        Integer.valueOf(42), Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
    assertEquals(1, ledger.size());
    assertEquals(-6, ledger.get(0).amount());
  }

  @Test
  void deleteAccount_theClosedAccountCanNeverLogInAgain_andTheEmailCanRegisterAfresh() {

    when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");

    userServiceImpl.deleteAccount(REGISTERED_EMAIL);
    String closedEmail = userRoot.getUsers().iterator().next().getEmailAddress();

    assertThrows(InvalidCredentialsException.class,
        () -> userServiceImpl.login(new LoginRequest(REGISTERED_EMAIL, "password")));
    assertThrows(InvalidCredentialsException.class,
        () -> userServiceImpl.login(new LoginRequest(closedEmail, "password")));
    assertThrows(InvalidPrincipalException.class, () -> userServiceImpl.getProfile(REGISTERED_EMAIL));

    userServiceImpl.register(new RegisterRequest("Jane", "Doe", REGISTERED_EMAIL, "password"));

    UserEntity fresh = registeredUser();
    assertEquals(0, fresh.getNumCredits(), "a new account never inherits a closed one's credits");
    assertTrue(fresh.getUserSongCreditUsages().isEmpty());
    assertTrue(fresh.getUserAddFundsTransactions().isEmpty());
    assertEquals(2, userRoot.getUsers().size());
  }

  @Test
  void register_newAccountStartsWithNoCredits() {

    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");

    userServiceImpl.register(new RegisterRequest("New", "Patron", "new@example.com", "password"));

    // Every credit a web user spends is paid out to the location, so none may be free.
    assertEquals(0, userRoot.getUserByEmailAddressNullIfNotExists("new@example.com").getNumCredits());
    assertEquals(0, userServiceImpl.getAffordableQueueAddCount("new@example.com", 42, 1, false));
    assertThrows(InsufficientCreditsException.class,
        () -> userServiceImpl.requireAffordableQueueAdd("new@example.com", 42, 1, false));
  }

  @Test
  void register_rejectsTheClosedAccountEmailDomain() {

    assertThrows(UserServiceException.class, () -> userServiceImpl.register(new RegisterRequest(
        "X", "Y", "someone" + UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN, "password")));
  }

  @Test
  void deleteAccountIsIdempotentlyRejectedOnSecondCall() {

    userServiceImpl.deleteAccount(REGISTERED_EMAIL);

    assertThrows(InvalidPrincipalException.class,
        () -> userServiceImpl.deleteAccount(REGISTERED_EMAIL));
  }

  @Test
  void deleteAccountThrowsForUnknownEmailAddress() {

    assertThrows(InvalidPrincipalException.class,
        () -> userServiceImpl.deleteAccount("unknown@example.com"));

    verify(userRepository, never()).storeAggregateRoot(userRoot);
  }

  @Test
  void addFunds_throwsForUnknownPackage() {

    assertThrows(PaymentException.class, () -> userServiceImpl.addFunds(REGISTERED_EMAIL,
        new AddFundsRequest("nonexistent-pkg", "fake-nonce")));

    verify(userRepository, never()).storeAggregateRoot(userRoot);
  }

  @Test
  void addFunds_throwsForMissingNonce() {

    assertThrows(PaymentException.class,
        () -> userServiceImpl.addFunds(REGISTERED_EMAIL, new AddFundsRequest("pkg-7", null)));

    verify(userRepository, never()).storeAggregateRoot(userRoot);
  }

  @Test
  void addFunds_throwsAndGrantsNoCreditsWhenDeclined() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.failure("Payment declined: Do Not Honor"));

    int before = registeredUser().getNumCredits();

    assertThrows(PaymentException.class, () -> userServiceImpl.addFunds(REGISTERED_EMAIL,
        new AddFundsRequest("pkg-7", "fake-nonce")));

    assertEquals(before, registeredUser().getNumCredits());
    verify(userRepository, never()).storeAggregateRoot(userRoot);
    verify(eventPublisher, never()).publishEvent(any(UserCreditsChangedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(PurchaseCompletedEvent.class));
  }

  @Test
  void addFunds_grantsCreditsAndPublishesEventsOnSuccess() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));

    AddFundsResponseDto response =
        userServiceImpl.addFunds(REGISTERED_EMAIL, new AddFundsRequest("pkg-7", "fake-nonce"));

    // pkg-7: 12 credits + 1 bonus = 13, starting balance 6 -> 19
    assertEquals(19, registeredUser().getNumCredits());
    assertEquals(Integer.valueOf(19), response.numCredits());
    assertEquals(12, response.creditsAdded());
    assertEquals(1, response.bonusCreditsAdded());
    assertEquals("PayPal", response.paymentSource());
    assertEquals("txn-123", response.transactionId());
    assertEquals(0, new BigDecimal("6.33").compareTo(response.balanceUsd()));
    verify(userRepository).storeAggregateRoot(userRoot);
    verify(eventPublisher).publishEvent(any(UserCreditsChangedEvent.class));
    verify(eventPublisher).publishEvent(any(PurchaseCompletedEvent.class));
    verify(paymentGateway).charge(eq(new BigDecimal("7.00")), eq("fake-nonce"));
  }

  @Test
  void addFunds_recordsTheTransactionForBraintreeReconciliation_neverLocationAttributed() {

    when(songLibraryService.getOwnLocationId()).thenReturn(Integer.valueOf(42));
    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "Visa •••• 1111"));

    AddFundsResponseDto response =
        userServiceImpl.addFunds(REGISTERED_EMAIL, new AddFundsRequest("pkg-28", "fake-nonce"));

    assertEquals(1, registeredUser().getUserAddFundsTransactions().size());
    UserAddFundsTransactionEntity transaction =
        registeredUser().getUserAddFundsTransactions().iterator().next();
    assertEquals("pkg-28", transaction.getPackageId());
    assertEquals(48, transaction.getCreditsAwarded());
    assertEquals(24, transaction.getBonusCredits());
    assertEquals(0, new BigDecimal("28.00").compareTo(transaction.getAmountUsd()));
    assertEquals("Visa •••• 1111", transaction.getPaymentSource());
    assertEquals("txn-123", transaction.getPaymentTransactionId());
    assertEquals(6 + 48 + 24, transaction.getResultingBalance());
    assertEquals(response.timestamp(), transaction.getTimestamp());
    assertEquals(REGISTERED_EMAIL, transaction.getUserEmail());
    // Buying credits is not a play anywhere -- no location's revenue moves until they are spent.
    assertTrue(registeredUser().getUserSongCreditUsages().isEmpty());
    verify(eventPublisher, never()).publishEvent(any(LocationSongCreditUsageRecordedEvent.class));
  }

  @Test
  void addFunds_publishesTheNewBalanceOnlyAfterThePurchaseIsStored() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));

    userServiceImpl.addFunds(REGISTERED_EMAIL, new AddFundsRequest("pkg-7", "fake-nonce"));

    InOrder inOrder = inOrder(paymentGateway, userRepository, eventPublisher);
    inOrder.verify(paymentGateway).charge(any(BigDecimal.class), anyString());
    inOrder.verify(userRepository).storeAggregateRoot(userRoot);
    inOrder.verify(eventPublisher).publishEvent(new UserCreditsChangedEvent(REGISTERED_EMAIL, 19));
    inOrder.verify(eventPublisher).publishEvent(any(PurchaseCompletedEvent.class));
  }

  @Test
  void addFunds_whenThePurchaseCannotBeStored_voidsTheChargeAndGrantsNothing() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));
    when(paymentGateway.voidCharge("txn-123")).thenReturn(true);
    doThrow(new RuntimeException("database unavailable")).when(userRepository)
        .storeAggregateRoot(userRoot);

    PaymentException e = assertThrows(PaymentException.class,
        () -> userServiceImpl.addFunds(REGISTERED_EMAIL, new AddFundsRequest("pkg-7", "nonce")));

    assertTrue(e.getMessage().contains("payment was cancelled"), e.getMessage());
    verify(paymentGateway).voidCharge("txn-123");
    assertEquals(6, registeredUser().getNumCredits(), "the balance must be restored");
    assertTrue(registeredUser().getUserAddFundsTransactions().isEmpty(),
        "no purchase may be left in memory for a later store to write");
    verify(eventPublisher, never()).publishEvent(any(UserCreditsChangedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(PurchaseCompletedEvent.class));
  }

  @Test
  void addFunds_whenThePurchaseCannotBeStoredNorVoided_tellsTheCustomerToContactSupport() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));
    when(paymentGateway.voidCharge("txn-123")).thenReturn(false);
    doThrow(new RuntimeException("database unavailable")).when(userRepository)
        .storeAggregateRoot(userRoot);

    PaymentException e = assertThrows(PaymentException.class,
        () -> userServiceImpl.addFunds(REGISTERED_EMAIL, new AddFundsRequest("pkg-7", "nonce")));

    assertTrue(e.getMessage().contains("contact support"), e.getMessage());
    assertEquals(6, registeredUser().getNumCredits());
    assertTrue(registeredUser().getUserAddFundsTransactions().isEmpty());
  }

  @Test
  void addFunds_onAStandaloneOrSlaveInstance_isRefusedWithoutGrantingCredits() {

    UserServiceImpl standalone = new UserServiceImpl(userRepository, passwordEncoder, jwtUtil,
        eventPublisher, songLibraryService, pricingService, false, new NoOpPaymentGateway());

    assertThrows(PaymentException.class,
        () -> standalone.addFunds(REGISTERED_EMAIL, new AddFundsRequest("pkg-7", "nonce")));

    assertEquals(6, registeredUser().getNumCredits());
    assertTrue(registeredUser().getUserAddFundsTransactions().isEmpty());
    verify(userRepository, never()).storeAggregateRoot(any());
  }

  @Test
  void generatePaymentClientToken_returnsGeneratedToken() {

    when(paymentGateway.generateClientToken()).thenReturn("fake-client-token");

    assertEquals("fake-client-token", userServiceImpl.generatePaymentClientToken());
  }

  @Test
  void updateProfile_updatesFirstAndLastNameAndPersists() {

    UserProfileDto updated =
        userServiceImpl.updateProfile(REGISTERED_EMAIL, new UpdateProfileRequest("Janet", "Doerson"));

    assertEquals("Janet", updated.firstName());
    assertEquals("Doerson", updated.lastName());
    assertEquals("Janet", registeredUser().getFirstName());
    assertEquals("Doerson", registeredUser().getLastName());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HOME PAGE / CREDIT PACKAGES
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getPublicHomePage_returnsHotHereArtistsAndSongsFromSongLibrary() {

    when(songLibraryService.getMusicByPopularity(any()))
        .thenReturn(new SearchResultDto(List.of(), List.of(), List.of()));

    var homePage = userServiceImpl.getPublicHomePage();

    assertNotNull(homePage);
    assertTrue(homePage.artistsHotHere().isEmpty());
    assertTrue(homePage.songsHotHere().isEmpty());
  }

  @Test
  void getHomePage_returnsPlaylistNamesAndSearchHistoryForRegisteredUser() {

    when(songLibraryService.getMusicByPopularity(any()))
        .thenReturn(new SearchResultDto(List.of(), List.of(), List.of()));
    registeredUser().addToSearchHistory("beatles", 10);

    UserHomePageDto homePage = userServiceImpl.getHomePage(REGISTERED_EMAIL);

    assertNotNull(homePage);
    assertEquals(List.of(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME), homePage.myPlaylists());
    assertEquals(List.of("beatles"), homePage.searchHistory());
    assertTrue(homePage.myRecentPlays().isEmpty());
  }

  @Test
  void getHomePage_throwsForUnknownEmailAddress() {

    assertThrows(InvalidPrincipalException.class, () -> userServiceImpl.getHomePage("unknown@example.com"));
  }

  @Test
  void getCreditPackages_returnsThreeFixedPackages() {

    List<CreditPackageDto> packages = userServiceImpl.getCreditPackages();

    assertEquals(3, packages.size());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // SEARCH HISTORY
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getSearchHistory_returnsUsersSearchHistory() {

    registeredUser().addToSearchHistory("queen", 10);

    assertEquals(List.of("queen"), userServiceImpl.getSearchHistory(REGISTERED_EMAIL));
  }

  @Test
  void addSearchHistory_addsQueryAndPersists() {

    userServiceImpl.addSearchHistory(REGISTERED_EMAIL, "queen");

    assertEquals(List.of("queen"), registeredUser().getSearchHistory());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void removeSearchHistory_removesEntryAtIndexAndPersists() {

    UserEntity user = registeredUser();
    user.addToSearchHistory("queen", 10);
    user.addToSearchHistory("beatles", 10); // inserted at index 0 -> ["beatles", "queen"]

    userServiceImpl.removeSearchHistory(REGISTERED_EMAIL, 0);

    assertEquals(List.of("queen"), user.getSearchHistory());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PLAYLISTS
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void createPlaylist_createsNewPlaylistForUserAndPersists() throws Exception {

    boolean result = userServiceImpl.createPlaylist(REGISTERED_EMAIL, "Road Trip");

    assertTrue(result);
    assertNotNull(registeredUser().getPlaylistByNameNullIfNotExists("Road Trip"));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void addSongToPlaylist_addsSongAndPersists() throws Exception {

    registeredUser().createPlaylist("Road Trip");
    SongFileEntity song = buildSong(1, 100);

    boolean result = userServiceImpl.addSongToPlaylist(REGISTERED_EMAIL, "Road Trip", 9, song);

    assertTrue(result);
    assertEquals(List.of(new SongIdentifier(9, 1, 100)),
        registeredUser().getPlaylistByName("Road Trip").getSongs());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void removeSongFromPlaylist_removesSongAndPersists() throws Exception {

    PlaylistEntity playlist = registeredUser().createPlaylist("Road Trip");
    playlist.addSong(new SongIdentifier(9, 1, 100));
    SongFileEntity song = buildSong(1, 100);

    boolean result =
        userServiceImpl.removeSongFromPlaylist(REGISTERED_EMAIL, "Road Trip", 9, song);

    assertTrue(result);
    assertTrue(playlist.getSongs().isEmpty());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void deletePlaylist_removesPlaylistAndPersists() throws Exception {

    registeredUser().createPlaylist("Road Trip");

    boolean result = userServiceImpl.deletePlaylist(REGISTERED_EMAIL, "Road Trip");

    assertTrue(result);
    assertNull(registeredUser().getPlaylistByNameNullIfNotExists("Road Trip"));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void addSongToMyFavoritesPlaylist_addsSongToFavoritesAndPersists() throws Exception {

    SongFileEntity song = buildSong(2, 200);

    boolean result = userServiceImpl.addSongToMyFavoritesPlaylist(REGISTERED_EMAIL, 9, song);

    assertTrue(result);
    assertTrue(registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .getSongs().contains(new SongIdentifier(9, 2, 200)));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void removeSongFromMyFavoritesPlaylist_removesSongFromFavoritesAndPersists() throws Exception {

    registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .addSong(new SongIdentifier(9, 2, 200));
    SongFileEntity song = buildSong(2, 200);

    boolean result = userServiceImpl.removeSongFromMyFavoritesPlaylist(REGISTERED_EMAIL, 9, song);

    assertTrue(result);
    assertFalse(registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .getSongs().contains(new SongIdentifier(9, 2, 200)));
  }

  @Test
  void getPlaylists_returnsSummaryForEachPlaylist() {

    List<PlaylistSummaryDto> playlists = userServiceImpl.getPlaylists(REGISTERED_EMAIL);

    assertEquals(1, playlists.size());
    assertEquals(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, playlists.get(0).name());
    assertEquals(0, playlists.get(0).songCount());
  }

  @Test
  void getPlaylistSongs_returnsSongsResolvedFromSongLibrary() throws Exception {

    registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .addSong(new SongIdentifier(9, 3, 300));
    SongDto songDto = buildSongDto(3, 300);
    when(songLibraryService.getSongById(any(), eq(3), eq(300))).thenReturn(songDto);

    List<SongDto> songs =
        userServiceImpl.getPlaylistSongs(REGISTERED_EMAIL, PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);

    assertEquals(List.of(songDto), songs);
  }

  @Test
  void reorderPlaylistSongs_keepsOnlyExistingSongsInGivenOrderAndPersists() throws Exception {

    PlaylistEntity playlist = registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    SongIdentifier a = new SongIdentifier(9, 1, 1);
    SongIdentifier b = new SongIdentifier(9, 2, 2);
    playlist.addSong(a);
    playlist.addSong(b);

    userServiceImpl.reorderPlaylistSongs(REGISTERED_EMAIL, PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME,
        List.of(b, a, new SongIdentifier(9, 9, 9)));

    assertEquals(List.of(b, a), playlist.getSongs());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void getFavoriteSongIdentifiers_returnsFavoritesPlaylistSongs() {

    SongIdentifier fav = new SongIdentifier(9, 4, 400);
    registeredUser().getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .addSong(fav);

    assertEquals(List.of(fav), userServiceImpl.getFavoriteSongIdentifiers(REGISTERED_EMAIL));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // QUEUE EVENTS / CREDIT CHARGING / CREDIT LEDGER
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void handleSongAddedToQueueEvent_addsToHistoryAndDeductsCreditsForWebUser() {

    // Stubbed explicitly: an unstubbed Integer method on a Mockito mock returns 0, not null.
    when(songLibraryService.getOwnLocationId()).thenReturn(Integer.valueOf(42));
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(5, 500), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false));

    UserEntity user = registeredUser();
    assertTrue(user.getSongPlayHistory().contains(new SongIdentifier(42, 5, 500)));
    assertEquals(4, user.getNumCredits()); // 6 - (normal play: fixed 1 * webCostMultiplier 2)
    verify(eventPublisher).publishEvent(any(UserCreditsChangedEvent.class));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void handleSongAddedToQueueEvent_withLocationId_tagsCreditTransactionWithLocation() {

    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(6, 600), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false),
        Integer.valueOf(42));

    UserSongCreditUsageEntity transaction = registeredUser().getUserSongCreditUsages().iterator().next();
    assertEquals(Integer.valueOf(42), transaction.getLocationId());
  }

  @Test
  void handleSongAddedToQueueEvent_chargesPriorityPlayRateEvenWhenPriorityIsOne() {

    // getHighestPriority() returns 1 whenever the queue's current top entry is a priority-0
    // background song -- a genuine priority play can therefore carry the exact same priority (1)
    // a normal play always does. The priorityPlay flag, not the priority value, must decide cost.
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(7, 700), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, true));

    // 6 - (priority 1 * priorityCostMultiplier 2 * webCostMultiplier 2) = 2, not the
    // normal-play-priced 4 a naive "priority <= 1" check would produce.
    assertEquals(2, registeredUser().getNumCredits());
  }

  @Test
  void chargeCreditsForQueueAction_deductsCreditsBasedOnPriorityAndPersists() {

    registeredUser().setNumCredits(20);

    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 2);

    // cost = max(1, priority(2) * 3) * webCostMultiplier(2) = 12
    assertEquals(8, registeredUser().getNumCredits());
    assertEquals(-12, registeredUser().getUserSongCreditUsages().iterator().next().getAmount());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void chargeCreditsForQueueAction_whenBalanceFallsShort_recordsOnlyTheCreditsActuallyDeducted() {

    // A last line of defense only -- the controller refuses an unaffordable action up front. Each
    // recorded credit is paid out to the location, so the entry must match what the balance lost.
    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 2, Integer.valueOf(42));

    assertEquals(0, registeredUser().getNumCredits());
    UserSongCreditUsageEntity usage = registeredUser().getUserSongCreditUsages().iterator().next();
    assertEquals(-6, usage.getAmount(), "only the 6 credits held may be recorded, not the 12 cost");
    assertEquals(0, usage.getResultingBalance());
  }

  @Test
  void chargeCreditsForQueueAction_withNoBalance_recordsNoUsageAndAnnouncesNothing() {

    registeredUser().setNumCredits(0);

    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, Integer.valueOf(42));

    assertEquals(0, registeredUser().getNumCredits());
    assertTrue(registeredUser().getUserSongCreditUsages().isEmpty());
    verify(eventPublisher, never()).publishEvent(any(LocationSongCreditUsageRecordedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(UserCreditsChangedEvent.class));
  }

  @Test
  void handleSongAddedToQueueEvent_whenBalanceFallsShort_recordsOnlyTheCreditsActuallyDeducted() {

    registeredUser().setNumCredits(1);
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(5, 500), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false),
        Integer.valueOf(42));

    assertEquals(0, registeredUser().getNumCredits());
    assertEquals(-1, registeredUser().getUserSongCreditUsages().iterator().next().getAmount());
  }

  @Test
  void chargeCreditsForQueueAction_withLocationId_tagsCreditTransactionWithLocation() {

    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, Integer.valueOf(7));

    UserSongCreditUsageEntity transaction = registeredUser().getUserSongCreditUsages().iterator().next();
    assertEquals(Integer.valueOf(7), transaction.getLocationId());
  }

  @Test
  void getCreditLedgerForLocation_returnsOnlyMatchingLocationWithinTimeRange() {

    registeredUser().setNumCredits(100);
    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, Integer.valueOf(101));
    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, Integer.valueOf(102));

    Instant from = Instant.now().minusSeconds(60);
    Instant to = Instant.now().plusSeconds(60);

    List<UserSongCreditUsageDto> ledger =
        userServiceImpl.getCreditLedgerForLocation(Integer.valueOf(101), from, to);

    assertEquals(1, ledger.size());
    assertEquals(Integer.valueOf(101), ledger.get(0).locationId());
  }

  @Test
  void handleSongAddedToQueueEvent_forOwnQueue_tagsTheSpendAndPlayWithTheOwnLocation() {

    // Standalone: the song was added to this instance's own queue, so the spend is this
    // location's mobile revenue -- an untagged spend would never be counted in its ledger.
    when(songLibraryService.getOwnLocationId()).thenReturn(Integer.valueOf(42));
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(5, 500), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false));

    UserSongCreditUsageEntity usage = registeredUser().getUserSongCreditUsages().iterator().next();
    assertEquals(Integer.valueOf(42), usage.getLocationId());
    assertEquals(-2, usage.getAmount());
    assertTrue(registeredUser().getSongPlayHistory().contains(new SongIdentifier(42, 5, 500)));
    assertEquals(1, userServiceImpl.getCreditLedgerForLocation(Integer.valueOf(42),
        Instant.now().minusSeconds(60), Instant.now().plusSeconds(60)).size());
  }

  @Test
  void handleSongAddedToQueueEvent_announcesTheSpendOnlyAfterItIsStored() {

    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(5, 500), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false),
        Integer.valueOf(42));

    InOrder inOrder = inOrder(userRepository, eventPublisher);
    inOrder.verify(userRepository).storeAggregateRoot(userRoot);
    inOrder.verify(eventPublisher).publishEvent(any(LocationSongCreditUsageRecordedEvent.class));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // AFFORDABILITY -- refusing an unpaid queue operation before it runs
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void requireAffordableQueueAdd_passesWhenTheBalanceCoversTheCost() {

    // normal play: 1 * webCostMultiplier 2 = 2; priority 1 play: 1 * 2 * 2 = 4; balance 6
    userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false);
    userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, true);

    registeredUser().setNumCredits(2);
    userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false); // exactly enough

    assertEquals(2, registeredUser().getNumCredits(), "a check never charges");
    verify(userRepository, never()).storeAggregateRoot(any());
  }

  @Test
  void requireAffordableQueueAdd_refusesWhenTheBalanceFallsShort() {

    registeredUser().setNumCredits(3);

    // priority 1 play: 1 * priorityCostMultiplier 2 * webCostMultiplier 2 = 4
    InsufficientCreditsException e = assertThrows(InsufficientCreditsException.class,
        () -> userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, true));
    assertEquals(4, e.getCost());
    assertEquals(3, e.getBalance());

    registeredUser().setNumCredits(0);
    assertThrows(InsufficientCreditsException.class,
        () -> userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false));
  }

  @Test
  void requireAffordableQueueAdd_pricesAtTheLocationsOwnConfig() {

    when(pricingService.resolvePricingConfig(Integer.valueOf(43)))
        .thenReturn(new PricingConfig(2, 3, 3, 10, 5, false));
    registeredUser().setNumCredits(4);

    userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false); // costs 2
    assertThrows(InsufficientCreditsException.class,
        () -> userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 43, 1, false)); // costs 5
  }

  @Test
  void requireAffordableQueueAction_refusesWhenTheBalanceFallsShort() {

    // priority 1: max(1, 1 * 3) * webCostMultiplier 2 = 6 -- exactly the balance of 6
    userServiceImpl.requireAffordableQueueAction(REGISTERED_EMAIL, 1, 42);

    // priority 2: max(1, 2 * 3) * 2 = 12
    InsufficientCreditsException e = assertThrows(InsufficientCreditsException.class,
        () -> userServiceImpl.requireAffordableQueueAction(REGISTERED_EMAIL, 2, 42));
    assertEquals(12, e.getCost());
  }

  @Test
  void requireAffordable_rejectsAnUnknownUser() {

    assertThrows(InvalidPrincipalException.class,
        () -> userServiceImpl.requireAffordableQueueAdd("unknown@example.com", 42, 1, false));
    assertThrows(InvalidPrincipalException.class,
        () -> userServiceImpl.requireAffordableQueueAction("unknown@example.com", 1, 42));
  }

  @Test
  void slaveMode_refusesAPatronQueueingDirectly_butLetsAnAdminQueueUncharged() {

    UserServiceImpl slaveUserService = new UserServiceImpl(userRepository, passwordEncoder,
        jwtUtil, eventPublisher, songLibraryService, pricingService, true, paymentGateway);
    userRoot.addUser(new UserEntity(Integer.valueOf(2), "Ada", "Min", "admin@example.com",
        "hash", Integer.valueOf(0), UserRole.ROLE_ADMIN));

    // A slave never charges a web user (credits are master-owned), so a patron must not queue
    // here at all -- otherwise every play would be free.
    assertThrows(QueueAccessDeniedException.class,
        () -> slaveUserService.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false));
    assertThrows(QueueAccessDeniedException.class,
        () -> slaveUserService.requireAffordableQueueAction(REGISTERED_EMAIL, 1, 42));
    assertThrows(QueueAccessDeniedException.class,
        () -> slaveUserService.getAffordableQueueAddCount(REGISTERED_EMAIL, 42, 1, false));
    assertThrows(QueueAccessDeniedException.class,
        () -> slaveUserService.requireAffordableQueueAdd("not-on-this-slave@example.com", 42, 1,
            false));

    slaveUserService.requireAffordableQueueAdd("admin@example.com", 42, 1, false);
    slaveUserService.requireAffordableQueueAction("admin@example.com", 1, 42);
    assertEquals(Integer.MAX_VALUE,
        slaveUserService.getAffordableQueueAddCount("admin@example.com", 42, 1, false));
  }

  @Test
  void slaveMode_withAllowSlaveUrlQueueOperations_letsAPatronQueueDirectly_uncharged() {

    // app.allow-slave-url-queue-operations=true -- local/testing only.
    UserServiceImpl slaveUserService = new UserServiceImpl(userRepository, passwordEncoder,
        jwtUtil, eventPublisher, songLibraryService, pricingService, true, paymentGateway, true);
    registeredUser().setNumCredits(0);

    slaveUserService.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, true);
    slaveUserService.requireAffordableQueueAction(REGISTERED_EMAIL, 1, 42);
    slaveUserService.requireAffordableQueueAdd("not-on-this-slave@example.com", 42, 1, false);
    assertEquals(Integer.MAX_VALUE,
        slaveUserService.getAffordableQueueAddCount(REGISTERED_EMAIL, 42, 1, false));

    // Still never charged on a slave: credits are master's.
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(5, 500), 1, null);
    slaveUserService.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false), 42);
    slaveUserService.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, 42);
    assertTrue(registeredUser().getUserSongCreditUsages().isEmpty());
  }

  @Test
  void allowSlaveUrlQueueOperations_isIgnoredOutsideSlaveMode() {

    UserServiceImpl standalone = new UserServiceImpl(userRepository, passwordEncoder, jwtUtil,
        eventPublisher, songLibraryService, pricingService, false, paymentGateway, true);
    registeredUser().setNumCredits(0);

    assertThrows(InsufficientCreditsException.class,
        () -> standalone.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false));
  }

  @Test
  void getAffordableQueueAddCount_isTheBalanceDividedByTheCost() {

    registeredUser().setNumCredits(7);

    assertEquals(3, userServiceImpl.getAffordableQueueAddCount(REGISTERED_EMAIL, 42, 1, false));
    registeredUser().setNumCredits(1);
    assertEquals(0, userServiceImpl.getAffordableQueueAddCount(REGISTERED_EMAIL, 42, 1, false));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // OWN LOCATION ID CORRECTION
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void handleOwnLocationIdChangedEvent_retagsOnlyStateUnderThePreviousId_andPersists() {

    Integer previousLocationId = Integer.valueOf(7);
    Integer confirmedLocationId = Integer.valueOf(42);
    Integer otherLocationId = Integer.valueOf(99);

    UserEntity user = registeredUser();
    user.addSongToSongPlayHistory(new SongIdentifier(previousLocationId, 1, 2));
    user.addSongToSongPlayHistory(new SongIdentifier(otherLocationId, 3, 4));

    PlaylistEntity favorites = user.createMyFavoritesPlaylist();
    favorites.addSong(new SongIdentifier(previousLocationId, 5, 6));
    favorites.addSong(new SongIdentifier(otherLocationId, 7, 8));

    user.setNumCredits(100);
    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, previousLocationId);
    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, otherLocationId);
    clearInvocations(userRepository);

    userServiceImpl.handleOwnLocationIdChangedEvent(
        new OwnLocationIdChangedEvent(previousLocationId, confirmedLocationId));

    assertEquals(List.of(new SongIdentifier(confirmedLocationId, 1, 2),
        new SongIdentifier(otherLocationId, 3, 4)), user.getSongPlayHistory());
    assertEquals(List.of(new SongIdentifier(confirmedLocationId, 5, 6),
        new SongIdentifier(otherLocationId, 7, 8)), favorites.getSongs());

    List<Integer> usageLocationIds = user.getUserSongCreditUsages().stream()
        .map(UserSongCreditUsageEntity::getLocationId)
        .sorted()
        .toList();
    assertEquals(List.of(confirmedLocationId, otherLocationId), usageLocationIds);

    verify(userRepository).storeAggregateRoot(userRoot);
  }
}
