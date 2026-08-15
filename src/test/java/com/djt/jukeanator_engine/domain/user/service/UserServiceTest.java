package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.InvalidPrincipalException;
import com.djt.jukeanator_engine.domain.common.security.JwtUtil;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
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
@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml
public class UserServiceTest extends AbstractServiceIntegrationTest {

  private static final String REGISTERED_EMAIL = "jane.doe@example.com";

  @Autowired
  private UserService userService;

  private UserRepository userRepository;
  private UserRootEntity userRoot;
  private UserServiceImpl userServiceImpl;

  @BeforeEach
  void setUp() throws EntityDoesNotExistException {

    userRepository = mock(UserRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    JwtUtil jwtUtil = mock(JwtUtil.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    SongLibraryService songLibraryService = mock(SongLibraryService.class);

    userRoot = new UserRootEntity();
    userRoot.addUser(new UserEntity(Integer.valueOf(1), "Jane", "Doe", REGISTERED_EMAIL,
        "hashed-password", Integer.valueOf(6), "ROLE_USER"));

    when(userRepository.loadAggregateRoot(anyString())).thenReturn(userRoot);

    userServiceImpl = new UserServiceImpl("test-root", userRepository, passwordEncoder, jwtUtil,
        eventPublisher, songLibraryService, false);
  }

  @Test
  void shouldInitializeService() {
    assertNotNull(userService, "userService should be injected");
  }

  @Test
  void lifecycle() {

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
    assertThrows(UserServiceException.class, () -> userService.login(badLoginRequest));

    // Logging in with an unknown email address should fail
    LoginRequest unknownLoginRequest = new LoginRequest("unknown@example.com", "password123");
    assertThrows(UserServiceException.class, () -> userService.login(unknownLoginRequest));

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
  void deleteAccountRemovesRegisteredUserAndPersistsRoot() {

    userServiceImpl.deleteAccount(REGISTERED_EMAIL);

    assertNull(userRoot.getUserByEmailAddressNullIfNotExists(REGISTERED_EMAIL),
        "user should no longer be present in the user root");
    verify(userRepository).storeAggregateRoot(userRoot);
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
}
