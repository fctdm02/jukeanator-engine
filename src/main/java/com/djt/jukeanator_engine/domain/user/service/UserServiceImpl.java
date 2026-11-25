package com.djt.jukeanator_engine.domain.user.service;

import static java.util.Objects.requireNonNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.InvalidPrincipalException;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.security.LocalPrincipal;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsResponseDto;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.dto.HomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.LocationBalanceDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.PlaylistSummaryDto;
import com.djt.jukeanator_engine.domain.user.dto.PricingConfigDto;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserHomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;
import com.djt.jukeanator_engine.domain.user.event.LocationSongCreditUsageRecordedEvent;
import com.djt.jukeanator_engine.domain.user.event.PurchaseCompletedEvent;
import com.djt.jukeanator_engine.domain.user.event.RecentPlayAddedEvent;
import com.djt.jukeanator_engine.domain.user.event.UserCreditsChangedEvent;
import com.djt.jukeanator_engine.domain.user.event.VerificationEmailRequestedEvent;
import com.djt.jukeanator_engine.domain.user.exception.AccountNotVerifiedException;
import com.djt.jukeanator_engine.domain.user.exception.EmailAlreadyRegisteredException;
import com.djt.jukeanator_engine.domain.user.exception.IncorrectPasswordException;
import com.djt.jukeanator_engine.domain.user.exception.InsufficientCreditsException;
import com.djt.jukeanator_engine.domain.user.exception.InvalidCredentialsException;
import com.djt.jukeanator_engine.domain.user.exception.PaymentException;
import com.djt.jukeanator_engine.domain.user.exception.QueueAccessDeniedException;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserAddFundsTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserLocationAccountEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageType;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;

/**
 * @author tmyers
 */
public class UserServiceImpl implements UserService, AggregateRootService<UserRootEntity> {

  private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

  private static final int MAX_RECENT_PLAYS = 10;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final ApplicationEventPublisher eventPublisher;
  private final SongLibraryService songLibraryService;
  private final PricingService pricingService;
  private final boolean slaveMode;
  private final PaymentGateway paymentGateway;
  private final boolean allowSlaveUrlQueueOperations;
  private final EmailVerificationPolicy emailVerificationPolicy;
  private final SecureRandom secureRandom = new SecureRandom();

  // See setFinancialLedgerServiceSupplier() / setLocationService().
  private volatile Supplier<FinancialLedgerService> financialLedgerServiceSupplier = () -> null;
  private volatile LocationService locationService;

  private UserRootEntity userRoot;

