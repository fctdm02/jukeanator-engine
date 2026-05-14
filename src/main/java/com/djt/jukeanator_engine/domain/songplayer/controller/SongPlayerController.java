package com.djt.jukeanator_engine.domain.songplayer.controller;

import static java.util.Objects.requireNonNull;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;

/**
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-player")
public class SongPlayerController implements SongPlayerService {

  private final SongPlayerService songPlayerService;

  public SongPlayerController(@Qualifier("songPlayerService") SongPlayerService songPlayerService) {

    this.songPlayerService = requireNonNull(songPlayerService, "songPlayerService cannot be null");
  }

  @Override
  @GetMapping("/nowPlayingSong")
  public NowPlayingSongDto getNowPlayingSong() {

    return songPlayerService.getNowPlayingSong();
  }

  @Override
  @GetMapping("/playbackStatus")
  public SongPlaybackStatusDto getPlaybackStatus() {

    return songPlayerService.getPlaybackStatus();
  }

  @Override
  @PostMapping("/next")
  public void playNextTrack() {

    songPlayerService.playNextTrack();
  }

  @Override
  @PostMapping("/pause")
  public void pause() {

    songPlayerService.pause();
  }

  @Override
  @PostMapping("/stop")
  public void stop() {

    songPlayerService.stop();
  }
}
