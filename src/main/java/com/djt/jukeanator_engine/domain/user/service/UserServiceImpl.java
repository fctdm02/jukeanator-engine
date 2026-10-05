package com.djt.jukeanator_engine.domain.user.service;

import static java.util.Objects.requireNonNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
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
import com.djt.jukeanator_engine.domain.user.event.UserCreditsChangedEvent;
import com.djt.jukeanator_engine.domain.user.exception.InsufficientCreditsException;
import com.djt.jukeanator_engine.domain.user.exception.InvalidCredentialsException;
import com.djt.jukeanator_engine.domain.user.exception.PaymentException;
import com.djt.jukeanator_engine.domain.user.exception.QueueAccessDeniedException;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserAddFundsTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
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

  static final Integer STARTING_CREDITS = Integer.valueOf(0);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final ApplicationEventPublisher eventPublisher;
  private final SongLibraryService songLibraryService;
  private final PricingService pricingService;
  private final boolean slaveMode;
  private final PaymentGateway paymentGateway;
  private final boolean allowSlaveUrlQueueOperations;

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

    requireNonNull(userRepository, "userRepository cannot be null");
    requireNonNull(passwordEncoder, "passwordEncoder cannot be null");
    requireNonNull(jwtUtil, "jwtUtil cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    requireNonNull(pricingService, "pricingService cannot be null");
    requireNonNull(paymentGateway, "paymentGateway cannot be null");

    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.eventPublisher = eventPublisher;
    this.songLibraryService = songLibraryService;
    this.pricingService = pricingService;
    this.slaveMode = slaveMode;
    this.paymentGateway = paymentGateway;
    this.allowSlaveUrlQueueOperations = allowSlaveUrlQueueOperations;
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
    return registerWithRole(request, UserRole.ROLE_USER);
  }

  @Override
  public AuthResponse addAdminUser(RegisterRequest request) {
    return registerWithRole(request, UserRole.ROLE_ADMIN);
  }

  private synchronized AuthResponse registerWithRole(RegisterRequest request, UserRole role) {

    UserEntity check = userRoot.getUserByEmailAddressNullIfNotExists(request.emailAddress());
    if (check != null) {
      throw new UserServiceException("Email already registered: " + request.emailAddress());
    }
    if (request.emailAddress() != null
        && request.emailAddress().endsWith(UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN)) {
      throw new UserServiceException("Invalid email address: " + request.emailAddress());
    }

    Integer persistentIdentity = this.userRoot.nextUserPersistentIdentity();

    // A new account starts with no credits: every credit a web user spends must have been bought
    // through Add Funds, since each one spent is paid out to the location as mobile revenue.
    UserEntity user = new UserEntity(persistentIdentity, request.firstName(), request.lastName(),
        request.emailAddress(), passwordEncoder.encode(request.password()), STARTING_CREDITS,
        role);

    this.userRoot.addUser(user);
    this.userRepository.storeAggregateRoot(this.userRoot);

    String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole().name());
    return new AuthResponse(token, user.getEmailAddress(), user.getRole().name());
  }

  @Override
  public synchronized AuthResponse login(LoginRequest request) throws InvalidCredentialsException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(request.emailAddress());;
    if (user == null || user.isClosed()) {
      throw new InvalidCredentialsException("Invalid credentials");
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException("Invalid credentials");
    }

    String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole().name());
    return new AuthResponse(token, user.getEmailAddress(), user.getRole().name());
  }

  @Override
  public synchronized UserProfileDto getProfile(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);;
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    PricingConfig pricingConfig =
        pricingService.resolvePricingConfig(songLibraryService.getOwnLocationId());
    java.math.BigDecimal balanceUsd = java.math.BigDecimal.valueOf(user.getNumCredits()).divide(
        java.math.BigDecimal.valueOf(pricingConfig.creditsPerDollar()), 2,
        java.math.RoundingMode.HALF_UP);
    return new UserProfileDto(user.getPersistentIdentity(), user.getFirstName(), user.getLastName(),
        user.getEmailAddress(), user.getNumCredits(), balanceUsd, user.getSongPlayHistory());
  }

  private static final int MAX_HOT_HERE = 10;

  @Override
  public synchronized HomePageDto getPublicHomePage() {
    var popular = songLibraryService.getMusicByPopularity(songLibraryService.getOwnLocationId());
    var artists = popular.artists().stream().limit(MAX_HOT_HERE).toList();
    var albums = popular.albums().stream().limit(MAX_HOT_HERE).toList();
    var songs = popular.songs().stream().limit(MAX_HOT_HERE).toList();
    return new HomePageDto(artists, albums, songs);
  }

  @Override
  public synchronized UserHomePageDto getHomePage(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    // Handle the case where a de-serialized user does not have any playlists.
    List<PlaylistEntity> playlists = user.getPlaylists();
    if (playlists == null || playlists.isEmpty()) {

      user.createMyFavoritesPlaylist();
    }

    List<String> playlistNames = new ArrayList<>();
    for (PlaylistEntity playlist : user.getPlaylists()) {
      playlistNames.add(playlist.getName());
    }

    List<SongIdentifier> history = user.getSongPlayHistory();
    List<SongDto> recentPlays = new ArrayList<>();
    for (int i = history.size() - 1; i >= 0 && recentPlays.size() < MAX_RECENT_PLAYS; i--) {
      SongIdentifier id = history.get(i);
      try {
        // TODO(Phase E): once SongIdentifier carries its own locationId, use that instead of
        // getOwnLocationId() -- a master-served user's history can span multiple locations.
        SongDto song = songLibraryService.getSongById(songLibraryService.getOwnLocationId(),
            id.getAlbumId(), id.getSongId());
        if (song != null)
          recentPlays.add(song);
      } catch (Exception e) {
        // song may have been removed from the library; skip it
      }
    }

    HomePageDto publicHomePageDto = getPublicHomePage();
    
    UserHomePageDto userHomePageDto =
        new UserHomePageDto(recentPlays, playlistNames, publicHomePageDto.artistsHotHere(),
            publicHomePageDto.albumsHotHere(), publicHomePageDto.songsHotHere(),
            user.getSearchHistory());

    return userHomePageDto;
  }

  @Override
  public List<CreditPackageDto> getCreditPackages() {

    return List.of(
        new CreditPackageDto("pkg-28", 48, 24, new java.math.BigDecimal("28.00"), "Best Value"),
        new CreditPackageDto("pkg-14", 24, 7, new java.math.BigDecimal("14.00"), null),
        new CreditPackageDto("pkg-7", 12, 1, new java.math.BigDecimal("7.00"), null));
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

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new UserServiceException("Current password is incorrect");
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public synchronized void deleteAccount(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    // Closed, never removed: the account's Add Funds transactions and song-credit usages are the
    // record that Braintree's charges and every location's mobile revenue are reconciled against.
    String closedEmailAddress =
        "closed-" + UUID.randomUUID() + UserEntity.CLOSED_ACCOUNT_EMAIL_DOMAIN;
    userRoot.closeUser(emailAddress, closedEmailAddress,
        passwordEncoder.encode(UUID.randomUUID().toString()));
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public synchronized AddFundsResponseDto addFunds(String emailAddress, AddFundsRequest request) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    CreditPackageDto pkg = getCreditPackages().stream()
        .filter(p -> p.id().equals(request.packageId()))
        .findFirst()
        .orElseThrow(() -> new PaymentException("Unknown package: " + request.packageId()));

    if (request.paymentMethodNonce() == null || request.paymentMethodNonce().isBlank()) {
      throw new PaymentException("Missing payment method");
    }

    PaymentChargeResult chargeResult = paymentGateway.charge(pkg.priceUsd(), request.paymentMethodNonce());
    if (!chargeResult.success()) {
      throw new PaymentException(chargeResult.failureMessage());
    }

    Integer locationId = songLibraryService.getOwnLocationId();
    Instant now = Instant.now();

    int previousBalance = user.getNumCredits() != null ? user.getNumCredits() : 0;
    UserAddFundsTransactionEntity transaction =
        recordAddFundsTransaction(user, pkg, chargeResult, now);
    try {
      this.userRepository.storeAggregateRoot(this.userRoot);
    } catch (RuntimeException e) {
      // The card was charged but the credits could not be stored -- undo both, so the customer
      // is never billed for credits they did not receive.
      user.setNumCredits(previousBalance);
      user.removeUnstoredUserAddFundsTransaction(transaction);
      boolean voided = paymentGateway.voidCharge(chargeResult.transactionId());
      log.error("Add Funds for " + emailAddress + " could not be stored; payment transaction "
          + chargeResult.transactionId() + (voided ? " was voided" : " could NOT be voided"), e);
      throw new PaymentException(voided
          ? "Your purchase could not be completed and your payment was cancelled. Please try again."
          : "Your purchase could not be completed. Please contact support to be refunded.", e);
    }

    eventPublisher.publishEvent(new UserCreditsChangedEvent(emailAddress, user.getNumCredits()));
    eventPublisher.publishEvent(new PurchaseCompletedEvent(emailAddress, user.getFirstName(),
        pkg.credits(), pkg.bonusCredits(), pkg.priceUsd(), chargeResult.paymentSource(),
        chargeResult.transactionId(), now, user.getNumCredits()));

    PricingConfig pricingConfig = pricingService.resolvePricingConfig(locationId);
    BigDecimal balanceUsd = BigDecimal.valueOf(user.getNumCredits())
        .divide(BigDecimal.valueOf(pricingConfig.creditsPerDollar()), 2, RoundingMode.HALF_UP);

    return new AddFundsResponseDto(user.getNumCredits(), balanceUsd, pkg.credits(),
        pkg.bonusCredits(), chargeResult.paymentSource(), chargeResult.transactionId(), now);
  }

  @Override
  public String generatePaymentClientToken() {
    return paymentGateway.generateClientToken();
  }

  @Override
  public synchronized UserProfileDto updateProfile(String emailAddress, UpdateProfileRequest request) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    if (request.firstName() != null)
      user.setFirstName(request.firstName());
    if (request.lastName() != null)
      user.setLastName(request.lastName());

    this.userRepository.storeAggregateRoot(this.userRoot);
    return getProfile(emailAddress);
  }

  @Override
  public synchronized List<String> getSearchHistory(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    return user.getSearchHistory();
  }

  @Override
  public synchronized void addSearchHistory(String emailAddress, String query) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    user.addToSearchHistory(query, 10);

    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public synchronized void removeSearchHistory(String emailAddress, int index) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    user.removeFromSearchHistory(index);

    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public synchronized boolean createPlaylist(String emailAddress, String playlistName)
      throws EntityAlreadyExistsException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    user.createPlaylist(playlistName);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return true;
  }

  @Override
  public synchronized boolean addSongToPlaylist(String emailAddress, String playlistName, Integer locationId,
      SongFileEntity song) throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result = user.addSongToPlaylist(playlistName, locationId, song);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return result;
  }

  @Override
  public synchronized boolean removeSongFromPlaylist(String emailAddress, String playlistName,
      Integer locationId, SongFileEntity song) throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result = user.removeSongFromPlaylist(playlistName, locationId, song);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return result;
  }

  @Override
  public synchronized boolean deletePlaylist(String emailAddress, String playlistName)
      throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result = user.deletePlaylist(playlistName);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return result;
  }

  @Override
  public synchronized boolean renamePlaylist(String emailAddress, String playlistName,
      String newPlaylistName) throws EntityDoesNotExistException, EntityAlreadyExistsException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result = user.renamePlaylist(playlistName, newPlaylistName);
    if (result) {
      this.userRepository.storeAggregateRoot(this.userRoot);
    }

    return result;
  }

  @Override
  public synchronized boolean addSongToMyFavoritesPlaylist(String emailAddress, Integer locationId,
      SongFileEntity song) throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result =
        user.addSongToPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, locationId, song);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return result;
  }

  @Override
  public synchronized boolean removeSongFromMyFavoritesPlaylist(String emailAddress, Integer locationId,
      SongFileEntity song) throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result =
        user.removeSongFromPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, locationId, song);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return result;
  }

  @Override
  public synchronized List<PlaylistSummaryDto> getPlaylists(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    List<PlaylistSummaryDto> result = new ArrayList<>();
    for (PlaylistEntity p : user.getPlaylists()) {
      List<SongIdentifier> songs = p.getSongs();
      Integer firstSongAlbumId = songs.isEmpty() ? null : songs.get(0).getAlbumId();
      result.add(new PlaylistSummaryDto(p.getName(), songs.size(), firstSongAlbumId));
    }
    return result;
  }

  @Override
  public synchronized List<SongDto> getPlaylistSongs(String emailAddress, String playlistName)
      throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    PlaylistEntity playlist = user.getPlaylistByName(playlistName);
    List<SongDto> result = new ArrayList<>();
    for (SongIdentifier si : playlist.getSongs()) {
      try {
        // TODO(Phase E): once SongIdentifier carries its own locationId, use that instead of
        // getOwnLocationId() -- a master-served playlist can span multiple locations.
        SongDto song = songLibraryService.getSongById(songLibraryService.getOwnLocationId(),
            si.getAlbumId(), si.getSongId());
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

  @Override
  public synchronized void reorderPlaylistSongs(String emailAddress, String playlistName,
      List<SongIdentifier> songs) throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    PlaylistEntity playlist = user.getPlaylistByName(playlistName);
    // Match each requested song against the stored entries and keep the stored identifier, so
    // a client that omits (or mis-tags) locationId can never strip or blank out the entry's
    // locationId -- matching on the full identifier here previously dropped every such song.
    List<SongIdentifier> current = new ArrayList<>(playlist.getSongs());
    List<SongIdentifier> reordered = new ArrayList<>();
    for (SongIdentifier si : songs) {
      for (SongIdentifier stored : current) {
        if ((si.getLocationId() == null || si.getLocationId().equals(stored.getLocationId()))
            && Objects.equals(si.getAlbumId(), stored.getAlbumId())
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
  public synchronized List<SongIdentifier> getFavoriteSongIdentifiers(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    PlaylistEntity favs =
        user.getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    if (favs == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(favs.getSongs());
  }

  @Override
  public synchronized List<SongIdentifier> getPlaylistSongIdentifiers(String emailAddress,
      String playlistName) throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    return new ArrayList<>(user.getPlaylistByName(playlistName).getSongs());
  }

  /**
   * Re-tags every user's in-memory play history, playlist songs and song-credit usages from the
   * previous own location id to the confirmed one, then persists them -- under JPA the rows were
   * already re-pointed by {@code LocationRepositoryJpaImpl.changeLocationId}, so without this the
   * next store would write the previous id back over them.
   */
  @EventListener
  @Override
  public synchronized void handleOwnLocationIdChangedEvent(OwnLocationIdChangedEvent event) {

    this.userRoot.changeLocationId(event.previousLocationId(), event.confirmedLocationId());
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  /**
   * A song this instance added to its own queue -- tagged with the own location id, so the spend
   * counts toward this location's mobile revenue (an untagged spend is never counted). On master,
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
    // locationId-aware overload after the command succeeds (see
    // LocationScopedSongQueueController), so this would otherwise double-charge (or, as here,
    // throw "user not found" and cancel the whole add). A genuine local walk-up action
    // (LOCAL_USERNAME) is untouched — it never carries credits through this path either way.
    if (slaveMode && !LocalPrincipal.LOCAL_USERNAME.equals(username)) {
      return;
    }

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(username);
    if (user == null && LocalPrincipal.LOCAL_USERNAME.equals(username)) {

      String firstName = "Local";
      String lastName = "User";
      String password = "password";

      RegisterRequest request = new RegisterRequest(firstName, lastName, username, password);
      register(request);

      user = userRoot.getUserByEmailAddressNullIfNotExists(username);
    }

    if (user == null) {
      throw new UserServiceException("User not found: " + username);
    }

    SongDto song = event.queueEntry().song();
    user.addSongToSongPlayHistory(
        new SongIdentifier(locationId, song.albumId(), song.songId()));

    // Deduct Web UI credits for non-local (web) users -- see CreditCostCalculator for the
    // swingCost * webCostMultiplier formula.
    UserSongCreditUsageEntity usage = null;
    if (!LocalPrincipal.LOCAL_USERNAME.equals(username)) {
      int priority =
          event.queueEntry().priority() != null ? event.queueEntry().priority() : 1;
      int cost = CreditCostCalculator.webQueueAddCost(pricingService.resolvePricingConfig(locationId),
          priority, event.priorityPlay());
      usage = deductCredits(user, username, cost, UserSongCreditUsageType.QUEUE_ADD, locationId,
          song.albumId(), song.songId());
    }

    this.userRepository.storeAggregateRoot(this.userRoot);
    if (usage != null) {
      announceLocationCreditUsage(usage);
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

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    int cost = queueActionCost(priority, locationId);
    UserSongCreditUsageEntity usage = deductCredits(user, emailAddress, cost,
        UserSongCreditUsageType.QUEUE_ACTION, locationId, null, null);

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
    requireAffordable(requireUser(emailAddress), CreditCostCalculator
        .webQueueAddCost(pricingService.resolvePricingConfig(locationId), priority, priorityPlay));
  }

  @Override
  public synchronized void requireAffordableQueueAction(String emailAddress, Integer priority,
      Integer locationId) throws InsufficientCreditsException {

    if (slaveMode) {
      requireSlaveQueueAccess(emailAddress);
      return;
    }
    requireAffordable(requireUser(emailAddress), queueActionCost(priority, locationId));
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

  private static void requireAffordable(UserEntity user, int cost) {

    int balance = user.getNumCredits() != null ? user.getNumCredits() : 0;
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

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    int cost = CreditCostCalculator.webQueueAddCost(
        pricingService.resolvePricingConfig(locationId), priority, priorityPlay);
    if (cost <= 0) {
      return Integer.MAX_VALUE;
    }
    int numCredits = user.getNumCredits() != null ? user.getNumCredits() : 0;
    return Math.max(0, numCredits) / cost;
  }

  @Override
  public synchronized List<UserSongCreditUsageDto> getCreditLedgerForLocation(Integer locationId, Instant from,
      Instant to) {

    return userRoot.getUsers().stream()
        .flatMap(u -> u.getUserSongCreditUsages().stream())
        .filter(t -> locationId.equals(t.getLocationId()))
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
   * Deducts {@code cost} credits (floored at zero), broadcasts the new balance, and appends a
   * ledger entry for the credits actually deducted to the user's own song-credit-usage set -- each
   * of those credits is paid out to {@code locationId} as mobile revenue, so the entry must never
   * claim more than the balance really lost. Returns {@code null}, recording nothing, when nothing
   * was deducted. Callers refuse an unaffordable action up front (see {@link #requireAffordable}),
   * so the floor only matters as a last line of defense. Callers are responsible for persisting the
   * user root afterward, and then for passing a non-null entry to {@link
   * #announceLocationCreditUsage}.
   */
  private UserSongCreditUsageEntity deductCredits(UserEntity user, String emailAddress, int cost,
      UserSongCreditUsageType type, Integer locationId, Integer songAlbumId, Integer songId) {

    int balance = user.getNumCredits() != null ? user.getNumCredits() : 0;
    int deducted = Math.max(0, Math.min(cost, balance));
    if (deducted < cost) {
      log.warn("Charged " + emailAddress + " " + deducted + " of " + cost
          + " credits -- the balance did not cover the full cost");
    }
    if (deducted == 0) {
      return null;
    }

    int remaining = balance - deducted;
    user.setNumCredits(remaining);
    eventPublisher.publishEvent(new UserCreditsChangedEvent(emailAddress, remaining));

    Integer persistentIdentity = user.nextUserSongCreditUsageIdentity();
    UserSongCreditUsageEntity usage = new UserSongCreditUsageEntity(persistentIdentity,
        locationId, -deducted, type, Instant.now(), songAlbumId, songId, remaining,
        UUID.randomUUID().toString());
    return user.addUserSongCreditUsage(usage);
  }

  /**
   * Announces a location-attributed spend via {@link LocationSongCreditUsageRecordedEvent} so
   * master can mirror it to that location's slave. Called only once the spend has been stored, so
   * a slave can never end up holding a spend master itself failed to record.
   */
  private void announceLocationCreditUsage(UserSongCreditUsageEntity usage) {

    if (usage.getLocationId() != null) {
      // Harmless on standalone -- only master's MobileCreditUsageSlaveNotifier listens.
      eventPublisher.publishEvent(new LocationSongCreditUsageRecordedEvent(toDto(usage)));
    }
  }

  /**
   * Adds the package's credits (+ bonus) and appends an Add-Funds ledger entry to the user's own
   * set -- never location-attributed, since Add-Funds credits can be spent at any location (see
   * {@link UserAddFundsTransactionEntity}'s javadoc). Callers are responsible for persisting the
   * user root afterward, and only then for broadcasting the new balance.
   */
  private UserAddFundsTransactionEntity recordAddFundsTransaction(UserEntity user,
      CreditPackageDto pkg, PaymentChargeResult chargeResult, Instant timestamp) {

    int totalCredits = pkg.credits() + pkg.bonusCredits();
    int newBalance = (user.getNumCredits() != null ? user.getNumCredits() : 0) + totalCredits;
    user.setNumCredits(newBalance);

    Integer persistentIdentity = user.nextUserAddFundsTransactionIdentity();
    return user.addUserAddFundsTransaction(new UserAddFundsTransactionEntity(persistentIdentity,
        pkg.id(), pkg.credits(), pkg.bonusCredits(), pkg.priceUsd(), chargeResult.paymentSource(),
        chargeResult.transactionId(), timestamp, newBalance));
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
