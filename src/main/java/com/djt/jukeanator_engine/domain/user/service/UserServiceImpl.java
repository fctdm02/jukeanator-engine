package com.djt.jukeanator_engine.domain.user.service;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.dto.CreditTransactionDto;
import com.djt.jukeanator_engine.domain.user.dto.HomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.PlaylistSummaryDto;
import com.djt.jukeanator_engine.domain.user.dto.PricingConfigDto;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserHomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.event.UserCreditsChangedEvent;
import com.djt.jukeanator_engine.domain.user.exception.InvalidCredentialsException;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.CreditTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.CreditTransactionType;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
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

  private UserRootEntity userRoot;

  public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil, ApplicationEventPublisher eventPublisher,
      SongLibraryService songLibraryService, PricingService pricingService, boolean slaveMode) {

    requireNonNull(userRepository, "userRepository cannot be null");
    requireNonNull(passwordEncoder, "passwordEncoder cannot be null");
    requireNonNull(jwtUtil, "jwtUtil cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    requireNonNull(pricingService, "pricingService cannot be null");

    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.eventPublisher = eventPublisher;
    this.songLibraryService = songLibraryService;
    this.pricingService = pricingService;
    this.slaveMode = slaveMode;

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

    Integer persistentIdentity = Integer.valueOf(this.userRoot.getUsers().size() + 1);

    UserEntity user = new UserEntity(persistentIdentity, request.firstName(), request.lastName(),
        request.emailAddress(), passwordEncoder.encode(request.password()), Integer.valueOf(6),
        role);

    this.userRoot.addUser(user);
    this.userRepository.storeAggregateRoot(this.userRoot);

    String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole().name());
    return new AuthResponse(token, user.getEmailAddress(), user.getRole().name());
  }

  @Override
  public synchronized AuthResponse login(LoginRequest request) throws InvalidCredentialsException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(request.emailAddress());;
    if (user == null) {
      throw new InvalidCredentialsException("Invalid credentials");
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException("Invalid credentials");
    }

    String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole().name());
    return new AuthResponse(token, user.getEmailAddress(), user.getRole().name());
  }

  private static final int DEFAULT_CREDITS = 6;

  @Override
  public synchronized UserProfileDto getProfile(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);;
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    // Temporary: ensure every user has at least the default credit balance until
    // the Add Funds workflow is implemented.
    if (user.getNumCredits() == null || user.getNumCredits() == 0) {
      user.setNumCredits(DEFAULT_CREDITS);
      this.userRepository.storeAggregateRoot(this.userRoot);
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
    var songs = popular.songs().stream().limit(MAX_HOT_HERE).toList();
    return new HomePageDto(artists, songs);
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
            publicHomePageDto.songsHotHere(), user.getSearchHistory());

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

    userRoot.removeUser(emailAddress);
    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public void addFunds(String emailAddress, AddFundsRequest request) {

    throw new UserServiceException("Add funds payment not yet implemented");
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
    List<SongIdentifier> current = new ArrayList<>(playlist.getSongs());
    List<SongIdentifier> reordered = new ArrayList<>();
    for (SongIdentifier si : songs) {
      if (current.contains(si)) {
        reordered.add(si);
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

  @EventListener
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {
    handleSongAddedToQueueEvent(event, null);
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
    if (!LocalPrincipal.LOCAL_USERNAME.equals(username)) {
      int priority =
          event.queueEntry().priority() != null ? event.queueEntry().priority() : 1;
      int cost = CreditCostCalculator.webQueueAddCost(pricingService.resolvePricingConfig(locationId),
          priority);
      deductCredits(user, username, cost, CreditTransactionType.QUEUE_ADD, locationId,
          song.albumId(), song.songId());
    }

    this.userRepository.storeAggregateRoot(this.userRoot);
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

    int cost = CreditCostCalculator.webQueueActionCost(pricingService.resolvePricingConfig(locationId),
        priority != null ? priority : 1);
    deductCredits(user, emailAddress, cost, CreditTransactionType.QUEUE_ACTION, locationId, null, null);

    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public synchronized List<CreditTransactionDto> getCreditLedgerForLocation(Integer locationId, Instant from,
      Instant to) {

    return userRoot.getUsers().stream()
        .flatMap(u -> u.getTransactions().stream())
        .filter(t -> locationId.equals(t.getLocationId()))
        .filter(t -> !t.getTimestamp().isBefore(from) && !t.getTimestamp().isAfter(to))
        .sorted(java.util.Comparator.comparing(CreditTransactionEntity::getTimestamp))
        .map(t -> new CreditTransactionDto(t.getUserEmail(), t.getLocationId(), t.getAmount(),
            t.getType(), t.getTimestamp(), t.getSongAlbumId(), t.getSongId(),
            t.getResultingBalance()))
        .toList();
  }

  /**
   * Deducts {@code cost} credits (floored at zero), broadcasts the new balance, and appends a
   * ledger entry to the user's own transaction set. {@code locationId} is {@code null} for
   * standalone-mode/non-location-attributed spends. Callers are responsible for persisting the
   * user root afterward.
   */
  private void deductCredits(UserEntity user, String emailAddress, int cost,
      CreditTransactionType type, Integer locationId, Integer songAlbumId, Integer songId) {

    int remaining = Math.max(0, (user.getNumCredits() != null ? user.getNumCredits() : 0) - cost);
    user.setNumCredits(remaining);
    eventPublisher.publishEvent(new UserCreditsChangedEvent(emailAddress, remaining));

    Integer persistentIdentity = Integer.valueOf(user.getTransactions().size() + 1);
    user.addTransaction(new CreditTransactionEntity(persistentIdentity, locationId, -cost, type,
        Instant.now(), songAlbumId, songId, remaining));
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
