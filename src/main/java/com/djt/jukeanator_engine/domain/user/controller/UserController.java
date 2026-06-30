package com.djt.jukeanator_engine.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {

    this.userService = userService;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {

    return ResponseEntity.ok(userService.register(request));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {

    return ResponseEntity.ok(userService.login(request));
  }

  /** Returns the profile for the currently authenticated user */
  @GetMapping("/me")
  public ResponseEntity<UserProfileDto> me(@AuthenticationPrincipal String emailAddress) {

    return ResponseEntity.ok(userService.getProfile(emailAddress));
  }

  @GetMapping("/credit-packages")
  public ResponseEntity<java.util.List<CreditPackageDto>> getCreditPackages() {
    return ResponseEntity.ok(userService.getCreditPackages());
  }

  @PostMapping("/change-password")
  public ResponseEntity<Void> changePassword(@AuthenticationPrincipal String emailAddress,
      @RequestBody ChangePasswordRequest request) {
    userService.changePassword(emailAddress, request);
    return ResponseEntity.noContent().build();
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/me")
  public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal String emailAddress) {
    userService.deleteAccount(emailAddress);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/add-funds")
  public ResponseEntity<Void> addFunds(@AuthenticationPrincipal String emailAddress,
      @RequestBody AddFundsRequest request) {
    userService.addFunds(emailAddress, request);
    return ResponseEntity.noContent().build();
  }

  @org.springframework.web.bind.annotation.PutMapping("/me")
  public ResponseEntity<UserProfileDto> updateProfile(@AuthenticationPrincipal String emailAddress,
      @RequestBody UpdateProfileRequest request) {
    return ResponseEntity.ok(userService.updateProfile(emailAddress, request));
  }
}
