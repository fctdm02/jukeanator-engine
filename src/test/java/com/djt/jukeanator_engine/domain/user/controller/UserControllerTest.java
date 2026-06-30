package com.djt.jukeanator_engine.domain.user.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import com.djt.jukeanator_engine.AbstractControllerTest;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.service.UserService;

class UserControllerTest extends AbstractControllerTest {

  @Mock
  private UserService userService;

  @InjectMocks
  private UserController userController;

  @Override
  protected Object getController() {
    return userController;
  }

  @Override
  protected void configureMockMvc(StandaloneMockMvcBuilder builder) {
    builder.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver());
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void register_returnsAuthResponse() throws Exception {
    RegisterRequest request = new RegisterRequest("Jane", "Doe", "jane@example.com", "password");
    AuthResponse response = new AuthResponse("token123", "jane@example.com", "USER");
    when(userService.register(any(RegisterRequest.class))).thenReturn(response);

    mockMvc.perform(post("/api/users/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token", is("token123")))
        .andExpect(jsonPath("$.emailAddress", is("jane@example.com")));
  }

  @Test
  void login_returnsAuthResponse() throws Exception {
    LoginRequest request = new LoginRequest("jane@example.com", "password");
    AuthResponse response = new AuthResponse("token123", "jane@example.com", "USER");
    when(userService.login(any(LoginRequest.class))).thenReturn(response);

    mockMvc.perform(post("/api/users/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token", is("token123")));
  }

  @Test
  void me_returnsProfileForAuthenticatedPrincipal() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    UserProfileDto profile =
        new UserProfileDto(1, "Jane", "Doe", "jane@example.com", 10, null, List.of());
    when(userService.getProfile("jane@example.com")).thenReturn(profile);

    mockMvc.perform(get("/api/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailAddress", is("jane@example.com")))
        .andExpect(jsonPath("$.numCredits", is(10)));
  }
}
