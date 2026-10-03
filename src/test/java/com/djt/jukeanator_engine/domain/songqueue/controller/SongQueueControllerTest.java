package com.djt.jukeanator_engine.domain.songqueue.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.djt.jukeanator_engine.AbstractControllerTest;
import com.djt.jukeanator_engine.domain.location.service.GeoFenceService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.CheckSongsEligibilityRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongEligibilityDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.service.UserService;

class SongQueueControllerTest extends AbstractControllerTest {

  private static final Integer LOCATION_ID = 7;
  private static final String BASE_PATH = "/api/locations/" + LOCATION_ID + "/song-queue";

  @Mock
  private SongQueueService songQueueService;

  @Mock
  private UserService userService;

  @Mock
  private SongLibraryService songLibraryService;

  // A mock never throws, so the geo-fence never refuses anything here; see
  // SongQueueControllerGeoFenceTest for the fenced behavior.
  @Mock
  private GeoFenceService geoFenceService;

  @InjectMocks
  private SongQueueController songQueueController;

  @Override
  protected Object getController() {
    return songQueueController;
  }

  private static final String WEB_USER = "web@domain.com";

  /** A JWT-style web user -- JwtAuthenticationFilter's principal is the email string. */
  private static Authentication webUser() {
    return new UsernamePasswordAuthenticationToken(WEB_USER, null, List.of());
  }

  private SongQueueEntryDto aQueueEntry() {
    SongDto song = new SongDto(1, "Genre", 2, "Artist", 3, "Album", "/cover.jpg", 4, "Song", 1, 0);
    return new SongQueueEntryDto("su@domain.com", song, 5, "/music/song.mp3");
  }

  @Test
  void getHighestPriority_delegatesToService() throws Exception {
    when(songQueueService.getHighestPriority(LOCATION_ID)).thenReturn(7);

    mockMvc.perform(get(BASE_PATH + "/highestPriority"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(7)));
  }

  @Test
  void getQueuedSongs_returnsListFromService() throws Exception {
    when(songQueueService.getQueuedSongs(LOCATION_ID)).thenReturn(List.of(aQueueEntry()));

    mockMvc.perform(get(BASE_PATH + "/queuedSongs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].songPath", is("/music/song.mp3")));
  }

  @Test
  void isSongEligibleForQueue_passesParams() throws Exception {
    when(songQueueService.isSongEligibleForQueue(LOCATION_ID, 3, 4, 5)).thenReturn("ELIGIBLE");

    mockMvc.perform(get(BASE_PATH + "/isSongEligibleForQueue")
            .param("albumId", "3")
            .param("songId", "4")
            .param("priority", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is("ELIGIBLE")));

    verify(songQueueService).isSongEligibleForQueue(LOCATION_ID, 3, 4, 5);
  }

