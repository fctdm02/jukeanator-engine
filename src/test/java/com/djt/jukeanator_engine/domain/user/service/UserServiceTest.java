package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.common.security.InvalidPrincipalException;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;

/**
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml
public class UserServiceTest extends AbstractServiceIntegrationTest {

  @Autowired
  private UserService userService;

  @Test
  void shouldInitializeService() {
    assertNotNull(userService, "userService should be injected");
  }

  @Test
  void lifecycle() {

    // Register a new user
    RegisterRequest registerRequest =
        new RegisterRequest("Jane", "Doe", "jane.doe@example.com", "password123");
    AuthResponse registerResponse = userService.register(registerRequest);
    assertNotNull(registerResponse, "registerResponse should not be null");
    assertNotNull(registerResponse.token(), "token should not be null");
    assertEquals("jane.doe@example.com", registerResponse.emailAddress());
    assertEquals("ROLE_USER", registerResponse.role());

    // Registering the same email address again should fail
    assertThrows(UserServiceException.class, () -> userService.register(registerRequest));

    // Log in with the registered user's credentials
    LoginRequest loginRequest = new LoginRequest("jane.doe@example.com", "password123");
    AuthResponse loginResponse = userService.login(loginRequest);
    assertNotNull(loginResponse, "loginResponse should not be null");
    assertNotNull(loginResponse.token(), "token should not be null");
    assertEquals("jane.doe@example.com", loginResponse.emailAddress());

    // Logging in with an incorrect password should fail
    LoginRequest badLoginRequest = new LoginRequest("jane.doe@example.com", "wrongPassword");
    assertThrows(UserServiceException.class, () -> userService.login(badLoginRequest));

    // Logging in with an unknown email address should fail
    LoginRequest unknownLoginRequest = new LoginRequest("unknown@example.com", "password123");
    assertThrows(UserServiceException.class, () -> userService.login(unknownLoginRequest));

    // Get the profile for the registered user
    UserProfileDto profile = userService.getProfile("jane.doe@example.com");
    assertNotNull(profile, "profile should not be null");
    assertEquals("Jane", profile.firstName());
    assertEquals("Doe", profile.lastName());
    assertEquals("jane.doe@example.com", profile.emailAddress());

    // Getting a profile for an unknown email address should fail
    assertThrows(InvalidPrincipalException.class, () -> userService.getProfile("unknown@example.com"));
  }
}
