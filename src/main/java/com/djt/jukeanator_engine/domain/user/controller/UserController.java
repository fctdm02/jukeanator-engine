package com.djt.jukeanator_engine.domain.user.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.domain.user.dto.HomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserHomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;
  private final SongLibraryService songLibraryService;

  public UserController(UserService userService, SongLibraryService songLibraryService) {

    this.userService = userService;
    this.songLibraryService = songLibraryService;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {

    return ResponseEntity.ok(userService.register(request));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {

    return ResponseEntity.ok(userService.login(request));
  }

  /** Returns trending artists and songs for unauthenticated users. */
  @GetMapping("/home-public")
  public ResponseEntity<HomePageDto> getPublicHomePage() {

    return ResponseEntity.ok(userService.getPublicHomePage());
  }

  /** Returns home-page content and search history for the currently authenticated user. */
  @GetMapping("/home")
  public ResponseEntity<UserHomePageDto> getHomePage(@AuthenticationPrincipal String emailAddress) {

    return ResponseEntity.ok(userService.getHomePage(emailAddress));
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

  @GetMapping("/search-history")
  public ResponseEntity<List<String>> getSearchHistory(@AuthenticationPrincipal String emailAddress) {
    return ResponseEntity.ok(userService.getSearchHistory(emailAddress));
  }

  @PostMapping("/search-history")
  public ResponseEntity<Void> addSearchHistory(@AuthenticationPrincipal String emailAddress,
      @RequestBody Map<String, String> body) {
    String query = body.get("query");
    if (query != null && !query.isBlank()) {
      userService.addSearchHistory(emailAddress, query.strip());
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/search-history/{index}")
  public ResponseEntity<Void> removeSearchHistory(@AuthenticationPrincipal String emailAddress,
      @PathVariable int index) {
    userService.removeSearchHistory(emailAddress, index);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/playlists")
  public ResponseEntity<Void> createPlaylist(@AuthenticationPrincipal String emailAddress,
      @RequestBody Map<String, String> body) throws EntityAlreadyExistsException {
    String playlistName = body.get("playlistName");
    if (playlistName != null && !playlistName.isBlank()) {
      userService.createPlaylist(emailAddress, playlistName.strip());
    }
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/playlists/{playlistName}/songs")
  public ResponseEntity<Void> addSongToPlaylist(@AuthenticationPrincipal String emailAddress,
      @PathVariable String playlistName, @RequestBody SongIdentifier songIdentifier)
      throws EntityDoesNotExistException {
    SongFileEntity song = songLibraryService.getSongLibraryRoot()
        .getSongById(songIdentifier.getAlbumId(), songIdentifier.getSongId());
    userService.addSongToPlaylist(emailAddress, playlistName, song);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/playlists/{playlistName}/songs")
  public ResponseEntity<Void> removeSongFromPlaylist(@AuthenticationPrincipal String emailAddress,
      @PathVariable String playlistName, @RequestBody SongIdentifier songIdentifier)
      throws EntityDoesNotExistException {
    SongFileEntity song = songLibraryService.getSongLibraryRoot()
        .getSongById(songIdentifier.getAlbumId(), songIdentifier.getSongId());
    userService.removeSongFromPlaylist(emailAddress, playlistName, song);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/playlists/favorites/songs")
  public ResponseEntity<Void> addSongToMyFavoritesPlaylist(
      @AuthenticationPrincipal String emailAddress, @RequestBody SongIdentifier songIdentifier)
      throws EntityDoesNotExistException {
    SongFileEntity song = songLibraryService.getSongLibraryRoot()
        .getSongById(songIdentifier.getAlbumId(), songIdentifier.getSongId());
    userService.addSongToMyFavoritesPlaylist(emailAddress, song);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/playlists/favorites/songs")
  public ResponseEntity<Void> removeSongFromMyFavoritesPlaylist(
      @AuthenticationPrincipal String emailAddress, @RequestBody SongIdentifier songIdentifier)
      throws EntityDoesNotExistException {
    SongFileEntity song = songLibraryService.getSongLibraryRoot()
        .getSongById(songIdentifier.getAlbumId(), songIdentifier.getSongId());
    userService.removeSongFromMyFavoritesPlaylist(emailAddress, song);
    return ResponseEntity.noContent().build();
  }
}