  @Test
  void checkSongsEligibility_marksOtherLocationsAndKeepsRequestOrder() throws Exception {
    when(songQueueService.checkSongsEligibility(eq(LOCATION_ID), any(), eq(1)))
        .thenReturn(List.of(new SongEligibilityDto(LOCATION_ID, 3, 1, null),
            new SongEligibilityDto(LOCATION_ID, 3, 2, "has already been played")));
    CheckSongsEligibilityRequest request = new CheckSongsEligibilityRequest(
        List.of(new SongIdentifier(LOCATION_ID, 3, 1), new SongIdentifier(99, 3, 9),
            new SongIdentifier(LOCATION_ID, 3, 2)),
        5);

    mockMvc.perform(post(BASE_PATH + "/checkSongsEligibility")
            .principal(webUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", is(3)))
        .andExpect(jsonPath("$[0].songId", is(1)))
        .andExpect(jsonPath("$[0].ineligibleReason").doesNotExist())
        .andExpect(jsonPath("$[1].songId", is(9)))
        .andExpect(jsonPath("$[1].ineligibleReason", is("is not available at this location")))
        .andExpect(jsonPath("$[2].songId", is(2)))
        .andExpect(jsonPath("$[2].ineligibleReason", is("has already been played")));

    // Only this location's songs are checked, always as normal plays (priority 1).
    verify(songQueueService).checkSongsEligibility(LOCATION_ID,
        List.of(new SongIdentifier(LOCATION_ID, 3, 1), new SongIdentifier(LOCATION_ID, 3, 2)), 1);
  }

  @Test
  void checkSongsEligibility_onlyOtherLocations_doesNotCallService() throws Exception {
    CheckSongsEligibilityRequest request =
        new CheckSongsEligibilityRequest(List.of(new SongIdentifier(99, 3, 9)), 1);

    mockMvc.perform(post(BASE_PATH + "/checkSongsEligibility")
            .principal(webUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].ineligibleReason", is("is not available at this location")));

    verify(songQueueService, never()).checkSongsEligibility(any(), any(), any());
  }

  @Test
  void addSongToQueue_passesRequest() throws Exception {
    // This location is the controller's own -- addSong therefore takes the event-driven credit
    // charge path (see SongQueueController's class javadoc), not the explicit one.
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    AddSongToQueueRequest request = new AddSongToQueueRequest("user", 3, 4, 5, false);
    when(songQueueService.addSongToQueue(any(), any(AddSongToQueueRequest.class)))
        .thenReturn(aQueueEntry());

    mockMvc.perform(post(BASE_PATH + "/addSong")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.songPath", is("/music/song.mp3")));
  }

  @Test
  void addAlbumToQueue_passesRequest() throws Exception {
    AddAlbumToQueueRequest request = new AddAlbumToQueueRequest("user", 3, 5);
    when(songQueueService.addAlbumToQueue(any(), any(AddAlbumToQueueRequest.class)))
        .thenReturn(List.of(aQueueEntry()));

    mockMvc.perform(post(BASE_PATH + "/addAlbum")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].songPath", is("/music/song.mp3")));
  }

  @Test
  void addMultipleSongsToQueue_passesRequest() throws Exception {
    AddMultipleSongsToQueueRequest request =
        new AddMultipleSongsToQueueRequest("user", List.of(), 5);
    when(songQueueService.addMultipleSongsToQueue(any(), any(AddMultipleSongsToQueueRequest.class)))
        .thenReturn(List.of(aQueueEntry()));

    mockMvc.perform(post(BASE_PATH + "/addMultipleSongs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void addMultipleSongsToQueue_webUser_sendsOneCappedCommandAndChargesEachQueuedSong()
      throws Exception {
    when(userService.getAffordableQueueAddCount(WEB_USER, LOCATION_ID, 1, false)).thenReturn(4);
    when(songQueueService.addMultipleSongsToQueue(any(), any(AddMultipleSongsToQueueRequest.class)))
        .thenReturn(List.of(aQueueEntry(), aQueueEntry()));
    AddMultipleSongsToQueueRequest request = new AddMultipleSongsToQueueRequest("someone-else",
        List.of(new SongIdentifier(LOCATION_ID, 3, 1), new SongIdentifier(99, 3, 9),
            new SongIdentifier(LOCATION_ID, 3, 2)),
        5);

    mockMvc.perform(post(BASE_PATH + "/addMultipleSongs")
            .principal(webUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", is(2)));

    // One command: only this location's songs, as the logged-in user at normal priority,
    // skipping ineligible songs and capped at what the user can afford.
    verify(songQueueService).addMultipleSongsToQueue(LOCATION_ID,
        new AddMultipleSongsToQueueRequest(WEB_USER,
            List.of(new SongIdentifier(LOCATION_ID, 3, 1), new SongIdentifier(LOCATION_ID, 3, 2)),
            1, true, 4));
    verify(songQueueService, never()).addSongToQueue(any(), any());
    verify(userService, times(2)).handleSongAddedToQueueEvent(any(), eq(LOCATION_ID));
  }

  @Test
  void addMultipleSongsToQueue_webUserWhoCannotAffordOneSong_queuesNothing() throws Exception {
    when(userService.getAffordableQueueAddCount(WEB_USER, LOCATION_ID, 1, false)).thenReturn(0);
    AddMultipleSongsToQueueRequest request = new AddMultipleSongsToQueueRequest(null,
        List.of(new SongIdentifier(LOCATION_ID, 3, 1)), 1);

    mockMvc.perform(post(BASE_PATH + "/addMultipleSongs")
            .principal(webUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", is(0)));

    verify(songQueueService, never()).addMultipleSongsToQueue(any(), any());
    verify(userService, never()).handleSongAddedToQueueEvent(any(), any());
  }

  @Test
  void flushQueue_delegatesToService() throws Exception {
    when(songQueueService.flushQueue(LOCATION_ID)).thenReturn(3);

    mockMvc.perform(post(BASE_PATH + "/flushQueue"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(3)));
  }

  @Test
  void randomizeQueue_delegatesToService() throws Exception {
    when(songQueueService.randomizeQueue(LOCATION_ID)).thenReturn(3);

    mockMvc.perform(post(BASE_PATH + "/randomizeQueue"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(3)));
  }

  @Test
  void moveSongUpInQueue_passesRequest() throws Exception {
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(3, 4);
    when(songQueueService.moveSongUpInQueue(any(), any(ChangeSongQueueRequest.class)))
        .thenReturn(1);

    mockMvc.perform(post(BASE_PATH + "/moveSongUpInQueue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(1)));
  }

  @Test
  void moveSongDownInQueue_passesRequest() throws Exception {
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(3, 4);
    when(songQueueService.moveSongDownInQueue(any(), any(ChangeSongQueueRequest.class)))
        .thenReturn(1);

    mockMvc.perform(post(BASE_PATH + "/moveSongDownInQueue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(1)));
  }

  @Test
  void removeSongDownFromQueue_passesRequest() throws Exception {
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(3, 4);
    when(songQueueService.removeSongDownFromQueue(any(), any(ChangeSongQueueRequest.class)))
        .thenReturn(1);

    mockMvc.perform(post(BASE_PATH + "/removeSongDownFromQueue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(1)));
  }

  @Test
  void saveQueueAsPlaylist_passesFilename() throws Exception {
    when(songQueueService.saveQueueAsPlaylist(LOCATION_ID, "myPlaylist")).thenReturn(1);

    mockMvc.perform(post(BASE_PATH + "/saveQueueAsPlaylist")
            .contentType(MediaType.TEXT_PLAIN)
            .content("myPlaylist"))
        .andExpect(status().isOk());

    verify(songQueueService).saveQueueAsPlaylist(LOCATION_ID, "myPlaylist");
  }

  @Test
  void loadPlaylistIntoQueue_passesRequest() throws Exception {
    LoadPlaylistIntoQueueRequest request = new LoadPlaylistIntoQueueRequest("user", "myPlaylist");
    when(songQueueService.loadPlaylistIntoQueue(any(), any(LoadPlaylistIntoQueueRequest.class)))
        .thenReturn(5);

    mockMvc.perform(post(BASE_PATH + "/loadPlaylistIntoQueue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(5)));
  }
}