  public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil, ApplicationEventPublisher eventPublisher,
      SongLibraryService songLibraryService, PricingService pricingService, boolean slaveMode,
      PaymentGateway paymentGateway) {
    this(userRepository, passwordEncoder, jwtUtil, eventPublisher, songLibraryService,
        pricingService, slaveMode, paymentGateway, false);
  }

  /**
   * @param allowSlaveUrlQueueOperations {@code app.allow-slave-url-queue-operations}: local/testing
   *        only -- lets a slave queue for any web user on its own endpoints (uncharged), instead of
   *        refusing all but admins. Ignored unless {@code slaveMode}.
   */
  public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil, ApplicationEventPublisher eventPublisher,
      SongLibraryService songLibraryService, PricingService pricingService, boolean slaveMode,
      PaymentGateway paymentGateway, boolean allowSlaveUrlQueueOperations) {
    this(userRepository, passwordEncoder, jwtUtil, eventPublisher, songLibraryService,
        pricingService, slaveMode, paymentGateway, allowSlaveUrlQueueOperations,
        EmailVerificationPolicy.DISABLED);
  }

  /**
   * @param emailVerificationPolicy whether (and how) a Web/Mobile UI registration must verify its
   *        email address before it can log in
   */
  public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil, ApplicationEventPublisher eventPublisher,
      SongLibraryService songLibraryService, PricingService pricingService, boolean slaveMode,
      PaymentGateway paymentGateway, boolean allowSlaveUrlQueueOperations,
      EmailVerificationPolicy emailVerificationPolicy) {

    requireNonNull(userRepository, "userRepository cannot be null");
    requireNonNull(passwordEncoder, "passwordEncoder cannot be null");
    requireNonNull(jwtUtil, "jwtUtil cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    requireNonNull(pricingService, "pricingService cannot be null");
    requireNonNull(paymentGateway, "paymentGateway cannot be null");
    requireNonNull(emailVerificationPolicy, "emailVerificationPolicy cannot be null");

    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.eventPublisher = eventPublisher;
    this.songLibraryService = songLibraryService;
    this.pricingService = pricingService;
    this.slaveMode = slaveMode;
    this.paymentGateway = paymentGateway;
    this.allowSlaveUrlQueueOperations = allowSlaveUrlQueueOperations;
    this.emailVerificationPolicy = emailVerificationPolicy;
    if (slaveMode && allowSlaveUrlQueueOperations) {
      log.warn("app.allow-slave-url-queue-operations is true: web users can queue songs on this "
          + "slave's own endpoints without being charged -- for local/testing use only");
    }

    initialize();

    log.info("Using user root: " + this.userRoot);
  }

  // Service methods
  @Override
  public AuthResponse register(RegisterRequest request) {
    return register(request, null);
  }

  @Override
  public AuthResponse register(RegisterRequest request, String requestBaseUrl) {
    return registerWithRole(request, UserRole.ROLE_USER, emailVerificationPolicy.required(),
        requestBaseUrl);
  }

  @Override
  public AuthResponse addAdminUser(RegisterRequest request) {
    // Created by an operator already logged into the Swing admin panel: never PENDING.
    return registerWithRole(request, UserRole.ROLE_ADMIN, false, null);
  }

  /**
   * Validates the request against {@link AccountValidator}'s rules before creating the account.
   * With {@code requireVerification}, the account is created PENDING, its verification link is
   * emailed, and the returned {@link AuthResponse} carries no token.
   */
  private synchronized AuthResponse registerWithRole(RegisterRequest request, UserRole role,
      boolean requireVerification, String requestBaseUrl) {

    String emailAddress = AccountValidator.requireValidEmail(request.emailAddress());
    if (emailAddress.endsWith(UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN)) {
      throw new IllegalArgumentException("Please enter a valid email address.");
    }
    String firstName = AccountValidator.requireValidName(request.firstName(), "first name");
    String lastName = AccountValidator.requireValidName(request.lastName(), "last name");
    AccountValidator.requireValidPassword(request.password());

    UserEntity existing = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (existing != null && existing.isPending()) {
      throw new EmailAlreadyRegisteredException("An account for " + emailAddress
          + " is waiting for email verification. Please use the link we emailed you, or sign in "
          + "to have a new link sent.");
    }
    if (existing != null) {
      throw new EmailAlreadyRegisteredException(
          "An account already exists for " + emailAddress + ". Please sign in instead.");
    }

    if (!requireVerification) {
      UserEntity user = createUser(firstName, lastName, emailAddress, request.password(), role);
      String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole().name());
      return new AuthResponse(token, user.getEmailAddress(), user.getRole().name());
    }

    String baseUrl = resolveVerificationBaseUrl(requestBaseUrl);
    UserEntity user = newUser(firstName, lastName, emailAddress, request.password(), role);
    String rawToken = issueVerificationToken(user);
    this.userRoot.addUser(user);
    this.userRepository.storeAggregateRoot(this.userRoot);
    publishVerificationEmail(user, baseUrl, rawToken);
    return new AuthResponse(null, user.getEmailAddress(), user.getRole().name());
  }

  @Override
  public synchronized VerificationResult verifyAccount(String token) {

    if (token == null || token.isBlank()) {
      return VerificationResult.INVALID;
    }
    String tokenHash = sha256Hex(token);
    UserEntity user = userRoot.getUsers().stream()
        .filter(u -> tokenHash.equals(u.getVerificationTokenHash()))
        .findFirst()
        .orElse(null);
    if (user == null || !user.isPending()) {
      return VerificationResult.INVALID;
    }
    if (user.getVerificationTokenExpiresAt() == null
        || Instant.now().isAfter(user.getVerificationTokenExpiresAt())) {
      return VerificationResult.EXPIRED;
    }

    user.markVerified();
    this.userRepository.storeAggregateRoot(this.userRoot);
    log.info("Verified account {}", user.getEmailAddress());
    return VerificationResult.VERIFIED;
  }

  @Override
  public synchronized void resendVerification(String emailAddress, String requestBaseUrl) {

    if (emailAddress == null) {
      return;
    }
    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(
        AccountValidator.normalizeEmail(emailAddress));
    // Deliberately silent otherwise, so the endpoint never reveals whether an email is registered.
    if (user == null || !user.isPending()) {
      return;
    }

    String baseUrl = resolveVerificationBaseUrl(requestBaseUrl);
    String rawToken = issueVerificationToken(user);
    this.userRepository.storeAggregateRoot(this.userRoot);
    publishVerificationEmail(user, baseUrl, rawToken);
  }

  /** Replaces any outstanding token on {@code user}; returns the raw token for the email link. */
  private String issueVerificationToken(UserEntity user) {

    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    user.issueVerificationToken(sha256Hex(rawToken),
        Instant.now().plus(emailVerificationPolicy.tokenTtl()));
    return rawToken;
  }

  private void publishVerificationEmail(UserEntity user, String baseUrl, String rawToken) {

    String verificationUrl = baseUrl + "/api/users/verify?token=" + rawToken;
    eventPublisher.publishEvent(new VerificationEmailRequestedEvent(user.getEmailAddress(),
        user.getFirstName(), verificationUrl, user.getVerificationTokenExpiresAt()));
  }

  /** {@code email.verification-base-url} when set, else the URL the request arrived on. */
  private String resolveVerificationBaseUrl(String requestBaseUrl) {

    String baseUrl = emailVerificationPolicy.baseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = requestBaseUrl;
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException(
          "No base URL for the verification link: set email.verification-base-url");
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e); // guaranteed by every JRE
    }
  }

  /**
   * Creates and stores an account, unvalidated -- callers validate (or, for the kiosk's own
   * internal {@code LOCAL_USERNAME} account, deliberately do not).
   */
  private UserEntity createUser(String firstName, String lastName, String emailAddress,
      String password, UserRole role) {

    UserEntity user = newUser(firstName, lastName, emailAddress, password, role);
    this.userRoot.addUser(user);
    this.userRepository.storeAggregateRoot(this.userRoot);
    return user;
  }

  /** An ACTIVE, not-yet-stored account. */
  private UserEntity newUser(String firstName, String lastName, String emailAddress,
      String password, UserRole role) {

    Integer persistentIdentity = this.userRoot.nextUserPersistentIdentity();

    // A new account has no location accounts, so no credits anywhere: each location account (with
    // its own My Favorites playlist) is created the first time the patron uses that location, and
    // every credit spent there must have been bought there through Add Funds.
    return new UserEntity(persistentIdentity, firstName, lastName, emailAddress,
        passwordEncoder.encode(password), role);
  }

  @Override
  public synchronized AuthResponse login(LoginRequest request) throws InvalidCredentialsException {

    if (request.emailAddress() == null || request.password() == null) {
      throw new InvalidCredentialsException("Invalid credentials");
    }
    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(
        AccountValidator.normalizeEmail(request.emailAddress()));
    if (user == null || user.isClosed()) {
      throw new InvalidCredentialsException("Invalid credentials");
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException("Invalid credentials");
    }
    // Checked only after the password, so this never reveals that an email is registered.
    if (user.isPending()) {
      throw new AccountNotVerifiedException("Please verify your email address before signing in. "
          + "Check your inbox for the link we sent to " + user.getEmailAddress() + ".");
    }

    String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole().name());
    return new AuthResponse(token, user.getEmailAddress(), user.getRole().name());
  }

  @Override
  public synchronized UserProfileDto getProfile(String emailAddress) {

    UserEntity user = requireUser(emailAddress);

    // Credits are per location: one balance for every location the patron has used. No dollar
    // balance -- web credits are bought at package prices and spent at each location's
    // web-cost-multiplier, so no single dollar rate is right (see docs/pricing.md).
    List<LocationBalanceDto> balances = new ArrayList<>();
    for (UserLocationAccountEntity account : user.getLocationAccounts()) {
      balances.add(new LocationBalanceDto(account.getLocationId(),
          locationNameOf(account.getLocationId()), account.getNumCredits()));
    }
    return new UserProfileDto(user.getPersistentIdentity(), user.getFirstName(), user.getLastName(),
        user.getEmailAddress(), balances);
  }

  @Override
  public synchronized int getNumCredits(String emailAddress, Integer locationId) {
    return requireUser(emailAddress).getNumCredits(resolveLocationId(locationId));
  }

  private static final int MAX_HOT_HERE = 10;

  @Override
  public synchronized HomePageDto getPublicHomePage(Integer locationId) {
    var popular = songLibraryService.getMusicByPopularity(locationId);
    var artists = popular.artists().stream().limit(MAX_HOT_HERE).toList();
    var albums = popular.albums().stream().limit(MAX_HOT_HERE).toList();
    var songs = popular.songs().stream().limit(MAX_HOT_HERE).toList();
    return new HomePageDto(artists, albums, songs);
  }

  @Override
  public synchronized UserHomePageDto getHomePage(String emailAddress, Integer locationId) {

    UserEntity user = requireUser(emailAddress);
    Integer resolvedLocationId = resolveLocationIdOrNull(locationId);
    if (resolvedLocationId == null) {
      // Master with no location selected yet: nothing location-specific to show.
      return new UserHomePageDto(List.of(), List.of(), List.of(), List.of(), List.of(),
          user.getSearchHistory(), 0);
    }
    UserLocationAccountEntity account = accountFor(user, resolvedLocationId);

    List<String> playlistNames = new ArrayList<>();
    for (PlaylistEntity playlist : account.getPlaylists()) {
      playlistNames.add(playlist.getName());
    }

    // Newest first, one entry per song; every entry is from this location's library.
    List<SongIdentifier> history = account.getSongPlayHistory();
    List<SongDto> recentPlays = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = history.size() - 1; i >= 0 && recentPlays.size() < MAX_RECENT_PLAYS; i--) {
      SongIdentifier id = history.get(i);
      if (!seen.add(id.getAlbumId() + ":" + id.getSongId())) {
        continue;
      }
      try {
        SongDto song =
            songLibraryService.getSongById(resolvedLocationId, id.getAlbumId(), id.getSongId());
        if (song != null)
          recentPlays.add(song);
      } catch (Exception e) {
        // song may have been removed from the library; skip it
      }
    }

    HomePageDto publicHomePageDto = getPublicHomePage(resolvedLocationId);

    return new UserHomePageDto(recentPlays, playlistNames, publicHomePageDto.artistsHotHere(),
        publicHomePageDto.albumsHotHere(), publicHomePageDto.songsHotHere(),
        user.getSearchHistory(), account.getNumCredits());
  }

  @Override
  public List<CreditPackageDto> getCreditPackages(Integer locationId) {
    return pricingService.resolveCreditPackages(resolveLocationId(locationId));
  }

  @Override
  public PricingConfigDto getPricingConfig(Integer locationId) {

    PricingConfig config = pricingService.resolvePricingConfig(locationId);
    return new PricingConfigDto(config.priorityCostMultiplier(), config.creditsPerDollar(),
        config.fiveDollarBonusCredits(), config.tenDollarBonusCredits(),
        config.webCostMultiplier(), config.displayCurrencyForCost());
  }

  @Override
  public synchronized void changePassword(String emailAddress, ChangePasswordRequest request) {

    UserEntity user = requireUser(emailAddress);

    if (request.currentPassword() == null
        || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new IncorrectPasswordException("Your current password is incorrect.");
    }
    AccountValidator.requireValidPassword(request.newPassword());

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public synchronized void deleteAccount(String emailAddress) {

    requireUser(emailAddress);

    // Closed, never removed: the account's Add Funds transactions and song-credit usages are the
    // record that Braintree's charges and every location's mobile revenue are reconciled against.
    String closedEmailAddress =
        "closed-" + UUID.randomUUID() + UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN;
    userRoot.closeUser(emailAddress, closedEmailAddress,
        passwordEncoder.encode(UUID.randomUUID().toString()));
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  /**
   * Buys a credit package for one location: the credits land on the patron's account at
   * {@code request.locationId()} and are usable only there, and the whole purchase is recorded
   * once in that location's financial ledger -- like a swipe on its own card reader.
   */
  @Override
  public synchronized AddFundsResponseDto addFunds(String emailAddress, AddFundsRequest request) {

    UserEntity user = requireUser(emailAddress);

    // The location the credits are for -- this instance's own when omitted (standalone/slave).
    Integer locationId = resolveLocationIdOrNull(request.locationId());
    if (locationId == null) {
      throw new PaymentException("Please select a location before adding funds.");
    }
    if (locationService != null && appIsMaster()
        && locationService.getLocationByIdNullIfNotExists(locationId) == null) {
      throw new PaymentException("Unknown location: " + locationId);
    }

    CreditPackageDto pkg = getCreditPackages(locationId).stream()
        .filter(p -> p.id().equals(request.packageId()))
        .findFirst()
        .orElseThrow(() -> new PaymentException("Unknown package: " + request.packageId()));

    if (request.paymentMethodNonce() == null || request.paymentMethodNonce().isBlank()) {
      throw new PaymentException("Missing payment method");
    }

    PaymentChargeResult chargeResult =
        paymentGateway.charge(pkg.priceUsd(), request.paymentMethodNonce());
    if (!chargeResult.success()) {
      throw new PaymentException(chargeResult.failureMessage());
    }

    Instant now = Instant.now();

    boolean newAccount = user.getLocationAccountNullIfNotExists(locationId) == null;
    UserLocationAccountEntity account = user.getOrCreateLocationAccount(locationId);
    int previousBalance = account.getNumCredits();
    UserAddFundsTransactionEntity transaction =
        recordAddFundsTransaction(account, pkg, chargeResult, now);
    try {
      this.userRepository.storeAggregateRoot(this.userRoot);
      // Last, so a failure here only has the in-memory credit and the charge to undo.
      requireFinancialLedgerService().recordMobilePurchase(locationId, pkg.priceUsd(),
          emailAddress, chargeResult.transactionId(), pkg.id(), pkg.credits(),
          pkg.bonusCredits());
    } catch (RuntimeException e) {
      // The card was charged but the purchase could not be fully recorded -- undo it all, so the
      // customer is never billed for credits they did not receive, and the location's ledger
      // never shows revenue for a cancelled charge.
      account.setNumCredits(previousBalance);
      account.removeUnstoredAddFundsTransaction(transaction);
      if (newAccount) {
        user.getLocationAccounts().remove(account);
      }
      try {
        this.userRepository.storeAggregateRoot(this.userRoot);
      } catch (RuntimeException storeFailure) {
        log.error("Could not store the rolled-back Add Funds for " + emailAddress, storeFailure);
      }
      boolean voided = paymentGateway.voidCharge(chargeResult.transactionId());
      log.error("Add Funds for " + emailAddress + " at location " + locationId
          + " could not be recorded; payment transaction " + chargeResult.transactionId()
          + (voided ? " was voided" : " could NOT be voided"), e);
      throw new PaymentException(voided
          ? "Your purchase could not be completed and your payment was cancelled. Please try again."
          : "Your purchase could not be completed. Please contact support to be refunded.", e);
    }

    String locationName = locationNameOf(locationId);
    eventPublisher.publishEvent(
        new UserCreditsChangedEvent(emailAddress, locationId, account.getNumCredits()));
    eventPublisher.publishEvent(new PurchaseCompletedEvent(emailAddress, user.getFirstName(),
        pkg.credits(), pkg.bonusCredits(), pkg.priceUsd(), chargeResult.paymentSource(),
        chargeResult.transactionId(), now, account.getNumCredits(), locationId, locationName));

    return new AddFundsResponseDto(account.getNumCredits(), pkg.credits(), pkg.bonusCredits(),
        chargeResult.paymentSource(), chargeResult.transactionId(), now, locationId,
        locationName);
  }

  @Override
  public String generatePaymentClientToken() {
    return paymentGateway.generateClientToken();
  }

  @Override
  public synchronized UserProfileDto updateProfile(String emailAddress, UpdateProfileRequest request) {

    UserEntity user = requireUser(emailAddress);

    // Validate both before changing either, so a refused update changes nothing.
    String firstName = request.firstName() != null
        ? AccountValidator.requireValidName(request.firstName(), "first name") : null;
    String lastName = request.lastName() != null
        ? AccountValidator.requireValidName(request.lastName(), "last name") : null;
    if (firstName != null)
      user.setFirstName(firstName);
    if (lastName != null)
      user.setLastName(lastName);

    this.userRepository.storeAggregateRoot(this.userRoot);
    return getProfile(emailAddress);
  }

  @Override
  public synchronized List<String> getSearchHistory(String emailAddress) {
    return requireUser(emailAddress).getSearchHistory();
  }

  @Override
  public synchronized void addSearchHistory(String emailAddress, String query) {

    requireUser(emailAddress).addToSearchHistory(query, 10);
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public synchronized void removeSearchHistory(String emailAddress, int index) {

    requireUser(emailAddress).removeFromSearchHistory(index);
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public synchronized boolean createPlaylist(String emailAddress, Integer locationId,
      String playlistName) throws EntityAlreadyExistsException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);
    account.createPlaylist(playlistName);
    this.userRepository.storeAggregateRoot(this.userRoot);
    return true;
  }

  @Override
  public synchronized boolean addSongToPlaylist(String emailAddress, String playlistName,
      Integer locationId, SongFileEntity song) throws EntityDoesNotExistException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);
    boolean result = account.addSongToPlaylist(playlistName, song);
    this.userRepository.storeAggregateRoot(this.userRoot);
    return result;
  }

  @Override
  public synchronized boolean removeSongFromPlaylist(String emailAddress, String playlistName,
      Integer locationId, SongFileEntity song) throws EntityDoesNotExistException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);
    boolean result = account.removeSongFromPlaylist(playlistName, song);
    this.userRepository.storeAggregateRoot(this.userRoot);
    return result;
  }

  @Override
  public synchronized boolean deletePlaylist(String emailAddress, Integer locationId,
      String playlistName) throws EntityDoesNotExistException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);
    boolean result = account.deletePlaylist(playlistName);
    this.userRepository.storeAggregateRoot(this.userRoot);
    return result;
  }

  @Override
  public synchronized boolean renamePlaylist(String emailAddress, Integer locationId,
      String playlistName, String newPlaylistName)
      throws EntityDoesNotExistException, EntityAlreadyExistsException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);
    boolean result = account.renamePlaylist(playlistName, newPlaylistName);
    if (result) {
      this.userRepository.storeAggregateRoot(this.userRoot);
    }
    return result;
  }

  @Override
  public synchronized boolean addSongToMyFavoritesPlaylist(String emailAddress, Integer locationId,
      SongFileEntity song) throws EntityDoesNotExistException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);
    account.ensureMyFavoritesPlaylist();
    boolean result = account.addSongToPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, song);
    this.userRepository.storeAggregateRoot(this.userRoot);
    return result;
  }

  @Override
  public synchronized boolean removeSongFromMyFavoritesPlaylist(String emailAddress,
      Integer locationId, SongFileEntity song) throws EntityDoesNotExistException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);
    account.ensureMyFavoritesPlaylist();
    boolean result =
        account.removeSongFromPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, song);
    this.userRepository.storeAggregateRoot(this.userRoot);
    return result;
  }

  @Override
  public synchronized List<PlaylistSummaryDto> getPlaylists(String emailAddress,
      Integer locationId) {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);

    List<PlaylistSummaryDto> result = new ArrayList<>();
    for (PlaylistEntity p : account.getPlaylists()) {
      List<SongIdentifier> songs = p.getSongs();
      Integer firstSongAlbumId = songs.isEmpty() ? null : songs.get(0).getAlbumId();
      result.add(new PlaylistSummaryDto(p.getName(), songs.size(), firstSongAlbumId));
    }
    return result;
  }

  @Override
  public synchronized List<SongDto> getPlaylistSongs(String emailAddress, Integer locationId,
      String playlistName) throws EntityDoesNotExistException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);

    PlaylistEntity playlist = account.getPlaylistByName(playlistName);
    List<SongDto> result = new ArrayList<>();
    for (SongIdentifier si : playlist.getSongs()) {
      try {
        SongDto song = songLibraryService.getSongById(account.getLocationId(), si.getAlbumId(),
            si.getSongId());
        if (song != null) {
          result.add(song);
        }
      } catch (Exception e) {
        log.warn("Skipping missing song albumId={} songId={} in playlist '{}'", si.getAlbumId(),
            si.getSongId(), playlistName);
      }
    }
    return result;
  }

  /**
   * {@code locationId}, or this instance's own location when it is omitted (standalone/slave).
   *
   * @throws IllegalArgumentException when neither is known (master with no location selected)
   */
  private Integer resolveLocationId(Integer locationId) {

    Integer resolved = resolveLocationIdOrNull(locationId);
    if (resolved == null) {
      throw new IllegalArgumentException("Please select a location first.");
    }
    return resolved;
  }

  private Integer resolveLocationIdOrNull(Integer locationId) {
    return locationId != null ? locationId : songLibraryService.getOwnLocationId();
  }

  /**
   * {@code user}'s account at {@code locationId} (see {@link #resolveLocationId}), created -- and
   * stored -- on first use, and repaired by {@link UserLocationAccountEntity#ensureMyFavoritesPlaylist}.
   */
  private UserLocationAccountEntity accountFor(UserEntity user, Integer locationId) {

    Integer resolved = resolveLocationId(locationId);
    boolean created = user.getLocationAccountNullIfNotExists(resolved) == null;
    UserLocationAccountEntity account = user.getOrCreateLocationAccount(resolved);
    if (account.ensureMyFavoritesPlaylist() || created) {
      this.userRepository.storeAggregateRoot(this.userRoot);
    }
    return account;
  }

  @Override
  public synchronized void reorderPlaylistSongs(String emailAddress, Integer locationId,
      String playlistName, List<SongIdentifier> songs) throws EntityDoesNotExistException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);

    PlaylistEntity playlist = account.getPlaylistByName(playlistName);
    // Match each requested song against the stored entries and keep the stored identifier, so
    // a client that omits (or mis-tags) locationId can never strip or blank out the entry's
    // locationId.
    List<SongIdentifier> current = new ArrayList<>(playlist.getSongs());
    List<SongIdentifier> reordered = new ArrayList<>();
    for (SongIdentifier si : songs) {
      for (SongIdentifier stored : current) {
        if (Objects.equals(si.getAlbumId(), stored.getAlbumId())
            && Objects.equals(si.getSongId(), stored.getSongId())) {
          reordered.add(stored);
          current.remove(stored);
          break;
        }
      }
    }
    playlist.setSongs(reordered);
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public synchronized List<SongIdentifier> getFavoriteSongIdentifiers(String emailAddress,
      Integer locationId) {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);
    PlaylistEntity favs =
        account.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    return new ArrayList<>(favs.getSongs());
  }

  @Override
  public synchronized List<SongIdentifier> getPlaylistSongIdentifiers(String emailAddress,
      Integer locationId, String playlistName) throws EntityDoesNotExistException {

    UserLocationAccountEntity account = accountFor(requireUser(emailAddress), locationId);
    return new ArrayList<>(account.getPlaylistByName(playlistName).getSongs());
  }

  /**
   * Re-tags every user's location account (and everything in it) from the previous own location
   * id to the confirmed one, then persists them -- under JPA the rows were already re-pointed by
   * {@code LocationRepositoryJpaImpl.changeLocationId}, so without this the next store would write
   * the previous id back over them.
   */
  @EventListener
  @Override
  public synchronized void handleOwnLocationIdChangedEvent(OwnLocationIdChangedEvent event) {

    this.userRoot.changeLocationId(event.previousLocationId(), event.confirmedLocationId());
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  /**
   * A song this instance added to its own queue -- tagged with the own location id. On master,
   * which has no queue of its own, this never fires for a web user.
   */
  @EventListener
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {
    handleSongAddedToQueueEvent(event, songLibraryService.getOwnLocationId());
  }

  @Override
  public synchronized void handleSongAddedToQueueEvent(SongAddedToQueueEvent event, Integer locationId) {

    String username = event.queueEntry().username();

    // In slave mode, a remotely-dispatched (master-relayed) command's addSongToQueue fires this
    // exact same local event via SongQueueServiceImpl's own event publish — but the web/mobile
    // user is never registered on the slave's own user store, since credits/identity are entirely
    // master-owned once a location is in slave mode. Master charges credits explicitly via the
    // locationId-aware overload after the command succeeds (see SongQueueController), so this
    // would otherwise double-charge (or throw "user not found" and cancel the whole add). A
    // genuine local walk-up action (LOCAL_USERNAME) is untouched.
    if (slaveMode && !LocalPrincipal.LOCAL_USERNAME.equals(username)) {
      return;
    }

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(username);
    if (user == null && LocalPrincipal.LOCAL_USERNAME.equals(username)) {

      // The kiosk's own internal account: never signed into, so not held to a patron's
      // email/password rules.
      user = createUser("Local", "User", username, "password", UserRole.ROLE_USER);
    }

    if (user == null) {
      throw new UserServiceException("User not found: " + username);
    }
    if (locationId == null) {
      log.warn("Song queued for {} with no location; not recorded in play history", username);
      return;
    }

    UserLocationAccountEntity account = user.getOrCreateLocationAccount(locationId);
    SongDto song = event.queueEntry().song();
    account.addSongToSongPlayHistory(
        new SongIdentifier(locationId, song.albumId(), song.songId()));

    // Deduct Web UI credits for non-local (web) users -- from their balance at this location
    // only. See CreditCostCalculator for the swingCost * webCostMultiplier formula.
    UserSongCreditUsageEntity usage = null;
    if (!LocalPrincipal.LOCAL_USERNAME.equals(username)) {
      int priority =
          event.queueEntry().priority() != null ? event.queueEntry().priority() : 1;
      int cost = CreditCostCalculator.webQueueAddCost(pricingService.resolvePricingConfig(locationId),
          priority, event.priorityPlay());
      usage = deductCredits(account, username, cost, UserSongCreditUsageType.QUEUE_ADD,
          song.albumId(), song.songId());
    }

    this.userRepository.storeAggregateRoot(this.userRoot);
    if (usage != null) {
      announceLocationCreditUsage(usage);
    }
    // Every web user's queued song -- single or from a playlist, here or forwarded to a slave --
    // passes through here, so this is the one place the live Recent Plays row is fed from.
    if (!LocalPrincipal.LOCAL_USERNAME.equals(username)) {
      eventPublisher.publishEvent(new RecentPlayAddedEvent(username, locationId, song));
    }
  }

  @Override
  public void chargeCreditsForQueueAction(String emailAddress, Integer priority) {
    chargeCreditsForQueueAction(emailAddress, priority, null);
  }

  @Override
  public synchronized void chargeCreditsForQueueAction(String emailAddress, Integer priority, Integer locationId) {

    // Same rationale as handleSongAddedToQueueEvent above — in slave mode this is only ever
    // reachable via a direct hit on the slave's own (untouched) endpoints, never via the
    // locationId-aware path (that only runs on master, which is never slaveMode).
    if (slaveMode) {
      return;
    }

    UserEntity user = requireUser(emailAddress);
    UserLocationAccountEntity account = user.getOrCreateLocationAccount(resolveLocationId(locationId));

    int cost = queueActionCost(priority, account.getLocationId());
    UserSongCreditUsageEntity usage = deductCredits(account, emailAddress, cost,
        UserSongCreditUsageType.QUEUE_ACTION, null, null);

    this.userRepository.storeAggregateRoot(this.userRoot);
    if (usage != null) {
      announceLocationCreditUsage(usage);
    }
  }

  @Override
  public synchronized void requireAffordableQueueAdd(String emailAddress, Integer locationId,
      int priority, boolean priorityPlay) throws InsufficientCreditsException {

    if (slaveMode) {
      requireSlaveQueueAccess(emailAddress);
      return;
    }
    Integer resolved = resolveLocationId(locationId);
    requireAffordable(requireUser(emailAddress), resolved, CreditCostCalculator
        .webQueueAddCost(pricingService.resolvePricingConfig(resolved), priority, priorityPlay));
  }

  @Override
  public synchronized void requireAffordableQueueAction(String emailAddress, Integer priority,
      Integer locationId) throws InsufficientCreditsException {

    if (slaveMode) {
      requireSlaveQueueAccess(emailAddress);
      return;
    }
    Integer resolved = resolveLocationId(locationId);
    requireAffordable(requireUser(emailAddress), resolved, queueActionCost(priority, resolved));
  }

  /**
   * A slave never charges a web user (credits are master-owned, see handleSongAddedToQueueEvent),
   * so a patron queueing directly on the slave would play for free -- only an admin may, unless
   * {@code app.allow-slave-url-queue-operations} (local/testing only) lets everyone through.
   */
  private void requireSlaveQueueAccess(String emailAddress) {

    if (allowSlaveUrlQueueOperations) {
      return;
    }
    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null || user.getRole() != UserRole.ROLE_ADMIN) {
      throw new QueueAccessDeniedException("Songs can't be queued from this jukebox's own web "
          + "page. Please use the JukeANator website or app to queue songs here.");
    }
  }

  private int queueActionCost(Integer priority, Integer locationId) {
    return CreditCostCalculator.webQueueActionCost(pricingService.resolvePricingConfig(locationId),
        priority != null ? priority : 1);
  }

  private UserEntity requireUser(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }
    return user;
  }

  private static void requireAffordable(UserEntity user, Integer locationId, int cost) {

    int balance = user.getNumCredits(locationId);
    if (balance < cost) {
      throw new InsufficientCreditsException(cost, balance);
    }
  }

  @Override
  public synchronized int getAffordableQueueAddCount(String emailAddress, Integer locationId,
      int priority, boolean priorityPlay) {

    // Mirrors handleSongAddedToQueueEvent, which never charges a web user in slave mode -- so only
    // an admin may queue there at all.
    if (slaveMode) {
      requireSlaveQueueAccess(emailAddress);
      return Integer.MAX_VALUE;
    }

    UserEntity user = requireUser(emailAddress);
    Integer resolved = resolveLocationId(locationId);

    int cost = CreditCostCalculator.webQueueAddCost(
        pricingService.resolvePricingConfig(resolved), priority, priorityPlay);
    if (cost <= 0) {
      return Integer.MAX_VALUE;
    }
    return Math.max(0, user.getNumCredits(resolved)) / cost;
  }

  @Override
  public synchronized List<UserSongCreditUsageDto> getCreditLedgerForLocation(Integer locationId, Instant from,
      Instant to) {

    return userRoot.getUsers().stream()
        .map(u -> u.getLocationAccountNullIfNotExists(locationId))
        .filter(Objects::nonNull)
        .flatMap(account -> account.getSongCreditUsages().stream())
        .filter(t -> !t.getTimestamp().isBefore(from) && !t.getTimestamp().isAfter(to))
        .sorted(java.util.Comparator.comparing(UserSongCreditUsageEntity::getTimestamp))
        .map(UserServiceImpl::toDto)
        .toList();
  }

  private static UserSongCreditUsageDto toDto(UserSongCreditUsageEntity usage) {
    return new UserSongCreditUsageDto(usage.getUserEmail(), usage.getLocationId(),
        usage.getAmount(), usage.getType(), usage.getTimestamp(), usage.getSongAlbumId(),
        usage.getSongId(), usage.getResultingBalance(), usage.getSyncId());
  }

  /**
   * Deducts {@code cost} credits (floored at zero) from the patron's balance at the account's
   * location, broadcasts the new balance, and appends a spend record to the account. The spend is
   * an audit record only -- the location's revenue was recorded in full when the credits were
   * bought. Returns {@code null}, recording nothing, when nothing was deducted. Callers refuse an
   * unaffordable action up front (see {@link #requireAffordable}), so the floor only matters as a
   * last line of defense. Callers persist the user root afterward, then pass a non-null entry to
   * {@link #announceLocationCreditUsage}.
   */
  private UserSongCreditUsageEntity deductCredits(UserLocationAccountEntity account,
      String emailAddress, int cost, UserSongCreditUsageType type, Integer songAlbumId,
      Integer songId) {

    int balance = account.getNumCredits();
    int deducted = Math.max(0, Math.min(cost, balance));
    if (deducted < cost) {
      log.warn("Charged " + emailAddress + " " + deducted + " of " + cost
          + " credits at location " + account.getLocationId()
          + " -- the balance did not cover the full cost");
    }
    if (deducted == 0) {
      return null;
    }

    int remaining = balance - deducted;
    account.setNumCredits(remaining);
    eventPublisher.publishEvent(
        new UserCreditsChangedEvent(emailAddress, account.getLocationId(), remaining));

    Integer persistentIdentity = account.nextSongCreditUsageIdentity();
    UserSongCreditUsageEntity usage = new UserSongCreditUsageEntity(persistentIdentity,
        account.getLocationId(), -deducted, type, Instant.now(), songAlbumId, songId, remaining,
        UUID.randomUUID().toString());
    return account.addSongCreditUsage(usage);
  }

  /**
   * Announces a spend via {@link LocationSongCreditUsageRecordedEvent} so master can mirror it to
   * that location's slave as an audit record. Called only once the spend has been stored.
   */
  private void announceLocationCreditUsage(UserSongCreditUsageEntity usage) {

    if (usage.getLocationId() != null) {
      // Harmless on standalone -- only master's MobileCreditUsageSlaveNotifier listens.
      eventPublisher.publishEvent(new LocationSongCreditUsageRecordedEvent(toDto(usage)));
    }
  }

  /**
   * Adds the package's credits (+ bonus) to the account's balance and appends an Add Funds record
   * to it. Callers persist the user root afterward, and only then broadcast the new balance.
   */
  private UserAddFundsTransactionEntity recordAddFundsTransaction(UserLocationAccountEntity account,
      CreditPackageDto pkg, PaymentChargeResult chargeResult, Instant timestamp) {

    int newBalance = account.getNumCredits() + pkg.credits() + pkg.bonusCredits();
    account.setNumCredits(newBalance);

    Integer persistentIdentity = account.nextAddFundsTransactionIdentity();
    return account.addAddFundsTransaction(new UserAddFundsTransactionEntity(persistentIdentity,
        account.getLocationId(), pkg.id(), pkg.credits(), pkg.bonusCredits(), pkg.priceUsd(),
        chargeResult.paymentSource(), chargeResult.transactionId(), timestamp, newBalance));
  }

  /**
   * The financial ledger records each Add Funds purchase in its location's ledger. Wired after
   * construction (see {@code AppConfig}), because the ledger service itself depends on this one.
   */
  public void setFinancialLedgerServiceSupplier(
      Supplier<FinancialLedgerService> financialLedgerServiceSupplier) {
    this.financialLedgerServiceSupplier = requireNonNull(financialLedgerServiceSupplier);
  }

  /** Used to name locations in balances and receipts, and to check an Add Funds location. */
  public void setLocationService(LocationService locationService) {
    this.locationService = locationService;
  }

  private FinancialLedgerService requireFinancialLedgerService() {

    FinancialLedgerService service = financialLedgerServiceSupplier.get();
    if (service == null) {
      throw new IllegalStateException("No financial ledger is available to record the purchase");
    }
    return service;
  }

  private boolean appIsMaster() {
    // Master is the only mode with no own location (see SongLibraryServiceImpl.initialize()).
    return songLibraryService.getOwnLocationId() == null;
  }

  private String locationNameOf(Integer locationId) {

    if (locationId == null || locationService == null) {
      return null;
    }
    LocationEntity location = locationService.getLocationByIdNullIfNotExists(locationId);
    return location != null ? location.getName() : null;
  }

  // Repository methods
  @Override
  public UserRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    return this.userRepository.loadAggregateRoot(naturalIdentity);
  }

  @Override
  public UserRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    return this.userRepository.loadAggregateRoot(persistentIdentity);
  }

  @Override
  public void storeAggregateRoot(UserRootEntity root) {

    this.userRepository.storeAggregateRoot(root);
  }

  // Command methods
  @Override
  public CommandResponse processCommand(CommandRequest commandRequest) {

    throw new UserServiceException("Not implemented yet!");
  }

  // Query methods
  @Override
  public QueryResponse<QueryRequest, QueryResponseItem> processQuery(QueryRequest queryRequest) {

    throw new UserServiceException("Not implemented yet!");
  }

  private void initialize() {

    try {
      this.userRoot = this.userRepository.loadAggregateRoot(UserRootEntity.USER_LIST_FILENAME);
    } catch (EntityDoesNotExistException ednee) {
      log.error("Could not load user root from dataDir, using empty user root for now, error: "
          + ednee.getMessage());
      this.userRoot = new UserRootEntity();
    }
  }
}
