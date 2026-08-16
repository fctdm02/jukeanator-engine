package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.List;
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
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.dto.CreditTransactionDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.PlaylistSummaryDto;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserHomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;
import com.djt.jukeanator_engine.domain.user.event.UserCreditsChangedEvent;
import com.djt.jukeanator_engine.domain.user.exception.UserServiceException;
import com.djt.jukeanator_engine.domain.user.model.CreditTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;

/**
 * Covers every method declared on {@link UserService}. Most cases run against
 * {@link #userServiceImpl}, a locally constructed instance with fully mocked dependencies (fast,
 * deterministic, no Spring context / real password hashing / real JWTs needed). The auth-flow
 * happy path ({@code register}/{@code login}/{@code getProfile}) is instead exercised end-to-end
 * against the Spring-managed {@link #userService} bean in {@link #lifecycle()}, since that path
 * benefits from real password encoding and JWT generation.
 *
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml
public class UserServiceTest extends AbstractServiceIntegrationTest {

  private static final String REGISTERED_EMAIL = "jane.doe@example.com";
  private static final String REGISTERED_PASSWORD_HASH = "hashed-password";

  @Autowired
  private UserService userService;

  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private JwtUtil jwtUtil;
  private ApplicationEventPublisher eventPublisher;
  private SongLibraryService songLibraryService;
  private UserRootEntity userRoot;
  private UserServiceImpl userServiceImpl;

  @BeforeEach
  void setUp() throws EntityDoesNotExistException {

    userRepository = mock(UserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    jwtUtil = mock(JwtUtil.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    songLibraryService = mock(SongLibraryService.class);

    userRoot = new UserRootEntity();
    userRoot.addUser(new UserEntity(Integer.valueOf(1), "Jane", "Doe", REGISTERED_EMAIL,
        REGISTERED_PASSWORD_HASH, Integer.valueOf(6), UserRole.ROLE_USER));

    when(userRepository.loadAggregateRoot(anyString())).thenReturn(userRoot);

    userServiceImpl = new UserServiceImpl("test-root", userRepository, passwordEncoder, jwtUtil,
        eventPublisher, songLibraryService, false);
  }

  private UserEntity registeredUser() {
    return userRoot.getUserByEmailAddressNullIfNotExists(REGISTERED_EMAIL);
  }

  /** Minimal {@link SongFileEntity} whose {@code getAlbum().getPersistentIdentity()} and own
   *  {@code getPersistentIdentity()} resolve to the given ids, matching what
   *  {@link UserEntity#addSongToPlaylist} needs to build a {@link SongIdentifier}. */
  private static SongFileEntity buildSong(int albumId, int songId) {
    AlbumFolderEntity album = new AlbumFolderEntity(null, "Album" + albumId);
    album.setPersistentIdentity(albumId);
    SongFileEntity song = new SongFileEntity(album, "Song" + songId + ".mp3");
    song.setPersistentIdentity(songId);
    return song;
  }

  private static SongDto buildSongDto(int albumId, int songId) {
    return new SongDto(null, null, null, "Artist", albumId, "Album", null, songId, "Song", 1, 0);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // AUTH — register / addAdminUser / login / getProfile
  // ─────────────────────────────────────────────────────────────────────────

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
  void addAdminUser_createsUserWithAdminRoleAndPersists() {

    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("admin-token");

    RegisterRequest request = new RegisterRequest("Ada", "Min", "ada.min@example.com", "password123");

    AuthResponse response = userServiceImpl.addAdminUser(request);

    assertNotNull(response);
    assertEquals("admin-token", response.token());
    assertEquals("ada.min@example.com", response.emailAddress());
    assertEquals(UserRole.ROLE_ADMIN.name(), response.role());

    UserEntity created = userRoot.getUserByEmailAddressNullIfNotExists("ada.min@example.com");
    assertNotNull(created, "admin user should be added to the user root");
    assertEquals(UserRole.ROLE_ADMIN, created.getRole());

    verify(userRepository).storeAggregateRoot(userRoot);
    verify(jwtUtil).generateToken("ada.min@example.com", UserRole.ROLE_ADMIN.name());
  }

  @Test
  void addAdminUser_throwsWhenEmailAlreadyRegistered() {

    RegisterRequest request = new RegisterRequest("Ada", "Min", REGISTERED_EMAIL, "password123");

    assertThrows(UserServiceException.class, () -> userServiceImpl.addAdminUser(request));
  }

  @Test
  void addAdminUser_doesNotDowngradeRegularRegisterToAdmin() {

    when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("user-token");

    RegisterRequest request = new RegisterRequest("Reg", "User", "reg.user@example.com", "password123");

    userServiceImpl.register(request);

    UserEntity created = userRoot.getUserByEmailAddressNullIfNotExists("reg.user@example.com");
    assertEquals(UserRole.ROLE_USER, created.getRole());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ACCOUNT MANAGEMENT — changePassword / deleteAccount / addFunds / updateProfile
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void changePassword_updatesHashWhenCurrentPasswordMatchesAndPersists() {

    when(passwordEncoder.matches("oldPass", REGISTERED_PASSWORD_HASH)).thenReturn(true);
    when(passwordEncoder.encode("newPass")).thenReturn("new-hashed-password");

    userServiceImpl.changePassword(REGISTERED_EMAIL, new ChangePasswordRequest("oldPass", "newPass"));

    assertEquals("new-hashed-password", registeredUser().getPasswordHash());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void changePassword_throwsWhenCurrentPasswordIncorrect() {

    when(passwordEncoder.matches("wrongPass", REGISTERED_PASSWORD_HASH)).thenReturn(false);

    assertThrows(UserServiceException.class, () -> userServiceImpl.changePassword(REGISTERED_EMAIL,
        new ChangePasswordRequest("wrongPass", "newPass")));
  }

  @Test
  void changePassword_throwsForUnknownEmailAddress() {

    assertThrows(InvalidPrincipalException.class, () -> userServiceImpl
        .changePassword("unknown@example.com", new ChangePasswordRequest("old", "new")));
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

  @Test
  void addFunds_throwsNotYetImplemented() {

    assertThrows(UserServiceException.class,
        () -> userServiceImpl.addFunds(REGISTERED_EMAIL, new AddFundsRequest("pkg-28")));
  }

  @Test
  void updateProfile_updatesFirstAndLastNameAndPersists() {

    UserProfileDto updated =
        userServiceImpl.updateProfile(REGISTERED_EMAIL, new UpdateProfileRequest("Janet", "Doerson"));

    assertEquals("Janet", updated.firstName());
    assertEquals("Doerson", updated.lastName());
    assertEquals("Janet", registeredUser().getFirstName());
    assertEquals("Doerson", registeredUser().getLastName());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HOME PAGE / CREDIT PACKAGES
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getPublicHomePage_returnsHotHereArtistsAndSongsFromSongLibrary() {

    when(songLibraryService.getMusicByPopularity())
        .thenReturn(new SearchResultDto(List.of(), List.of(), List.of()));

    var homePage = userServiceImpl.getPublicHomePage();

    assertNotNull(homePage);
    assertTrue(homePage.getArtistsHotHere().isEmpty());
    assertTrue(homePage.getSongsHotHere().isEmpty());
  }

  @Test
  void getHomePage_returnsPlaylistNamesAndSearchHistoryForRegisteredUser() {

    when(songLibraryService.getMusicByPopularity())
        .thenReturn(new SearchResultDto(List.of(), List.of(), List.of()));
    registeredUser().addToSearchHistory("beatles", 10);

    UserHomePageDto homePage = userServiceImpl.getHomePage(REGISTERED_EMAIL);

    assertNotNull(homePage);
    assertEquals(List.of(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME), homePage.getMyPlaylists());
    assertEquals(List.of("beatles"), homePage.getSearchHistory());
    assertTrue(homePage.getMyRecentPlays().isEmpty());
  }

  @Test
  void getHomePage_throwsForUnknownEmailAddress() {

    assertThrows(InvalidPrincipalException.class, () -> userServiceImpl.getHomePage("unknown@example.com"));
  }

  @Test
  void getCreditPackages_returnsThreeFixedPackages() {

    List<CreditPackageDto> packages = userServiceImpl.getCreditPackages();

    assertEquals(3, packages.size());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // SEARCH HISTORY
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getSearchHistory_returnsUsersSearchHistory() {

    registeredUser().addToSearchHistory("queen", 10);

    assertEquals(List.of("queen"), userServiceImpl.getSearchHistory(REGISTERED_EMAIL));
  }

  @Test
  void addSearchHistory_addsQueryAndPersists() {

    userServiceImpl.addSearchHistory(REGISTERED_EMAIL, "queen");

    assertEquals(List.of("queen"), registeredUser().getSearchHistory());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void removeSearchHistory_removesEntryAtIndexAndPersists() {

    UserEntity user = registeredUser();
    user.addToSearchHistory("queen", 10);
    user.addToSearchHistory("beatles", 10); // inserted at index 0 -> ["beatles", "queen"]

    userServiceImpl.removeSearchHistory(REGISTERED_EMAIL, 0);

    assertEquals(List.of("queen"), user.getSearchHistory());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PLAYLISTS
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void createPlaylist_createsNewPlaylistForUserAndPersists() throws Exception {

    boolean result = userServiceImpl.createPlaylist(REGISTERED_EMAIL, "Road Trip");

    assertTrue(result);
    assertNotNull(registeredUser().getPlaylistByNameNullIfNotExists("Road Trip"));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void addSongToPlaylist_addsSongAndPersists() throws Exception {

    registeredUser().createPlaylist("Road Trip");
    SongFileEntity song = buildSong(1, 100);

    boolean result = userServiceImpl.addSongToPlaylist(REGISTERED_EMAIL, "Road Trip", song);

    assertTrue(result);
    assertEquals(List.of(new SongIdentifier(1, 100)),
        registeredUser().getPlaylistByName("Road Trip").getSongs());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void removeSongFromPlaylist_removesSongAndPersists() throws Exception {

    PlaylistEntity playlist = registeredUser().createPlaylist("Road Trip");
    playlist.addSong(new SongIdentifier(1, 100));
    SongFileEntity song = buildSong(1, 100);

    boolean result = userServiceImpl.removeSongFromPlaylist(REGISTERED_EMAIL, "Road Trip", song);

    assertTrue(result);
    assertTrue(playlist.getSongs().isEmpty());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void deletePlaylist_removesPlaylistAndPersists() throws Exception {

    registeredUser().createPlaylist("Road Trip");

    boolean result = userServiceImpl.deletePlaylist(REGISTERED_EMAIL, "Road Trip");

    assertTrue(result);
    assertNull(registeredUser().getPlaylistByNameNullIfNotExists("Road Trip"));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void addSongToMyFavoritesPlaylist_addsSongToFavoritesAndPersists() throws Exception {

    SongFileEntity song = buildSong(2, 200);

    boolean result = userServiceImpl.addSongToMyFavoritesPlaylist(REGISTERED_EMAIL, song);

    assertTrue(result);
    assertTrue(registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .getSongs().contains(new SongIdentifier(2, 200)));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void removeSongFromMyFavoritesPlaylist_removesSongFromFavoritesAndPersists() throws Exception {

    registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .addSong(new SongIdentifier(2, 200));
    SongFileEntity song = buildSong(2, 200);

    boolean result = userServiceImpl.removeSongFromMyFavoritesPlaylist(REGISTERED_EMAIL, song);

    assertTrue(result);
    assertFalse(registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .getSongs().contains(new SongIdentifier(2, 200)));
  }

  @Test
  void getPlaylists_returnsSummaryForEachPlaylist() {

    List<PlaylistSummaryDto> playlists = userServiceImpl.getPlaylists(REGISTERED_EMAIL);

    assertEquals(1, playlists.size());
    assertEquals(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, playlists.get(0).getName());
    assertEquals(0, playlists.get(0).getSongCount());
  }

  @Test
  void getPlaylistSongs_returnsSongsResolvedFromSongLibrary() throws Exception {

    registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .addSong(new SongIdentifier(3, 300));
    SongDto songDto = buildSongDto(3, 300);
    when(songLibraryService.getSongById(3, 300)).thenReturn(songDto);

    List<SongDto> songs =
        userServiceImpl.getPlaylistSongs(REGISTERED_EMAIL, PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);

    assertEquals(List.of(songDto), songs);
  }

  @Test
  void reorderPlaylistSongs_keepsOnlyExistingSongsInGivenOrderAndPersists() throws Exception {

    PlaylistEntity playlist = registeredUser().getPlaylistByName(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME);
    SongIdentifier a = new SongIdentifier(1, 1);
    SongIdentifier b = new SongIdentifier(2, 2);
    playlist.addSong(a);
    playlist.addSong(b);

    userServiceImpl.reorderPlaylistSongs(REGISTERED_EMAIL, PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME,
        List.of(b, a, new SongIdentifier(9, 9)));

    assertEquals(List.of(b, a), playlist.getSongs());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void getFavoriteSongIdentifiers_returnsFavoritesPlaylistSongs() {

    SongIdentifier fav = new SongIdentifier(4, 400);
    registeredUser().getPlaylistByNameNullIfNotExists(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME)
        .addSong(fav);

    assertEquals(List.of(fav), userServiceImpl.getFavoriteSongIdentifiers(REGISTERED_EMAIL));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // QUEUE EVENTS / CREDIT CHARGING / CREDIT LEDGER
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void handleSongAddedToQueueEvent_addsToHistoryAndDeductsCreditsForWebUser() {

    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(5, 500), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry));

    UserEntity user = registeredUser();
    assertTrue(user.getSongPlayHistory().contains(new SongIdentifier(5, 500)));
    assertEquals(4, user.getNumCredits()); // 6 - (priority 1 * 2 credits)
    verify(eventPublisher).publishEvent(any(UserCreditsChangedEvent.class));
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void handleSongAddedToQueueEvent_withLocationId_tagsCreditTransactionWithLocation() {

    SongQueueEntryDto entry = new SongQueueEntryDto(REGISTERED_EMAIL, buildSongDto(6, 600), 1, null);

    userServiceImpl.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry), "location-42");

    CreditTransactionEntity transaction = registeredUser().getTransactions().iterator().next();
    assertEquals("location-42", transaction.getLocationId());
  }

  @Test
  void chargeCreditsForQueueAction_deductsCreditsBasedOnPriorityAndPersists() {

    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 2);

    // cost = max(2, priority(2) * 6) = 12, floored at zero against a starting balance of 6
    assertEquals(0, registeredUser().getNumCredits());
    verify(userRepository).storeAggregateRoot(userRoot);
  }

  @Test
  void chargeCreditsForQueueAction_withLocationId_tagsCreditTransactionWithLocation() {

    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, "location-7");

    CreditTransactionEntity transaction = registeredUser().getTransactions().iterator().next();
    assertEquals("location-7", transaction.getLocationId());
  }

  @Test
  void getCreditLedgerForLocation_returnsOnlyMatchingLocationWithinTimeRange() {

    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, "loc-A");
    userServiceImpl.chargeCreditsForQueueAction(REGISTERED_EMAIL, 1, "loc-B");

    Instant from = Instant.now().minusSeconds(60);
    Instant to = Instant.now().plusSeconds(60);

    List<CreditTransactionDto> ledger = userServiceImpl.getCreditLedgerForLocation("loc-A", from, to);

    assertEquals(1, ledger.size());
    assertEquals("loc-A", ledger.get(0).locationId());
  }
}
