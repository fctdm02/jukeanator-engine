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
    when(songPlayerService.getNowPlayingSong()).thenReturn(song);

    mockMvc.perform(get("/api/song-player/nowPlayingSong"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.songName", is("Song")));
  }

  @Test
  void getPlaybackStatus_delegatesToService() throws Exception {
    SongPlaybackStatusDto playbackStatus =
        new SongPlaybackStatusDto(SongPlayerStatus.PLAYING, 10L, 200L);
    when(songPlayerService.getPlaybackStatus()).thenReturn(playbackStatus);

    mockMvc.perform(get("/api/song-player/playbackStatus"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("PLAYING")));
  }

  @Test
  void playNextTrack_delegatesToService() throws Exception {
    mockMvc.perform(post("/api/song-player/next"))
        .andExpect(status().isOk());

    verify(songPlayerService).playNextTrack();
  }

  @Test
  void pause_delegatesToService() throws Exception {
    mockMvc.perform(post("/api/song-player/pause"))
        .andExpect(status().isOk());

    verify(songPlayerService).pause();
  }

  @Test
  void stop_delegatesToService() throws Exception {
    mockMvc.perform(post("/api/song-player/stop"))
        .andExpect(status().isOk());

    verify(songPlayerService).stop();
  }

  @Test
  void lockQueue_delegatesToService() throws Exception {
    mockMvc.perform(post("/api/song-player/lockQueue"))
        .andExpect(status().isOk());

    verify(songPlayerService).lockQueue();
  }

  @Test
  void unlockQueue_delegatesToService() throws Exception {
    mockMvc.perform(post("/api/song-player/unlockQueue"))
        .andExpect(status().isOk());

    verify(songPlayerService).unlockQueue();
  }
}
