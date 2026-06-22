package com.djt.jukeanator_engine.domain.user.service;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;

/**
 * @author tmyers
 */
public final class UserServiceImpl implements UserService, AggregateRootService<UserRootEntity> {

  private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

  private final String rootPath;
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

    try {
      if (userRoot.getUserByEmailAddress(request.emailAddress()) != null) {
        throw new IllegalArgumentException("Email already registered");
      }
    } catch (EntityDoesNotExistException ednee) {
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

    UserEntity user = null;
    try {
      user = userRoot.getUserByEmailAddress(request.emailAddress());
    } catch (EntityDoesNotExistException ednee) {
      throw new IllegalArgumentException("Invalid credentials");
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new IllegalArgumentException("Invalid credentials");
    }

    String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole());
    return new AuthResponse(token, user.getEmailAddress(), user.getRole());
  }

  @Override
  public UserProfileDto getProfile(String emailAddress) {

    UserEntity user = null;
    try {
      user = userRoot.getUserByEmailAddress(emailAddress);
    } catch (EntityDoesNotExistException ednee) {
      throw new IllegalArgumentException("User not found");
    }

    return new UserProfileDto(user.getPersistentIdentity(), user.getFirstName(), user.getLastName(),
        user.getEmailAddress(), user.getNumCredits(), user.getSongPlayHistory());
  }

  /** Called by SongLibraryService (or an event listener) when a song is played */
  /*
   * TODO: Need to have either singleton LOCAL UserEntity or a logged in remote user associated with
   * every SongPlay public void recordSongPlay(String emailAddress, Integer albumId, Integer songId)
   * { userRepository.findByEmailAddress(emailAddress).ifPresent(user -> {
   * user.getSongPlayHistory().add(albumId + ":" + songId); userRepository.save(user); }); }
   */

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
