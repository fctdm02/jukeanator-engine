package com.djt.jukeanator_engine.domain.songqueue.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.djt.jukeanator_engine.AbstractControllerTest;
import com.djt.jukeanator_engine.domain.location.exception.LocationOfflineException;
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
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.exception.InsufficientCreditsException;
import com.djt.jukeanator_engine.domain.user.exception.QueueAccessDeniedException;
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
    AddSongToQueueRequest request = new AddSongToQueueRequest("user", 3, 4, 5, false);
    when(songQueueService.addSongToQueue(any(), any(AddSongToQueueRequest.class)))
        .thenReturn(aQueueEntry());

    mockMvc.perform(post(BASE_PATH + "/addSong")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.songPath", is("/music/song.mp3")));
  }

  // ── Charged web-user operations: refused before they run unless affordable ──

  private org.springframework.test.web.servlet.ResultActions postAsWebUser(String endpoint,
      Object body) throws Exception {
    return mockMvc.perform(post(BASE_PATH + "/" + endpoint)
        .principal(webUser())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)));
  }

  @Test
  void addSong_webUserWhoCannotAffordIt_isRefusedWith402BeforeAnythingIsQueued() throws Exception {
    doThrow(new InsufficientCreditsException(2, 1)).when(userService)
        .requireAffordableQueueAdd(WEB_USER, LOCATION_ID, 1, false);

    postAsWebUser("addSong", new AddSongToQueueRequest(null, 3, 4, 1, false))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.error", is("InsufficientCreditsException")))
        .andExpect(jsonPath("$.message",
            is("This costs 2 credits, but your balance is 1 credits. Add funds to continue.")));

    verify(songQueueService, never()).addSongToQueue(any(), any());
    verify(userService, never()).handleSongAddedToQueueEvent(any(), any());
  }

  @Test
  void addSong_webUserAtOwnLocation_isCheckedThenQueuedAsThemselves_andChargedOnlyOnce()
      throws Exception {
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    when(songQueueService.addSongToQueue(any(), any(AddSongToQueueRequest.class)))
        .thenReturn(aQueueEntry());

    postAsWebUser("addSong", new AddSongToQueueRequest("someone-else", 3, 4, 2, true))
        .andExpect(status().isOk());

    // The check uses the request's own priority and play type, and runs before the add; the
    // queued entry is always the logged-in user's, never the body's claimed username.
    InOrder inOrder = inOrder(userService, songQueueService);
    inOrder.verify(userService).requireAffordableQueueAdd(WEB_USER, LOCATION_ID, 2, true);
    inOrder.verify(songQueueService).addSongToQueue(LOCATION_ID,
        new AddSongToQueueRequest(WEB_USER, 3, 4, 2, true));
    // Own location: the queue's own SongAddedToQueueEvent charges -- charging here too would
    // double-charge.
    verify(userService, never()).handleSongAddedToQueueEvent(any(), any());
  }

  @Test
  void addSong_webUserAtAnotherInstancesLocation_isChargedExplicitlyForThatLocation()
      throws Exception {
    when(songLibraryService.getOwnLocationId()).thenReturn(null); // master owns no location
    SongQueueEntryDto entry = aQueueEntry();
    when(songQueueService.addSongToQueue(any(), any(AddSongToQueueRequest.class)))
        .thenReturn(entry);

    postAsWebUser("addSong", new AddSongToQueueRequest(null, 3, 4, 1, false))
        .andExpect(status().isOk());

    InOrder inOrder = inOrder(userService, songQueueService);
    inOrder.verify(userService).requireAffordableQueueAdd(WEB_USER, LOCATION_ID, 1, false);
    inOrder.verify(songQueueService).addSongToQueue(eq(LOCATION_ID), any());
    inOrder.verify(userService).handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, false),
        LOCATION_ID);
  }

  @Test
  void addSong_whenTheQueueRefusesTheSong_chargesNothing() throws Exception {
    when(songQueueService.addSongToQueue(any(), any(AddSongToQueueRequest.class)))
        .thenThrow(new LocationOfflineException(LOCATION_ID));

    postAsWebUser("addSong", new AddSongToQueueRequest(null, 3, 4, 1, false))
        .andExpect(status().isServiceUnavailable());

    verify(userService, never()).handleSongAddedToQueueEvent(any(), any());
  }

  @Test
  void addSong_localCaller_isNeverCheckedOrCharged() throws Exception {
    when(songQueueService.addSongToQueue(any(), any(AddSongToQueueRequest.class)))
        .thenReturn(aQueueEntry());

    mockMvc.perform(post(BASE_PATH + "/addSong")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new AddSongToQueueRequest("u", 3, 4, 1, false))))
        .andExpect(status().isOk());

    verify(userService, never()).requireAffordableQueueAdd(any(), any(), anyInt(), anyBoolean());
    verify(userService, never()).handleSongAddedToQueueEvent(any(), any());
  }

  @Test
  void addSong_patronQueueingDirectlyOnASlave_isRefusedWith403() throws Exception {
    doThrow(new QueueAccessDeniedException("Use the JukeANator website")).when(userService)
        .requireAffordableQueueAdd(WEB_USER, LOCATION_ID, 1, false);

    postAsWebUser("addSong", new AddSongToQueueRequest(null, 3, 4, 1, false))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("QueueAccessDeniedException")));

    verify(songQueueService, never()).addSongToQueue(any(), any());
  }

  @Test
  void addMultipleSongs_patronQueueingDirectlyOnASlave_isRefusedWith403() throws Exception {
    when(userService.getAffordableQueueAddCount(WEB_USER, LOCATION_ID, 1, false))
        .thenThrow(new QueueAccessDeniedException("Use the JukeANator website"));

    postAsWebUser("addMultipleSongs", new AddMultipleSongsToQueueRequest(null,
        List.of(new SongIdentifier(LOCATION_ID, 3, 1)), 1))
        .andExpect(status().isForbidden());

    verify(songQueueService, never()).addMultipleSongsToQueue(any(), any());
  }

  @Test
  void moveSongUp_webUserWhoCannotAffordIt_isRefusedWith402AndNotMoved() throws Exception {
    when(songQueueService.getQueuedSongs(LOCATION_ID)).thenReturn(List.of(aQueueEntry()));
    doThrow(new InsufficientCreditsException(30, 4)).when(userService)
        .requireAffordableQueueAction(WEB_USER, 5, LOCATION_ID);

    postAsWebUser("moveSongUpInQueue", new ChangeSongQueueRequest(3, 4))
        .andExpect(status().isPaymentRequired());

    verify(songQueueService, never()).moveSongUpInQueue(any(), any());
    verify(userService, never()).chargeCreditsForQueueAction(any(), any(), any());
  }

  @Test
  void moveSongDown_webUser_isCheckedAtTheQueuedPriority_thenMovedAndCharged() throws Exception {
    // aQueueEntry() is albumId 3 / songId 4 at priority 5 -- read before the move changes it.
    when(songQueueService.getQueuedSongs(LOCATION_ID)).thenReturn(List.of(aQueueEntry()));
    when(songQueueService.moveSongDownInQueue(eq(LOCATION_ID), any())).thenReturn(1);

    postAsWebUser("moveSongDownInQueue", new ChangeSongQueueRequest(3, 4))
        .andExpect(status().isOk());

    InOrder inOrder = inOrder(userService, songQueueService);
    inOrder.verify(userService).requireAffordableQueueAction(WEB_USER, 5, LOCATION_ID);
    inOrder.verify(songQueueService).moveSongDownInQueue(eq(LOCATION_ID), any());
    inOrder.verify(userService).chargeCreditsForQueueAction(WEB_USER, 5, LOCATION_ID);
  }

  @Test
  void removeSong_thatChangesNothing_isNotCharged() throws Exception {
    when(songQueueService.removeSongDownFromQueue(eq(LOCATION_ID), any())).thenReturn(0);

    postAsWebUser("removeSongDownFromQueue", new ChangeSongQueueRequest(3, 4))
        .andExpect(status().isOk());

    // Not queued any more: priced at the default priority 1.
    verify(userService).requireAffordableQueueAction(WEB_USER, 1, LOCATION_ID);
    verify(userService, never()).chargeCreditsForQueueAction(any(), any(), any());
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
