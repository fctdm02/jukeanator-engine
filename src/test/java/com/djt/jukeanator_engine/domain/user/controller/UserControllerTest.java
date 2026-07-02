package com.djt.jukeanator_engine.domain.user.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
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
    when(songLibraryService.getSongLibraryRoot()).thenReturn(mockRoot);
    when(mockRoot.getSongById(3, 4)).thenReturn(song);
    when(userService.addSongToPlaylist("jane@example.com", "Favorites", song)).thenReturn(true);

    mockMvc.perform(post("/api/users/playlists/Favorites/songs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SongIdentifier(3, 4))))
        .andExpect(status().isNoContent());

    verify(userService).addSongToPlaylist("jane@example.com", "Favorites", song);
  }

  @Test
  void removeSongFromPlaylist_resolvesSongAndDelegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    SongFileEntity song = new SongFileEntity();
    RootFolderEntity mockRoot = mock(RootFolderEntity.class);
    when(songLibraryService.getSongLibraryRoot()).thenReturn(mockRoot);
    when(mockRoot.getSongById(3, 4)).thenReturn(song);
    when(userService.removeSongFromPlaylist("jane@example.com", "Favorites", song))
        .thenReturn(true);

    mockMvc.perform(delete("/api/users/playlists/Favorites/songs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SongIdentifier(3, 4))))
        .andExpect(status().isNoContent());

    verify(userService).removeSongFromPlaylist("jane@example.com", "Favorites", song);
  }

  @Test
  void addSongToMyFavoritesPlaylist_resolvesSongAndDelegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    SongFileEntity song = new SongFileEntity();
    RootFolderEntity mockRoot = mock(RootFolderEntity.class);
    when(songLibraryService.getSongLibraryRoot()).thenReturn(mockRoot);
    when(mockRoot.getSongById(3, 4)).thenReturn(song);
    when(userService.addSongToMyFavoritesPlaylist("jane@example.com", song)).thenReturn(true);

    mockMvc.perform(post("/api/users/playlists/favorites/songs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SongIdentifier(3, 4))))
        .andExpect(status().isNoContent());

    verify(userService).addSongToMyFavoritesPlaylist("jane@example.com", song);
  }

  @Test
  void removeSongFromMyFavoritesPlaylist_resolvesSongAndDelegatesToService() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("jane@example.com", null, List.of()));
    SongFileEntity song = new SongFileEntity();
    RootFolderEntity mockRoot = mock(RootFolderEntity.class);
    when(songLibraryService.getSongLibraryRoot()).thenReturn(mockRoot);
    when(mockRoot.getSongById(3, 4)).thenReturn(song);
    when(userService.removeSongFromMyFavoritesPlaylist("jane@example.com", song))
        .thenReturn(true);

    mockMvc.perform(delete("/api/users/playlists/favorites/songs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SongIdentifier(3, 4))))
        .andExpect(status().isNoContent());

    verify(userService).removeSongFromMyFavoritesPlaylist("jane@example.com", song);
  }
}
