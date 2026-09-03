package com.djt.jukeanator_engine.domain.user.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
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
import com.djt.jukeanator_engine.domain.user.service.UserService;

class UserControllerTest extends AbstractControllerTest {

  @Mock
  private UserService userService;

  @Mock
  private SongLibraryService songLibraryService;

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

  @Test
  void createPlaylist_delegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    when(userService.createPlaylist("jane@example.com", "Road Trip")).thenReturn(true);

    mockMvc.perform(post("/api/users/playlists")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("playlistName", "Road Trip"))))
        .andExpect(status().isNoContent());

    verify(userService).createPlaylist("jane@example.com", "Road Trip");
  }

  @Test
  void addSongToPlaylist_resolvesSongAndDelegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    SongFileEntity song = new SongFileEntity();
    RootFolderEntity mockRoot = mock(RootFolderEntity.class);
    when(songLibraryService.getSongLibraryRoot(any())).thenReturn(mockRoot);
    when(mockRoot.getSongById(3, 4)).thenReturn(song);
    when(userService.addSongToPlaylist("jane@example.com", "Favorites", 9, song))
        .thenReturn(true);

    mockMvc.perform(post("/api/users/playlists/Favorites/songs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SongIdentifier(9, 3, 4))))
        .andExpect(status().isNoContent());

    verify(userService).addSongToPlaylist("jane@example.com", "Favorites", 9, song);
  }

  @Test
  void removeSongFromPlaylist_resolvesSongAndDelegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    SongFileEntity song = new SongFileEntity();
    RootFolderEntity mockRoot = mock(RootFolderEntity.class);
    when(songLibraryService.getSongLibraryRoot(any())).thenReturn(mockRoot);
    when(mockRoot.getSongById(3, 4)).thenReturn(song);
    when(userService.removeSongFromPlaylist("jane@example.com", "Favorites", 9, song))
        .thenReturn(true);

    mockMvc.perform(delete("/api/users/playlists/Favorites/songs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SongIdentifier(9, 3, 4))))
        .andExpect(status().isNoContent());

    verify(userService).removeSongFromPlaylist("jane@example.com", "Favorites", 9, song);
  }

  @Test
  void addSongToMyFavoritesPlaylist_resolvesSongAndDelegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    SongFileEntity song = new SongFileEntity();
    RootFolderEntity mockRoot = mock(RootFolderEntity.class);
    when(songLibraryService.getSongLibraryRoot(any())).thenReturn(mockRoot);
    when(mockRoot.getSongById(3, 4)).thenReturn(song);
    when(userService.addSongToMyFavoritesPlaylist("jane@example.com", 9, song))
        .thenReturn(true);

    mockMvc.perform(post("/api/users/playlists/favorites/songs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SongIdentifier(9, 3, 4))))
        .andExpect(status().isNoContent());

    verify(userService).addSongToMyFavoritesPlaylist("jane@example.com", 9, song);
  }

  @Test
  void removeSongFromMyFavoritesPlaylist_resolvesSongAndDelegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    SongFileEntity song = new SongFileEntity();
    RootFolderEntity mockRoot = mock(RootFolderEntity.class);
    when(songLibraryService.getSongLibraryRoot(any())).thenReturn(mockRoot);
    when(mockRoot.getSongById(3, 4)).thenReturn(song);
    when(userService.removeSongFromMyFavoritesPlaylist("jane@example.com", 9, song))
        .thenReturn(true);

    mockMvc.perform(delete("/api/users/playlists/favorites/songs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SongIdentifier(9, 3, 4))))
        .andExpect(status().isNoContent());

    verify(userService).removeSongFromMyFavoritesPlaylist("jane@example.com", 9, song);
  }

  @Test
  void getPublicHomePage_returnsHomePageDto() throws Exception {
    ArtistDto artist = new ArtistDto(1, "Artist", "/artist.jpg", 2, 3, 10, List.of());
    SongDto song = new SongDto(1, "Genre", 1, "Artist", 2, "Album", "/cover.jpg", 3, "Song", 1, 5);
    when(userService.getPublicHomePage()).thenReturn(new HomePageDto(List.of(artist), List.of(song)));

    mockMvc.perform(get("/api/users/home-public"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.artistsHotHere[0].artistName", is("Artist")))
        .andExpect(jsonPath("$.songsHotHere[0].songName", is("Song")));
  }

  @Test
  void getOwnLocation_returnsOwnLocationDtoBuiltFromEntity() throws Exception {
    LocationEntity location = new LocationEntity(7, "Bar Downtown", 40.0, -73.0, "hash");
    location.setLogoName("logo.png");
    when(songLibraryService.getOwnLocation()).thenReturn(location);

    mockMvc.perform(get("/api/users/own-location"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locationId", is(7)))
        .andExpect(jsonPath("$.name", is("Bar Downtown")))
        .andExpect(jsonPath("$.logoName", is("logo.png")));
  }

  @Test
  void getOwnLocation_returnsEmptyBodyWhenMasterOwnsNoLocation() throws Exception {
    when(songLibraryService.getOwnLocation()).thenReturn(null);

    mockMvc.perform(get("/api/users/own-location"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void getHomePage_returnsUserHomePageDto() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    UserHomePageDto homePage = new UserHomePageDto(List.of(), List.of("My Favorites"), List.of(),
        List.of(), List.of("beatles"));
    when(userService.getHomePage("jane@example.com")).thenReturn(homePage);

    mockMvc.perform(get("/api/users/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myPlaylists[0]", is("My Favorites")))
        .andExpect(jsonPath("$.searchHistory[0]", is("beatles")));
  }

  @Test
  void getCreditPackages_returnsListFromService() throws Exception {
    when(userService.getCreditPackages()).thenReturn(
        List.of(new CreditPackageDto("small", 10, 0, new BigDecimal("4.99"), null)));

    mockMvc.perform(get("/api/users/credit-packages"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", is("small")))
        .andExpect(jsonPath("$[0].credits", is(10)))
        .andExpect(jsonPath("$[0].priceUsd", is(4.99)));
  }

  @Test
  void getPlaylists_returnsSummariesFromService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    when(userService.getPlaylists("jane@example.com"))
        .thenReturn(List.of(new PlaylistSummaryDto("My Favorites", 3, 5)));

    mockMvc.perform(get("/api/users/playlists"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name", is("My Favorites")))
        .andExpect(jsonPath("$[0].songCount", is(3)))
        .andExpect(jsonPath("$[0].firstSongAlbumId", is(5)));
  }

  @Test
  void getFavoriteSongIdentifiers_returnsListFromService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    when(userService.getFavoriteSongIdentifiers("jane@example.com"))
        .thenReturn(List.of(new SongIdentifier(7, 3, 4)));

    mockMvc.perform(get("/api/users/playlists/favorites/songs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].locationId", is(7)))
        .andExpect(jsonPath("$[0].albumId", is(3)))
        .andExpect(jsonPath("$[0].songId", is(4)));
  }

  @Test
  void getPlaylistSongs_returnsSongsFromService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    SongDto song = new SongDto(1, "Genre", 1, "Artist", 2, "Album", "/cover.jpg", 3, "Song", 1, 5);
    when(userService.getPlaylistSongs("jane@example.com", "My Favorites"))
        .thenReturn(List.of(song));

    mockMvc.perform(get("/api/users/playlists/{playlistName}/songs", "My Favorites"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].songName", is("Song")));
  }

  @Test
  void getOwnLocationId_returnsIdFromSongLibraryService() throws Exception {
    when(songLibraryService.getOwnLocationId()).thenReturn(7);

    mockMvc.perform(get("/api/users/own-location-id"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(7)));
  }

  @Test
  void updateProfile_returnsUpdatedProfile() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    UpdateProfileRequest request = new UpdateProfileRequest("Janet", "Doe");
    UserProfileDto updated =
        new UserProfileDto(1, "Janet", "Doe", "jane@example.com", 10, null, List.of());
    when(userService.updateProfile("jane@example.com", request)).thenReturn(updated);

    mockMvc.perform(put("/api/users/me")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName", is("Janet")));
  }

  @Test
  void deleteAccount_delegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));

    mockMvc.perform(delete("/api/users/me"))
        .andExpect(status().isNoContent());

    verify(userService).deleteAccount("jane@example.com");
  }

  @Test
  void changePassword_delegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    ChangePasswordRequest request = new ChangePasswordRequest("oldpass", "newpass");

    mockMvc.perform(post("/api/users/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    verify(userService).changePassword("jane@example.com", request);
  }

  @Test
  void addFunds_delegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    AddFundsRequest request = new AddFundsRequest("small");

    mockMvc.perform(post("/api/users/add-funds")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    verify(userService).addFunds("jane@example.com", request);
  }

  @Test
  void getSearchHistory_returnsListFromService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    when(userService.getSearchHistory("jane@example.com")).thenReturn(List.of("beatles"));

    mockMvc.perform(get("/api/users/search-history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]", is("beatles")));
  }

  @Test
  void addSearchHistory_delegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));

    mockMvc.perform(post("/api/users/search-history")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("query", "beatles"))))
        .andExpect(status().isNoContent());

    verify(userService).addSearchHistory("jane@example.com", "beatles");
  }

  @Test
  void removeSearchHistory_delegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));

    mockMvc.perform(delete("/api/users/search-history/{index}", 2))
        .andExpect(status().isNoContent());

    verify(userService).removeSearchHistory("jane@example.com", 2);
  }

  @Test
  void deletePlaylist_delegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    when(userService.deletePlaylist("jane@example.com", "Road Trip")).thenReturn(true);

    mockMvc.perform(delete("/api/users/playlists/{playlistName}", "Road Trip"))
        .andExpect(status().isNoContent());

    verify(userService).deletePlaylist("jane@example.com", "Road Trip");
  }

  @Test
  void reorderPlaylistSongs_delegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    List<SongIdentifier> songs = List.of(new SongIdentifier(null, 3, 4), new SongIdentifier(null, 3, 5));

    mockMvc.perform(put("/api/users/playlists/{playlistName}/songs", "My Favorites")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(songs)))
        .andExpect(status().isNoContent());

    verify(userService).reorderPlaylistSongs("jane@example.com", "My Favorites", songs);
  }
}
