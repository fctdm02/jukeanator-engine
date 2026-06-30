package com.djt.jukeanator_engine.domain.user.service;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserHomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;

/**
 * @author tmyers
 */
public class UserServiceImpl implements UserService, AggregateRootService<UserRootEntity> {

  private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

  private static final int CREDITS_PER_DOLLAR = 3;

  private String rootPath;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;

  private UserRootEntity userRoot;

  public UserServiceImpl(String rootPath, UserRepository userRepository,
      PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {

    requireNonNull(rootPath, "rootPath cannot be null");
    requireNonNull(userRepository, "userRepository cannot be null");
    requireNonNull(passwordEncoder, "passwordEncoder cannot be null");
    requireNonNull(jwtUtil, "jwtUtil cannot be null");

    this.rootPath = rootPath;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;

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
        request.emailAddress(), passwordEncoder.encode(request.password()), Integer.valueOf(0),
        new ArrayList<>(), "ROLE_USER");

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

  @Override
  public UserProfileDto getProfile(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);;
    if (user == null)
    {
      throw new UserServiceException("User not found: " + emailAddress);
    }

    java.math.BigDecimal balanceUsd = java.math.BigDecimal.valueOf(user.getNumCredits()).divide(
        java.math.BigDecimal.valueOf(CREDITS_PER_DOLLAR), 2, java.math.RoundingMode.HALF_UP);
    return new UserProfileDto(user.getPersistentIdentity(), user.getFirstName(), user.getLastName(),
        user.getEmailAddress(), user.getNumCredits(), balanceUsd, user.getSongPlayHistory());
  }

  @Override
  public UserHomePageDto getHomePage(String emailAddress) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null) {
      throw new UserServiceException("User not found: " + emailAddress);
    }

    // Placeholder — all content lists are empty until the underlying features are built.
    return new UserHomePageDto(
        List.of(),       // (a) myRecentPlays
        List.of(),       // (b) myPlaylists
        List.of(),       // (c) artistsHotHere
        List.of(),       // (d) songsHotHere
        user.getSearchHistory());
  }

  @Override
  public List<CreditPackageDto> getCreditPackages() {

    return List.of(
        new CreditPackageDto("pkg-20", 60, 20, new java.math.BigDecimal("20.00"), "Best Value"),
        new CreditPackageDto("pkg-10", 30, 10, new java.math.BigDecimal("10.00"), null),
        new CreditPackageDto("pkg-5", 15, 3, new java.math.BigDecimal("5.00"), null));
  }

  @Override
  public void changePassword(String emailAddress, ChangePasswordRequest request) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null)
    {
      throw new UserServiceException("User not found: " + emailAddress);
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
    if (user == null)
    {
      throw new UserServiceException("User not found: " + emailAddress);
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
    if (user == null)
    {
      throw new UserServiceException("User not found: " + emailAddress);
    }

    return user.getSearchHistory();
  }

  @Override
  public void addSearchHistory(String emailAddress, String query) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null)
    {
      throw new UserServiceException("User not found: " + emailAddress);
    }

    user.addToSearchHistory(query, 10);

    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @Override
  public void removeSearchHistory(String emailAddress, int index) {

    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(emailAddress);
    if (user == null)
    {
      throw new UserServiceException("User not found: " + emailAddress);
    }

    user.removeFromSearchHistory(index);

    this.userRepository.storeAggregateRoot(this.userRoot);
  }

  @EventListener
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {

    String username = event.queueEntry().getUsername();
    UserEntity user = userRoot.getUserByEmailAddressNullIfNotExists(username);
    if (user == null && username == "LOCAL") {

      String firstName = "Local";
      String lastName = "User";
      String password = "password";

      RegisterRequest request = new RegisterRequest(firstName, lastName, username, password);
      register(request);

      user = userRoot.getUserByEmailAddressNullIfNotExists(username);
    }
    
    if (user == null)
    {
      throw new UserServiceException("User not found: " + username);
    }

    SongDto song = event.queueEntry().getSong();
    user.addSongToSongPlayHistory(new SongIdentifier(song.getAlbumId(), song.getSongId()));

    this.userRepository.storeAggregateRoot(this.userRoot);
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
