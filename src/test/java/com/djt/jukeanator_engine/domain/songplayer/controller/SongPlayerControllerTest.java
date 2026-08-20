package com.djt.jukeanator_engine.domain.songplayer.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.djt.jukeanator_engine.AbstractControllerTest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlayerStatus;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;

class SongPlayerControllerTest extends AbstractControllerTest {

  private static final Integer LOCATION_ID = 7;
  private static final String BASE_PATH = "/api/locations/" + LOCATION_ID + "/song-player";

  @Mock
  private SongPlayerService songPlayerService;

  @InjectMocks
  private SongPlayerController songPlayerController;

  @Override
  protected Object getController() {
    return songPlayerController;
  }

  @Test
  void getNowPlayingSong_delegatesToService() throws Exception {
    SongDto song = new SongDto(1, "Genre", 2, "Artist", 3, "Album", "/cover.jpg", 4, "Song", 1, 0);
    when(songPlayerService.getNowPlayingSong(LOCATION_ID)).thenReturn(song);

    mockMvc.perform(get(BASE_PATH + "/nowPlayingSong"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.songName", is("Song")));
  }

  @Test
  void getPlaybackStatus_delegatesToService() throws Exception {
    SongPlaybackStatusDto playbackStatus =
        new SongPlaybackStatusDto(SongPlayerStatus.PLAYING, 10L, 200L);
    when(songPlayerService.getPlaybackStatus(LOCATION_ID)).thenReturn(playbackStatus);

    mockMvc.perform(get(BASE_PATH + "/playbackStatus"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("PLAYING")));
  }

  @Test
  void playNextTrack_delegatesToService() throws Exception {
    mockMvc.perform(post(BASE_PATH + "/next"))
        .andExpect(status().isOk());

    verify(songPlayerService).playNextTrack(LOCATION_ID);
  }

  @Test
  void pause_delegatesToService() throws Exception {
    mockMvc.perform(post(BASE_PATH + "/pause"))
        .andExpect(status().isOk());

    verify(songPlayerService).pause(LOCATION_ID);
  }

  @Test
  void stop_delegatesToService() throws Exception {
    mockMvc.perform(post(BASE_PATH + "/stop"))
        .andExpect(status().isOk());

    verify(songPlayerService).stop(LOCATION_ID);
  }

  @Test
  void lockQueue_delegatesToService() throws Exception {
    mockMvc.perform(post(BASE_PATH + "/lockQueue"))
        .andExpect(status().isOk());

    verify(songPlayerService).lockQueue(LOCATION_ID);
  }

  @Test
  void unlockQueue_delegatesToService() throws Exception {
    mockMvc.perform(post(BASE_PATH + "/unlockQueue"))
        .andExpect(status().isOk());

    verify(songPlayerService).unlockQueue(LOCATION_ID);
  }
}
