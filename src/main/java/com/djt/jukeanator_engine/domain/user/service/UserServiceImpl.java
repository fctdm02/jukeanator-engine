package com.djt.jukeanator_engine.domain.user.service;

import static java.util.Objects.requireNonNull;
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
import com.djt.jukeanator_engine.domain.user.dto.HomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.PlaylistSummaryDto;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserHomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.event.UserCreditsChangedEvent;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;

/**
 * @author tmyers
 */
public class UserServiceImpl implements UserService, AggregateRootService<UserRootEntity> {

  private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

  private static final int CREDITS_PER_DOLLAR = 3;

  /** Web UI credit cost for a normal play (priority == 1). */
  private static final int WEB_COST_PER_PRIORITY_LEVEL = 2;

  /**
   * Web UI credit cost per priority level for reordering/removing a queued song — double the
   * JFC/Swing Queue tab's {@code priority * 3} (minimum {@value #WEB_QUEUE_ACTION_MIN_COST}),
   * matching the same 2x multiplier used for {@link #WEB_COST_PER_PRIORITY_LEVEL}.
   */
  private static final int WEB_QUEUE_ACTION_COST_PER_PRIORITY_LEVEL = 6;
  private static final int WEB_QUEUE_ACTION_MIN_COST = 2;

  private static final int MAX_RECENT_PLAYS = 10;

  private String rootPath;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final ApplicationEventPublisher eventPublisher;
  private final SongLibraryService songLibraryService;

  private UserRootEntity userRoot;

  public UserServiceImpl(String rootPath, UserRepository userRepository,
      PasswordEncoder passwordEncoder, JwtUtil jwtUtil, ApplicationEventPublisher eventPublisher,
      SongLibraryService songLibraryService) {

    requireNonNull(rootPath, "rootPath cannot be null");
    requireNonNull(userRepository, "userRepository cannot be null");
    requireNonNull(passwordEncoder, "passwordEncoder cannot be null");
    requireNonNull(jwtUtil, "jwtUtil cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");

    this.rootPath = rootPath;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.eventPublisher = eventPublisher;
    this.songLibraryService = songLibraryService;

    initialize();

    log.info("Using user root: " + this.userRoot);
  }

