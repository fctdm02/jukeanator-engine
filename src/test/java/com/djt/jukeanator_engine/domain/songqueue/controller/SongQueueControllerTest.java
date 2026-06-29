package com.djt.jukeanator_engine.domain.songqueue.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
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
import com.djt.jukeanator_engine.AbstractControllerTest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

class SongQueueControllerTest extends AbstractControllerTest {

  @Mock
  private SongQueueService songQueueService;

  @InjectMocks
  private SongQueueController songQueueController;

  @Override
  protected Object getController() {
    return songQueueController;
  }

  private SongQueueEntryDto aQueueEntry() {
    SongDto song = new SongDto(1, "Genre", 2, "Artist", 3, "Album", "/cover.jpg", 4, "Song", 1, 0);
    return new SongQueueEntryDto(song, 5, "/music/song.mp3");
  }

  @Test
  void getHighestPriority_delegatesToService() throws Exception {
    when(songQueueService.getHighestPriority()).thenReturn(7);

    mockMvc.perform(get("/api/song-queue/highestPriority"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(7)));
  }

  @Test
  void getQueuedSongs_returnsListFromService() throws Exception {
    when(songQueueService.getQueuedSongs()).thenReturn(List.of(aQueueEntry()));

    mockMvc.perform(get("/api/song-queue/queuedSongs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].songPath", is("/music/song.mp3")));
  }

  @Test
  void isSongEligibleForQueue_passesParams() throws Exception {
    when(songQueueService.isSongEligibleForQueue(3, 4, 5)).thenReturn("ELIGIBLE");

    mockMvc.perform(get("/api/song-queue/isSongEligibleForQueue")
            .param("albumId", "3")
            .param("songId", "4")
            .param("priority", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is("ELIGIBLE")));

    verify(songQueueService).isSongEligibleForQueue(3, 4, 5);
  }

  @Test
  void addSongToQueue_passesRequest() throws Exception {
    AddSongToQueueRequest request = new AddSongToQueueRequest("user", 3, 4, 5);
    when(songQueueService.addSongToQueue(any(AddSongToQueueRequest.class)))
        .thenReturn(aQueueEntry());

    mockMvc.perform(post("/api/song-queue/addSong")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.songPath", is("/music/song.mp3")));
  }

  @Test
  void addAlbumToQueue_passesRequest() throws Exception {
    AddAlbumToQueueRequest request = new AddAlbumToQueueRequest("user", 3, 5);
    when(songQueueService.addAlbumToQueue(any(AddAlbumToQueueRequest.class)))
        .thenReturn(List.of(aQueueEntry()));

    mockMvc.perform(post("/api/song-queue/addAlbum")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].songPath", is("/music/song.mp3")));
  }

  @Test
  void addMultipleSongsToQueue_passesRequest() throws Exception {
    AddMultipleSongsToQueueRequest request =
        new AddMultipleSongsToQueueRequest("user", List.of(), 5);
    when(songQueueService.addMultipleSongsToQueue(any(AddMultipleSongsToQueueRequest.class)))
        .thenReturn(List.of(aQueueEntry()));

    mockMvc.perform(post("/api/song-queue/addMultipleSongs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void flushQueue_delegatesToService() throws Exception {
    when(songQueueService.flushQueue()).thenReturn(3);

    mockMvc.perform(post("/api/song-queue/flushQueue"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(3)));
  }

  @Test
  void randomizeQueue_delegatesToService() throws Exception {
    when(songQueueService.randomizeQueue()).thenReturn(3);

    mockMvc.perform(post("/api/song-queue/randomizeQueue"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(3)));
  }

  @Test
  void moveSongUpInQueue_passesRequest() throws Exception {
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(3, 4);
    when(songQueueService.moveSongUpInQueue(any(ChangeSongQueueRequest.class))).thenReturn(1);

    mockMvc.perform(post("/api/song-queue/moveSongUpInQueue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(1)));
  }

  @Test
  void moveSongDownInQueue_passesRequest() throws Exception {
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(3, 4);
    when(songQueueService.moveSongDownInQueue(any(ChangeSongQueueRequest.class))).thenReturn(1);

    mockMvc.perform(post("/api/song-queue/moveSongDownInQueue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(1)));
  }

  @Test
  void removeSongDownFromQueue_passesRequest() throws Exception {
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(3, 4);
    when(songQueueService.removeSongDownFromQueue(any(ChangeSongQueueRequest.class)))
        .thenReturn(1);

    mockMvc.perform(post("/api/song-queue/removeSongDownFromQueue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(1)));
  }

  @Test
  void saveQueueAsPlaylist_passesFilename() throws Exception {
    when(songQueueService.saveQueueAsPlaylist("myPlaylist")).thenReturn(1);

    mockMvc.perform(post("/api/song-queue/saveQueueAsPlaylist")
            .contentType(MediaType.TEXT_PLAIN)
            .content("myPlaylist"))
        .andExpect(status().isOk());

    verify(songQueueService).saveQueueAsPlaylist("myPlaylist");
  }

  @Test
  void loadPlaylistIntoQueue_passesRequest() throws Exception {
    LoadPlaylistIntoQueueRequest request = new LoadPlaylistIntoQueueRequest("user", "myPlaylist");
    when(songQueueService.loadPlaylistIntoQueue(any(LoadPlaylistIntoQueueRequest.class)))
        .thenReturn(5);

    mockMvc.perform(post("/api/song-queue/loadPlaylistIntoQueue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is(5)));
  }
}
