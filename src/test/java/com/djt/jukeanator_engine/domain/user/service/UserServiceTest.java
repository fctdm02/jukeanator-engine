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
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;
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
import com.djt.jukeanator_engine.domain.user.dto.LocationBalanceDto;
import com.djt.jukeanator_engine.domain.user.dto.UserHomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.event.LocationSongCreditUsageRecordedEvent;
import com.djt.jukeanator_engine.domain.user.event.PurchaseCompletedEvent;
import com.djt.jukeanator_engine.domain.user.event.UserCreditsChangedEvent;
import com.djt.jukeanator_engine.domain.user.event.VerificationEmailRequestedEvent;
import com.djt.jukeanator_engine.domain.user.exception.AccountNotVerifiedException;
import com.djt.jukeanator_engine.domain.user.exception.InsufficientCreditsException;
import com.djt.jukeanator_engine.domain.user.exception.InvalidCredentialsException;
import com.djt.jukeanator_engine.domain.user.exception.PaymentException;
import com.djt.jukeanator_engine.domain.user.exception.QueueAccessDeniedException;
import com.djt.jukeanator_engine.domain.user.exception.EmailAlreadyRegisteredException;
import com.djt.jukeanator_engine.domain.user.exception.IncorrectPasswordException;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageEntity;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserAddFundsTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserLocationAccountEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.model.UserStatus;
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

  // Credits, plays and playlists are per location: the registered patron starts with an account
  // (6 credits) at this location only.
  private static final Integer LOC = Integer.valueOf(42);

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
  private FinancialLedgerService financialLedgerService;

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
    when(pricingService.resolveCreditPackages(any()))
        .thenReturn(CreditPackageCatalog.DEFAULT_PACKAGES);

    paymentGateway = mock(PaymentGateway.class);
    financialLedgerService = mock(FinancialLedgerService.class);

    userRoot = new UserRootEntity();
    UserEntity registered = new UserEntity(Integer.valueOf(1), "Jane", "Doe", REGISTERED_EMAIL,
        REGISTERED_PASSWORD_HASH, UserRole.ROLE_USER);
    registered.getOrCreateLocationAccount(LOC).setNumCredits(6);
    userRoot.addUser(registered);

    when(userRepository.loadAggregateRoot(anyString())).thenReturn(userRoot);

    userServiceImpl = new UserServiceImpl(userRepository, passwordEncoder, jwtUtil, eventPublisher,
        songLibraryService, pricingService, false, paymentGateway);
    userServiceImpl.setFinancialLedgerServiceSupplier(() -> financialLedgerService);
  }

  private UserEntity registeredUser() {
    return userRoot.getUserByEmailAddressNullIfNotExists(REGISTERED_EMAIL);
  }

  /** The registered patron's account at {@code locationId} (created if needed). */
  private UserLocationAccountEntity account(Integer locationId) {
    return registeredUser().getOrCreateLocationAccount(locationId);
  }

  /** The registered patron's account at {@link #LOC}. */
  private UserLocationAccountEntity account() {
    return account(LOC);
  }

  /** The registered patron's balance at {@link #LOC}. */
  private int credits() {
    return registeredUser().getNumCredits(LOC);
  }

  private static AddFundsRequest addFundsAt(Integer locationId, String packageId, String nonce) {
    return new AddFundsRequest(locationId, packageId, nonce);
  }

  /** Minimal {@link SongFileEntity} whose {@code getAlbum().getId()} and own
   *  {@code getId()} resolve to the given ids, matching what
   *  {@link UserLocationAccountEntity#addSongToPlaylist} needs to build a
   *  {@link SongIdentifier}. */
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
    assertThrows(EmailAlreadyRegisteredException.class, () -> userService.register(registerRequest));

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

    assertThrows(EmailAlreadyRegisteredException.class, () -> userServiceImpl.addAdminUser(request));
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
    when(passwordEncoder.encode("newPass123")).thenReturn("new-hashed-password");

    userServiceImpl.changePassword(REGISTERED_EMAIL, new ChangePasswordRequest("oldPass", "newPass123"));

    assertEquals("new-hashed-password", registeredUser().getPasswordHash());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void changePassword_throwsWhenCurrentPasswordIncorrect() {

    when(passwordEncoder.matches("wrongPass", REGISTERED_PASSWORD_HASH)).thenReturn(false);

    assertThrows(IncorrectPasswordException.class, () -> userServiceImpl.changePassword(REGISTERED_EMAIL,
        new ChangePasswordRequest("wrongPass", "newPass123")));
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
    userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-7", "nonce"));
    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, LOC);
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
    UserLocationAccountEntity closedAccount = closed.getLocationAccountNullIfNotExists(LOC);
    assertTrue(closedAccount.getPlaylists().isEmpty());
    assertTrue(closedAccount.getSongPlayHistory().isEmpty());

    // The financial records: the Braintree charge stays reconcilable, and location 42's spend
    // audit trail is unchanged.
    assertEquals("txn-123",
        closedAccount.getAddFundsTransactions().iterator().next().getPaymentTransactionId());
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

    userServiceImpl.register(new RegisterRequest("Jane", "Doe", REGISTERED_EMAIL, "password123"));

    UserEntity fresh = registeredUser();
    assertEquals(0, fresh.getNumCredits(LOC), "a new account never inherits a closed one's credits");
    assertTrue(fresh.getLocationAccounts().isEmpty());
    assertEquals(2, userRoot.getUsers().size());
  }

  @Test
  void register_newAccountStartsWithNoCredits() {

    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");

    userServiceImpl.register(new RegisterRequest("New", "Patron", "new@example.com", "password123"));

    // Every credit a web user spends was bought for the location, so none may be free.
    UserEntity created = userRoot.getUserByEmailAddressNullIfNotExists("new@example.com");
    assertTrue(created.getLocationAccounts().isEmpty(), "no location accounts until first use");
    assertEquals(0, created.getNumCredits(LOC));
    assertEquals(0, userServiceImpl.getAffordableQueueAddCount("new@example.com", 42, 1, false));
    assertThrows(InsufficientCreditsException.class,
        () -> userServiceImpl.requireAffordableQueueAdd("new@example.com", 42, 1, false));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // EMAIL VERIFICATION — register (PENDING) / login / verifyAccount / resendVerification
  // ─────────────────────────────────────────────────────────────────────────

  private UserServiceImpl verifyingService(java.time.Duration tokenTtl) {
    return new UserServiceImpl(userRepository, passwordEncoder, jwtUtil, eventPublisher,
        songLibraryService, pricingService, false, paymentGateway, false,
        new EmailVerificationPolicy(true, null, tokenTtl));
  }

  /** Registers {@code email} PENDING and returns the raw token from the emailed link. */
  private String registerPending(UserServiceImpl service, String email) {

    clearInvocations(eventPublisher);
    AuthResponse response = service.register(
        new RegisterRequest("New", "Patron", email, "password123"), "http://jukebox.test/");
    assertNull(response.token(), "a PENDING account is not logged in");
    assertEquals(email, response.emailAddress());
    return tokenFromLastVerificationEmail();
  }

  private String tokenFromLastVerificationEmail() {

    org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
    VerificationEmailRequestedEvent event = captor.getAllValues().stream()
        .filter(VerificationEmailRequestedEvent.class::isInstance)
        .map(VerificationEmailRequestedEvent.class::cast)
        .reduce((first, second) -> second)
        .orElseThrow();
    String prefix = "http://jukebox.test/api/users/verify?token=";
    assertTrue(event.verificationUrl().startsWith(prefix), event.verificationUrl());
    return event.verificationUrl().substring(prefix.length());
  }

  @Test
  void register_withVerification_createsPendingAccountAndEmailsLink() {

    UserServiceImpl service = verifyingService(java.time.Duration.ofHours(24));
    String token = registerPending(service, "new@example.com");

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists("new@example.com");
    assertEquals(UserStatus.PENDING, user.getStatus());
    assertNotNull(user.getVerificationTokenHash());
    assertFalse(user.getVerificationTokenHash().contains(token), "only the hash is stored");
    verify(jwtUtil, never()).generateToken(anyString(), anyString());
  }

  @Test
  void login_refusesPendingAccountUntilVerified() throws Exception {

    when(passwordEncoder.encode(anyString())).thenReturn("hash");
    when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");
    UserServiceImpl service = verifyingService(java.time.Duration.ofHours(24));
    String token = registerPending(service, "new@example.com");

    LoginRequest login = new LoginRequest("new@example.com", "password123");
    assertThrows(AccountNotVerifiedException.class, () -> service.login(login));

    assertEquals(VerificationResult.VERIFIED, service.verifyAccount(token));
    assertEquals(UserStatus.ACTIVE,
        userRoot.getUserByEmailAddressNullIfNotExists("new@example.com").getStatus());
    assertEquals("token", service.login(login).token());

    // The link is single-use.
    assertEquals(VerificationResult.INVALID, service.verifyAccount(token));
  }

  @Test
  void login_wrongPasswordOnPendingAccountIsPlainInvalidCredentials() {

    when(passwordEncoder.encode(anyString())).thenReturn("hash");
    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
    UserServiceImpl service = verifyingService(java.time.Duration.ofHours(24));
    registerPending(service, "new@example.com");

    InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
        () -> service.login(new LoginRequest("new@example.com", "wrong")));
    assertFalse(ex instanceof AccountNotVerifiedException);
  }

  @Test
  void verifyAccount_expiredOrUnknownToken() {

    UserServiceImpl service = verifyingService(java.time.Duration.ofSeconds(-1));
    String token = registerPending(service, "new@example.com");

    assertEquals(VerificationResult.EXPIRED, service.verifyAccount(token));
    assertEquals(VerificationResult.INVALID, service.verifyAccount("not-a-real-token"));
    assertEquals(VerificationResult.INVALID, service.verifyAccount(null));
    assertTrue(userRoot.getUserByEmailAddressNullIfNotExists("new@example.com").isPending());
  }

  @Test
  void resendVerification_replacesTheTokenOnlyForPendingAccounts() {

    UserServiceImpl service = verifyingService(java.time.Duration.ofHours(24));
    String firstToken = registerPending(service, "new@example.com");

    clearInvocations(eventPublisher);
    service.resendVerification("NEW@example.com", "http://jukebox.test");
    String secondToken = tokenFromLastVerificationEmail();

    assertEquals(VerificationResult.INVALID, service.verifyAccount(firstToken));
    assertEquals(VerificationResult.VERIFIED, service.verifyAccount(secondToken));

    // Neither an ACTIVE nor an unknown account gets an email.
    clearInvocations(eventPublisher);
    service.resendVerification("new@example.com", "http://jukebox.test");
    service.resendVerification("nobody@example.com", "http://jukebox.test");
    verify(eventPublisher, never()).publishEvent(any(VerificationEmailRequestedEvent.class));
  }

  @Test
  void register_pendingEmailAgainIsRejected() {

    UserServiceImpl service = verifyingService(java.time.Duration.ofHours(24));
    registerPending(service, "new@example.com");

    assertThrows(EmailAlreadyRegisteredException.class, () -> service.register(
        new RegisterRequest("New", "Patron", "new@example.com", "password123"), "http://x"));
  }

  @Test
  void addAdminUser_isActiveEvenWhenVerificationIsRequired() {

    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");
    UserServiceImpl service = verifyingService(java.time.Duration.ofHours(24));

    AuthResponse response =
        service.addAdminUser(new RegisterRequest("Ad", "Min", "admin@example.com", "password123"));

    assertEquals("token", response.token());
    assertEquals(UserStatus.ACTIVE,
        userRoot.getUserByEmailAddressNullIfNotExists("admin@example.com").getStatus());
  }

  @Test
  void register_withoutVerification_logsStraightIn() {

    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");

    AuthResponse response = userServiceImpl.register(
        new RegisterRequest("New", "Patron", "new@example.com", "password123"));

    assertEquals("token", response.token());
    assertEquals(UserStatus.ACTIVE,
        userRoot.getUserByEmailAddressNullIfNotExists("new@example.com").getStatus());
  }

  @Test
  void register_rejectsTheClosedAccountEmailDomain() {

    assertThrows(IllegalArgumentException.class, () -> userServiceImpl.register(new RegisterRequest(
        "X", "Y", "someone" + UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN, "password123")));
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
        addFundsAt(LOC, "nonexistent-pkg", "fake-nonce")));

    verify(userRepository, never()).storeAggregateRoot(userRoot);
  }

  @Test
  void addFunds_throwsForMissingNonce() {

    assertThrows(PaymentException.class,
        () -> userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-7", null)));

    verify(userRepository, never()).storeAggregateRoot(userRoot);
  }

  @Test
  void addFunds_throwsAndGrantsNoCreditsWhenDeclined() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.failure("Payment declined: Do Not Honor"));

    int before = credits();

    assertThrows(PaymentException.class, () -> userServiceImpl.addFunds(REGISTERED_EMAIL,
        addFundsAt(LOC, "pkg-7", "fake-nonce")));

    assertEquals(before, credits());
    verify(userRepository, never()).storeAggregateRoot(userRoot);
    verify(financialLedgerService, never()).recordMobilePurchase(any(), any(), any(), any(),
        any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    verify(eventPublisher, never()).publishEvent(any(UserCreditsChangedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(PurchaseCompletedEvent.class));
  }

  @Test
  void addFunds_grantsCreditsAndPublishesEventsOnSuccess() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));

    AddFundsResponseDto response =
        userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-7", "fake-nonce"));

    // pkg-7: 12 credits + 2 bonus = 14, starting balance 6 -> 20
    assertEquals(20, credits());
    assertEquals(Integer.valueOf(20), response.numCredits());
    assertEquals(LOC, response.locationId());
    assertEquals(12, response.creditsAdded());
    assertEquals(2, response.bonusCreditsAdded());
    assertEquals("PayPal", response.paymentSource());
    assertEquals("txn-123", response.transactionId());
    verify(userRepository).storeAggregateRoot(userRoot);
    verify(eventPublisher).publishEvent(any(UserCreditsChangedEvent.class));
    verify(eventPublisher).publishEvent(any(PurchaseCompletedEvent.class));
    verify(paymentGateway).charge(eq(new BigDecimal("7.00")), eq("fake-nonce"));
  }

  @Test
  void addFunds_recordsThePurchaseOnTheLocationAccount_forBraintreeReconciliation() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "Visa •••• 1111"));

    AddFundsResponseDto response =
        userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-28", "fake-nonce"));

    assertEquals(1, account().getAddFundsTransactions().size());
    UserAddFundsTransactionEntity transaction =
        account().getAddFundsTransactions().iterator().next();
    assertEquals(LOC, transaction.getLocationId());
    assertEquals("pkg-28", transaction.getPackageId());
    assertEquals(48, transaction.getCreditsAwarded());
    assertEquals(24, transaction.getBonusCredits());
    assertEquals(0, new BigDecimal("28.00").compareTo(transaction.getAmountUsd()));
    assertEquals("Visa •••• 1111", transaction.getPaymentSource());
    assertEquals("txn-123", transaction.getPaymentTransactionId());
    assertEquals(6 + 48 + 24, transaction.getResultingBalance());
    assertEquals(response.timestamp(), transaction.getTimestamp());
    assertEquals(REGISTERED_EMAIL, transaction.getUserEmail());
    // Buying credits is not a play: no spend is recorded.
    assertTrue(account().getSongCreditUsages().isEmpty());
    verify(eventPublisher, never()).publishEvent(any(LocationSongCreditUsageRecordedEvent.class));
  }

  @Test
  void addFunds_recordsTheWholePurchaseInThatLocationsFinancialLedger() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));

    userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-28", "fake-nonce"));

    // Once, for the full package price -- like a swipe on the location's own card reader.
    verify(financialLedgerService).recordMobilePurchase(LOC, new BigDecimal("28.00"),
        REGISTERED_EMAIL, "txn-123", "pkg-28", 48, 24);
  }

  @Test
  void addFunds_creditsOnlyTheChosenLocation() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));
    Integer otherLocation = Integer.valueOf(99);

    userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(otherLocation, "pkg-7", "fake-nonce"));

    assertEquals(14, registeredUser().getNumCredits(otherLocation));
    assertEquals(6, credits(), "the balance at another location is untouched");
    assertTrue(account().getAddFundsTransactions().isEmpty());
    verify(financialLedgerService).recordMobilePurchase(eq(otherLocation), any(), any(), any(),
        any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void addFunds_withoutALocation_isRefusedBeforeCharging() {

    when(songLibraryService.getOwnLocationId()).thenReturn(null); // master: no own location

    assertThrows(PaymentException.class, () -> userServiceImpl.addFunds(REGISTERED_EMAIL,
        addFundsAt(null, "pkg-7", "fake-nonce")));

    verify(paymentGateway, never()).charge(any(), any());
    verify(userRepository, never()).storeAggregateRoot(any());
  }

  @Test
  void addFunds_sellsOnlyThatLocationsPackages() {

    when(pricingService.resolveCreditPackages(Integer.valueOf(99))).thenReturn(
        List.of(new CreditPackageDto("pkg-99", 10, 0, new BigDecimal("5.00"), null)));

    // pkg-7 is sold at location 42 but not at 99.
    assertThrows(PaymentException.class, () -> userServiceImpl.addFunds(REGISTERED_EMAIL,
        addFundsAt(Integer.valueOf(99), "pkg-7", "fake-nonce")));
    verify(paymentGateway, never()).charge(any(), any());
  }

  @Test
  void addFunds_whenTheLedgerCannotRecordIt_voidsTheChargeAndGrantsNothing() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));
    when(paymentGateway.voidCharge("txn-123")).thenReturn(true);
    when(financialLedgerService.recordMobilePurchase(any(), any(), any(), any(), any(),
        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenThrow(new RuntimeException("ledger unavailable"));

    PaymentException e = assertThrows(PaymentException.class,
        () -> userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-7", "nonce")));

    assertTrue(e.getMessage().contains("payment was cancelled"), e.getMessage());
    verify(paymentGateway).voidCharge("txn-123");
    assertEquals(6, credits(), "the balance must be restored");
    assertTrue(account().getAddFundsTransactions().isEmpty());
    verify(eventPublisher, never()).publishEvent(any(UserCreditsChangedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(PurchaseCompletedEvent.class));
  }

  @Test
  void addFunds_atANewLocation_whoseLedgerWriteFails_leavesNoEmptyAccountBehind() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));
    when(financialLedgerService.recordMobilePurchase(any(), any(), any(), any(), any(),
        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenThrow(new RuntimeException("ledger unavailable"));

    assertThrows(PaymentException.class, () -> userServiceImpl.addFunds(REGISTERED_EMAIL,
        addFundsAt(Integer.valueOf(99), "pkg-7", "nonce")));

    assertNull(registeredUser().getLocationAccountNullIfNotExists(Integer.valueOf(99)));
  }

  @Test
  void addFunds_publishesTheNewBalanceOnlyAfterThePurchaseIsStored() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));

    userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-7", "fake-nonce"));

    InOrder inOrder =
        inOrder(paymentGateway, userRepository, financialLedgerService, eventPublisher);
    inOrder.verify(paymentGateway).charge(any(BigDecimal.class), anyString());
    inOrder.verify(userRepository).storeAggregateRoot(userRoot);
    inOrder.verify(financialLedgerService).recordMobilePurchase(any(), any(), any(), any(), any(),
        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    inOrder.verify(eventPublisher)
        .publishEvent(new UserCreditsChangedEvent(REGISTERED_EMAIL, LOC, 20));
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
        () -> userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-7", "nonce")));

    assertTrue(e.getMessage().contains("payment was cancelled"), e.getMessage());
    verify(paymentGateway).voidCharge("txn-123");
    assertEquals(6, credits(), "the balance must be restored");
    assertTrue(account().getAddFundsTransactions().isEmpty(),
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
        () -> userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-7", "nonce")));

    assertTrue(e.getMessage().contains("contact support"), e.getMessage());
    assertEquals(6, credits());
    assertTrue(account().getAddFundsTransactions().isEmpty());
  }

  @Test
  void addFunds_onAStandaloneOrSlaveInstance_isRefusedWithoutGrantingCredits() {

    UserServiceImpl standalone = new UserServiceImpl(userRepository, passwordEncoder, jwtUtil,
        eventPublisher, songLibraryService, pricingService, false, new NoOpPaymentGateway());

    assertThrows(PaymentException.class,
        () -> standalone.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-7", "nonce")));

    assertEquals(6, credits());
    assertTrue(account().getAddFundsTransactions().isEmpty());
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

  @Test
  void getProfile_listsTheBalanceAtEveryLocationThePatronHasUsed() {

    account(Integer.valueOf(99)).setNumCredits(14);

    UserProfileDto profile = userServiceImpl.getProfile(REGISTERED_EMAIL);

    assertEquals(List.of(new LocationBalanceDto(LOC, null, 6),
        new LocationBalanceDto(Integer.valueOf(99), null, 14)), profile.balances());
  }

  @Test
  void getNumCredits_isTheBalanceAtTheGivenLocationOnly() {

    assertEquals(6, userServiceImpl.getNumCredits(REGISTERED_EMAIL, LOC));
    assertEquals(0, userServiceImpl.getNumCredits(REGISTERED_EMAIL, Integer.valueOf(99)));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HOME PAGE / CREDIT PACKAGES
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getPublicHomePage_returnsHotHereArtistsAndSongsFromSongLibrary() {

    when(songLibraryService.getMusicByPopularity(any()))
        .thenReturn(new SearchResultDto(List.of(), List.of(), List.of()));

    var homePage = userServiceImpl.getPublicHomePage(42);

    assertNotNull(homePage);
    assertTrue(homePage.artistsHotHere().isEmpty());
    assertTrue(homePage.songsHotHere().isEmpty());
  }

  @Test
  void getHomePage_returnsPlaylistNamesAndSearchHistoryForRegisteredUser() {

    when(songLibraryService.getMusicByPopularity(any()))
        .thenReturn(new SearchResultDto(List.of(), List.of(), List.of()));
    registeredUser().addToSearchHistory("beatles", 10);

    UserHomePageDto homePage = userServiceImpl.getHomePage(REGISTERED_EMAIL, 42);

    assertNotNull(homePage);
    assertEquals(List.of(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME), homePage.myPlaylists());
    assertEquals(List.of("beatles"), homePage.searchHistory());
    assertTrue(homePage.myRecentPlays().isEmpty());
    assertEquals(6, homePage.numCredits());
  }

  @Test
  void getHomePage_showsOnlyTheRequestedLocationsRecentPlaysAndPlaylists() throws Exception {

    when(songLibraryService.getMusicByPopularity(any()))
        .thenReturn(new SearchResultDto(List.of(), List.of(), List.of()));
    account().addSongToSongPlayHistory(new SongIdentifier(LOC, 1, 1));
    account().createPlaylist("Here Only");
    UserLocationAccountEntity elsewhere = account(Integer.valueOf(99));
    elsewhere.addSongToSongPlayHistory(new SongIdentifier(99, 2, 2));
    elsewhere.createPlaylist("Elsewhere Only");
    SongDto here = buildSongDto(1, 1);
    when(songLibraryService.getSongById(LOC, 1, 1)).thenReturn(here);

    UserHomePageDto homePage = userServiceImpl.getHomePage(REGISTERED_EMAIL, LOC);

    assertEquals(List.of(here), homePage.myRecentPlays());
    assertEquals(List.of(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, "Here Only"),
        homePage.myPlaylists());
    verify(songLibraryService, never()).getSongById(eq(Integer.valueOf(99)), any(), any());
  }

  @Test
  void getHomePage_withNoLocationSelected_hasNoLocationSpecificSections() {

    when(songLibraryService.getOwnLocationId()).thenReturn(null); // master

    UserHomePageDto homePage = userServiceImpl.getHomePage(REGISTERED_EMAIL, null);

    assertTrue(homePage.myRecentPlays().isEmpty());
    assertTrue(homePage.myPlaylists().isEmpty());
    assertEquals(0, homePage.numCredits());
  }

  @Test
  void getHomePage_throwsForUnknownEmailAddress() {

    assertThrows(InvalidPrincipalException.class, () -> userServiceImpl.getHomePage("unknown@example.com", 42));
  }

  @Test
  void getCreditPackages_areTheLocationsOwnPackages() {

    CreditPackageDto pkg = new CreditPackageDto("pkg-10", 20, 0, new BigDecimal("10.00"), null);
    when(pricingService.resolveCreditPackages(Integer.valueOf(99))).thenReturn(List.of(pkg));

    assertEquals(List.of(pkg), userServiceImpl.getCreditPackages(Integer.valueOf(99)));
    assertEquals(CreditPackageCatalog.DEFAULT_PACKAGES, userServiceImpl.getCreditPackages(LOC));
  }

  @Test
  void addFunds_sellsAConfiguredPackage() {

    when(paymentGateway.charge(any(BigDecimal.class), anyString()))
        .thenReturn(PaymentChargeResult.success("txn-123", "PayPal"));
    when(pricingService.resolveCreditPackages(LOC)).thenReturn(
        List.of(new CreditPackageDto("pkg-10", 20, 4, new BigDecimal("10.00"), null)));

    AddFundsResponseDto response =
        userServiceImpl.addFunds(REGISTERED_EMAIL, addFundsAt(LOC, "pkg-10", "fake-nonce"));

    assertEquals(Integer.valueOf(30), response.numCredits()); // 6 + 20 + 4
    verify(paymentGateway).charge(eq(new BigDecimal("10.00")), eq("fake-nonce"));
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

    boolean result = userServiceImpl.createPlaylist(REGISTERED_EMAIL, LOC, "Road Trip");

    assertTrue(result);
    assertNotNull(account().getPlaylistByNameNullIfNotExists("Road Trip"));
    assertNull(account(Integer.valueOf(99)).getPlaylistByNameNullIfNotExists("Road Trip"),
        "a playlist belongs to one location");
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void addSongToPlaylist_addsSongAndPersists() throws Exception {

    account().createPlaylist("Road Trip");
    SongFileEntity song = buildSong(1, 100);

    boolean result = userServiceImpl.addSongToPlaylist(REGISTERED_EMAIL, "Road Trip", LOC, song);

    assertTrue(result);
    assertEquals(List.of(new SongIdentifier(LOC, 1, 100)),
        account().getPlaylistByName("Road Trip").getSongs());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void removeSongFromPlaylist_removesSongAndPersists() throws Exception {

    PlaylistEntity playlist = account().createPlaylist("Road Trip");
    playlist.addSong(new SongIdentifier(LOC, 1, 100));
    SongFileEntity song = buildSong(1, 100);

    boolean result =
        userServiceImpl.removeSongFromPlaylist(REGISTERED_EMAIL, "Road Trip", LOC, song);

    assertTrue(result);
    assertTrue(playlist.getSongs().isEmpty());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void deletePlaylist_removesPlaylistAndPersists() throws Exception {

    account().createPlaylist("Road Trip");

    boolean result = userServiceImpl.deletePlaylist(REGISTERED_EMAIL, LOC, "Road Trip");

    assertTrue(result);
    assertNull(account().getPlaylistByNameNullIfNotExists("Road Trip"));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void addSongToMyFavoritesPlaylist_addsSongToFavoritesAndPersists() throws Exception {

    SongFileEntity song = buildSong(2, 200);

    boolean result = userServiceImpl.addSongToMyFavoritesPlaylist(REGISTERED_EMAIL, LOC, song);

    assertTrue(result);
    assertTrue(account().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .getSongs().contains(new SongIdentifier(LOC, 2, 200)));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void removeSongFromMyFavoritesPlaylist_removesSongFromFavoritesAndPersists() throws Exception {

    account().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .addSong(new SongIdentifier(LOC, 2, 200));
    SongFileEntity song = buildSong(2, 200);

    boolean result = userServiceImpl.removeSongFromMyFavoritesPlaylist(REGISTERED_EMAIL, LOC, song);

    assertTrue(result);
    assertFalse(account().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .getSongs().contains(new SongIdentifier(LOC, 2, 200)));
  }

  @Test
  void getPlaylists_returnsSummaryForEachPlaylist() {

    List<PlaylistSummaryDto> playlists = userServiceImpl.getPlaylists(REGISTERED_EMAIL, LOC);

    assertEquals(1, playlists.size());
    assertEquals(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, playlists.get(0).name());
    assertEquals(0, playlists.get(0).songCount());
  }

  @Test
  void getPlaylists_atANewLocation_createsAnAccountWithItsOwnMyFavorites() {

    List<PlaylistSummaryDto> playlists =
        userServiceImpl.getPlaylists(REGISTERED_EMAIL, Integer.valueOf(99));

    assertEquals(List.of(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME),
        playlists.stream().map(PlaylistSummaryDto::name).toList());
    assertNotNull(registeredUser().getLocationAccountNullIfNotExists(Integer.valueOf(99)));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void playlistMethods_onMasterWithNoLocation_areRefused() {

    when(songLibraryService.getOwnLocationId()).thenReturn(null); // master

    assertThrows(IllegalArgumentException.class,
        () -> userServiceImpl.getPlaylists(REGISTERED_EMAIL, null));
    assertThrows(IllegalArgumentException.class,
        () -> userServiceImpl.createPlaylist(REGISTERED_EMAIL, null, "Road Trip"));
  }

  @Test
  void getPlaylistSongs_returnsSongsResolvedFromSongLibrary() throws Exception {

    account().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .addSong(new SongIdentifier(LOC, 3, 300));
    SongDto songDto = buildSongDto(3, 300);
    when(songLibraryService.getSongById(eq(LOC), eq(3), eq(300))).thenReturn(songDto);

    List<SongDto> songs = userServiceImpl.getPlaylistSongs(REGISTERED_EMAIL, LOC,
        PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);

    assertEquals(List.of(songDto), songs);
  }

  @Test
  void reorderPlaylistSongs_keepsOnlyExistingSongsInGivenOrderAndPersists() throws Exception {

    PlaylistEntity playlist = account().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    SongIdentifier a = new SongIdentifier(LOC, 1, 1);
    SongIdentifier b = new SongIdentifier(LOC, 2, 2);
    playlist.addSong(a);
    playlist.addSong(b);

    userServiceImpl.reorderPlaylistSongs(REGISTERED_EMAIL, LOC,
        PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, List.of(b, a, new SongIdentifier(LOC, 9, 9)));

    assertEquals(List.of(b, a), playlist.getSongs());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void getFavoriteSongIdentifiers_returnsFavoritesPlaylistSongs() {

    SongIdentifier fav = new SongIdentifier(LOC, 4, 400);
    account().getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .addSong(fav);

    assertEquals(List.of(fav), userServiceImpl.getFavoriteSongIdentifiers(REGISTERED_EMAIL, LOC));
    assertTrue(userServiceImpl.getFavoriteSongIdentifiers(REGISTERED_EMAIL, Integer.valueOf(99))
        .isEmpty(), "favorites are per location");
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

    assertTrue(account().getSongPlayHistory().contains(new SongIdentifier(42, 5, 500)));
    assertEquals(4, credits()); // 6 - (normal play: fixed 1 * webCostMultiplier 2)
    verify(eventPublisher).publishEvent(any(UserCreditsChangedEvent.class));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void handleSongAddedToQueueEvent_withLocationId_tagsCreditTransactionWithLocation() {

    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(6, 600), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false),
        Integer.valueOf(42));

    UserSongCreditUsageEntity transaction = account().getSongCreditUsages().iterator().next();
    assertEquals(Integer.valueOf(42), transaction.getLocationId());
  }

  @Test
  void handleSongAddedToQueueEvent_chargesTheBalanceAtThatLocationOnly() {

    account(Integer.valueOf(99)).setNumCredits(10);
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(6, 600), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false),
        Integer.valueOf(99));

    assertEquals(8, registeredUser().getNumCredits(Integer.valueOf(99)));
    assertEquals(6, credits(), "the balance at another location is untouched");
    assertTrue(account(Integer.valueOf(99)).getSongPlayHistory()
        .contains(new SongIdentifier(99, 6, 600)));
    assertTrue(account().getSongPlayHistory().isEmpty());
    verify(eventPublisher).publishEvent(new UserCreditsChangedEvent(REGISTERED_EMAIL, 99, 8));
  }

  @Test
  void handleSongAddedToQueueEvent_chargesAPriorityPlayByTheLevelItJumps() {

    // A priority play queued at priority 2 jumped a priority-1 song, so it is priced at level 1.
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(7, 700), 2, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, true), LOC);

    // 6 - ((priority 2 - 1) * priorityCostMultiplier 2 * webCostMultiplier 2) = 2
    assertEquals(2, credits());
  }

  @Test
  void chargeCreditsForQueueAction_deductsCreditsBasedOnPriorityAndPersists() {

    when(songLibraryService.getOwnLocationId()).thenReturn(LOC); // standalone: own location
    account().setNumCredits(20);

    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 2);

    // cost = max(1, priority(2) * 3) * webCostMultiplier(2) = 12
    assertEquals(8, credits());
    assertEquals(-12, account().getSongCreditUsages().iterator().next().getAmount());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void chargeCreditsForQueueAction_whenBalanceFallsShort_recordsOnlyTheCreditsActuallyDeducted() {

    // A last line of defense only -- the controller refuses an unaffordable action up front. Each
    // recorded credit is paid out to the location, so the entry must match what the balance lost.
    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 2, Integer.valueOf(42));

    assertEquals(0, credits());
    UserSongCreditUsageEntity usage = account().getSongCreditUsages().iterator().next();
    assertEquals(-6, usage.getAmount(), "only the 6 credits held may be recorded, not the 12 cost");
    assertEquals(0, usage.getResultingBalance());
  }

  @Test
  void chargeCreditsForQueueAction_withNoBalance_recordsNoUsageAndAnnouncesNothing() {

    account().setNumCredits(0);

    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, Integer.valueOf(42));

    assertEquals(0, credits());
    assertTrue(account().getSongCreditUsages().isEmpty());
    verify(eventPublisher, never()).publishEvent(any(LocationSongCreditUsageRecordedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(UserCreditsChangedEvent.class));
  }

  @Test
  void handleSongAddedToQueueEvent_whenBalanceFallsShort_recordsOnlyTheCreditsActuallyDeducted() {

    account().setNumCredits(1);
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(5, 500), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false),
        Integer.valueOf(42));

    assertEquals(0, credits());
    assertEquals(-1, account().getSongCreditUsages().iterator().next().getAmount());
  }

  @Test
  void chargeCreditsForQueueAction_withLocationId_tagsCreditTransactionWithLocation() {

    account(Integer.valueOf(7)).setNumCredits(6);

    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, Integer.valueOf(7));

    UserSongCreditUsageEntity transaction =
        account(Integer.valueOf(7)).getSongCreditUsages().iterator().next();
    assertEquals(Integer.valueOf(7), transaction.getLocationId());
    assertEquals(6, credits(), "location 42's balance is untouched");
  }

  @Test
  void getCreditLedgerForLocation_returnsOnlyMatchingLocationWithinTimeRange() {

    account(Integer.valueOf(101)).setNumCredits(100);
    account(Integer.valueOf(102)).setNumCredits(100);
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

    // Standalone: the song was added to this instance's own queue, so the spend and play belong
    // to this instance's own location account.
    when(songLibraryService.getOwnLocationId()).thenReturn(Integer.valueOf(42));
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(5, 500), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false));

    UserSongCreditUsageEntity usage = account().getSongCreditUsages().iterator().next();
    assertEquals(Integer.valueOf(42), usage.getLocationId());
    assertEquals(-2, usage.getAmount());
    assertTrue(account().getSongPlayHistory().contains(new SongIdentifier(42, 5, 500)));
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

    // normal play: 1 * webCostMultiplier 2 = 2; priority 2 play: (2 - 1) * 2 * 2 = 4; balance 6
    userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false);
    userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 2, true);

    account().setNumCredits(2);
    userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false); // exactly enough

    assertEquals(2, credits(), "a check never charges");
    verify(userRepository, never()).storeAggregateRoot(any());
  }

  @Test
  void requireAffordableQueueAdd_refusesWhenTheBalanceFallsShort() {

    account().setNumCredits(3);

    // priority 2 play: (2 - 1) * priorityCostMultiplier 2 * webCostMultiplier 2 = 4
    InsufficientCreditsException e = assertThrows(InsufficientCreditsException.class,
        () -> userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 2, true));
    assertEquals(4, e.getCost());
    assertEquals(3, e.getBalance());

    account().setNumCredits(0);
    assertThrows(InsufficientCreditsException.class,
        () -> userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false));
  }

  @Test
  void requireAffordableQueueAdd_refusesAtALocationWhereThePatronHasNoCredits() {

    // 6 credits at location 42 buy nothing at location 99.
    InsufficientCreditsException e = assertThrows(InsufficientCreditsException.class,
        () -> userServiceImpl.requireAffordableQueueAdd(REGISTERED_EMAIL, 99, 1, false));
    assertEquals(0, e.getBalance());
    assertEquals(0, userServiceImpl.getAffordableQueueAddCount(REGISTERED_EMAIL, 99, 1, false));
  }

  @Test
  void requireAffordableQueueAdd_pricesAtTheLocationsOwnConfig() {

    when(pricingService.resolvePricingConfig(Integer.valueOf(43)))
        .thenReturn(new PricingConfig(2, 3, 3, 10, 5, false));
    account().setNumCredits(4);
    account(Integer.valueOf(43)).setNumCredits(4);

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
        "hash", UserRole.ROLE_ADMIN));

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
    account().setNumCredits(0);

    slaveUserService.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, true);
    slaveUserService.requireAffordableQueueAction(REGISTERED_EMAIL, 1, 42);
    slaveUserService.requireAffordableQueueAdd("not-on-this-slave@example.com", 42, 1, false);
    assertEquals(Integer.MAX_VALUE,
        slaveUserService.getAffordableQueueAddCount(REGISTERED_EMAIL, 42, 1, false));

    // Still never charged on a slave: credits are master's.
    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(5, 500), 1, null);
    slaveUserService.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false), 42);
    slaveUserService.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, 42);
    assertTrue(account().getSongCreditUsages().isEmpty());
  }

  @Test
  void allowSlaveUrlQueueOperations_isIgnoredOutsideSlaveMode() {

    UserServiceImpl standalone = new UserServiceImpl(userRepository, passwordEncoder, jwtUtil,
        eventPublisher, songLibraryService, pricingService, false, paymentGateway, true);
    account().setNumCredits(0);

    assertThrows(InsufficientCreditsException.class,
        () -> standalone.requireAffordableQueueAdd(REGISTERED_EMAIL, 42, 1, false));
  }

  @Test
  void getAffordableQueueAddCount_isTheBalanceDividedByTheCost() {

    account().setNumCredits(7);

    assertEquals(3, userServiceImpl.getAffordableQueueAddCount(REGISTERED_EMAIL, 42, 1, false));
    account().setNumCredits(1);
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

    // A fresh patron (the registered one already has an account at 42, the confirmed id).
    UserEntity user = new UserEntity(Integer.valueOf(2), "Pat", "Ron", "pat@example.com", "hash",
        UserRole.ROLE_USER);
    userRoot.addUser(user);

    UserLocationAccountEntity previous = user.getOrCreateLocationAccount(previousLocationId);
    previous.addSongToSongPlayHistory(new SongIdentifier(previousLocationId, 1, 2));
    PlaylistEntity favorites =
        previous.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    favorites.addSong(new SongIdentifier(previousLocationId, 5, 6));
    previous.setNumCredits(100);

    UserLocationAccountEntity other = user.getOrCreateLocationAccount(otherLocationId);
    other.addSongToSongPlayHistory(new SongIdentifier(otherLocationId, 3, 4));
    other.setNumCredits(100);

    userServiceImpl.chargeCreditsForQueueAction("pat@example.com", 1, previousLocationId);
    userServiceImpl.chargeCreditsForQueueAction("pat@example.com", 1, otherLocationId);
    clearInvocations(userRepository);

    userServiceImpl.handleOwnLocationIdChangedEvent(
        new OwnLocationIdChangedEvent(previousLocationId, confirmedLocationId));

    assertEquals(confirmedLocationId, previous.getLocationId());
    assertEquals(List.of(new SongIdentifier(confirmedLocationId, 1, 2)),
        previous.getSongPlayHistory());
    assertEquals(List.of(new SongIdentifier(confirmedLocationId, 5, 6)), favorites.getSongs());
    assertEquals(confirmedLocationId,
        previous.getSongCreditUsages().iterator().next().getLocationId());

    assertEquals(otherLocationId, other.getLocationId());
    assertEquals(List.of(new SongIdentifier(otherLocationId, 3, 4)), other.getSongPlayHistory());
    assertEquals(otherLocationId, other.getSongCreditUsages().iterator().next().getLocationId());

    verify(userRepository).storeAggregateRoot(userRoot);
  }
}