  // Service methods
  @Override
  public AuthResponse register(RegisterRequest request) {

    UserEntity check = userRoot.getUserByEmailAddressNullIfNotExists(request.emailAddress());
    if (check != null) {
      throw new UserServiceException("Email already registered: " + request.emailAddress());
    }

    Integer persistentIdentity = Integer.valueOf(this.userRoot.getUsers().size() + 1);

    UserEntity user = new UserEntity(persistentIdentity, request.firstName(), request.lastName(),
        request.emailAddress(), passwordEncoder.encode(request.password()), Integer.valueOf(6),
        "ROLE_USER");

    this.userRoot.addUser(user);
    this.userRepository.storeAggregateRoot(this.userRoot);

    String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole());
    return new AuthResponse(token, user.getEmailAddress(), user.getRole());
  }

  @Override
  public AuthResponse login(LoginRequest request) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(request.emailAddress());;
    if (user == null) {
      throw new UserServiceException("Invalid credentials");
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new UserServiceException("Invalid credentials");
    }

    String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole());
    return new AuthResponse(token, user.getEmailAddress(), user.getRole());
  }

  private static final int DEFAULT_CREDITS = 6;

  @Override
  public UserProfileDto getProfile(String emailAddress) {

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

    java.math.BigDecimal balanceUsd = java.math.BigDecimal.valueOf(user.getNumCredits()).divide(
        java.math.BigDecimal.valueOf(CREDITS_PER_DOLLAR), 2, java.math.RoundingMode.HALF_UP);
    return new UserProfileDto(user.getPersistentIdentity(), user.getFirstName(), user.getLastName(),
        user.getEmailAddress(), user.getNumCredits(), balanceUsd, user.getSongPlayHistory());
  }

  private static final int MAX_HOT_HERE = 10;

  @Override
  public HomePageDto getPublicHomePage() {
    var popular = songLibraryService.getMusicByPopularity();
    var artists = popular.getArtists().stream().limit(MAX_HOT_HERE).toList();
    var songs = popular.getSongs().stream().limit(MAX_HOT_HERE).toList();
    return new HomePageDto(artists, songs);
  }

  @Override
  public UserHomePageDto getHomePage(String emailAddress) {

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
        SongDto song = songLibraryService.getSongById(id.getAlbumId(), id.getSongId());
        if (song != null)
          recentPlays.add(song);
      } catch (Exception e) {
        // song may have been removed from the library; skip it
      }
    }

    HomePageDto publicHomePageDto = getPublicHomePage();
    
    UserHomePageDto userHomePageDto =
        new UserHomePageDto(recentPlays, playlistNames, publicHomePageDto.getArtistsHotHere(),
            publicHomePageDto.getSongsHotHere(), user.getSearchHistory());

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
  public void changePassword(String emailAddress, ChangePasswordRequest request) {

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
  public void deleteAccount(String emailAddress) {

    throw new UserServiceException("Delete account not yet implemented");
  }

  @Override
  public void addFunds(String emailAddress, AddFundsRequest request) {

    throw new UserServiceException("Add funds payment not yet implemented");
  }

  @Override
  public UserProfileDto updateProfile(String emailAddress, UpdateProfileRequest request) {

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
  public List<String> getSearchHistory(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    return user.getSearchHistory();
  }

  @Override
  public void addSearchHistory(String emailAddress, String query) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    user.addToSearchHistory(query, 10);

    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public void removeSearchHistory(String emailAddress, int index) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    user.removeFromSearchHistory(index);

    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public boolean createPlaylist(String emailAddress, String playlistName)
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
  public boolean addSongToPlaylist(String emailAddress, String playlistName, SongFileEntity song)
      throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result = user.addSongToPlaylist(playlistName, song);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return result;
  }

  @Override
  public boolean removeSongFromPlaylist(String emailAddress, String playlistName,
      SongFileEntity song) throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result = user.removeSongFromPlaylist(playlistName, song);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return result;
  }

  @Override
  public boolean deletePlaylist(String emailAddress, String playlistName)
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
  public boolean addSongToMyFavoritesPlaylist(String emailAddress, SongFileEntity song)
      throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result = user.addSongToPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, song);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return result;
  }

  @Override
  public boolean removeSongFromMyFavoritesPlaylist(String emailAddress, SongFileEntity song)
      throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    boolean result = user.removeSongFromPlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, song);

    this.userRepository.storeAggregateRoot(this.userRoot);

    return result;
  }

  @Override
  public List<PlaylistSummaryDto> getPlaylists(String emailAddress) {

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
  public List<SongDto> getPlaylistSongs(String emailAddress, String playlistName)
      throws EntityDoesNotExistException {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    PlaylistEntity playlist = user.getPlaylistByName(playlistName);
    List<SongDto> result = new ArrayList<>();
    for (SongIdentifier si : playlist.getSongs()) {
      try {
        SongDto song = songLibraryService.getSongById(si.getAlbumId(), si.getSongId());
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
  public void reorderPlaylistSongs(String emailAddress, String playlistName,
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
  public List<SongIdentifier> getFavoriteSongIdentifiers(String emailAddress) {

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

    String username = event.queueEntry().getUsername();
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

    SongDto song = event.queueEntry().getSong();
    user.addSongToSongPlayHistory(new SongIdentifier(song.getAlbumId(), song.getSongId()));

    // Deduct Web UI credits for non-local (web) users.
    // Cost = priority * WEB_COST_PER_PRIORITY_LEVEL:
    // normal play (priority == 1) → 2 credits
    // priority play (priority == N) → N * 2 credits
    if (!LocalPrincipal.LOCAL_USERNAME.equals(username)) {
      int priority =
          event.queueEntry().getPriority() != null ? event.queueEntry().getPriority() : 1;
      deductCredits(user, username, priority * WEB_COST_PER_PRIORITY_LEVEL);
    }

    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public void chargeCreditsForQueueAction(String emailAddress, Integer priority) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new InvalidPrincipalException("User not found: " + emailAddress);
    }

    int cost = Math.max(WEB_QUEUE_ACTION_MIN_COST,
        (priority != null ? priority : 1) * WEB_QUEUE_ACTION_COST_PER_PRIORITY_LEVEL);
    deductCredits(user, emailAddress, cost);

    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  /** Deducts {@code cost} credits (floored at zero) and broadcasts the new balance. */
  private void deductCredits(UserEntity user, String emailAddress, int cost) {
    int remaining = Math.max(0, (user.getNumCredits() != null ? user.getNumCredits() : 0) - cost);
    user.setNumCredits(remaining);
    eventPublisher.publishEvent(new UserCreditsChangedEvent(emailAddress, remaining));
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
      this.userRoot = this.userRepository.loadAggregateRoot(rootPath);
    } catch (EntityDoesNotExistException ednee) {
      log.error("Could not load user root from: " + rootPath
          + ", using empty user root for now, error: " + ednee.getMessage());
      this.userRoot = new UserRootEntity();
    }
  }
}
