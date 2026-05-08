package com.djt.jukeanator_engine.domain.songplayer.controller;

import static java.util.Objects.requireNonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;

/**
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-player")
public class SongPlayerController implements SongPlayerService {

  private final SongPlayerService songplayerService;

  public SongPlayerController(SongPlayerService songplayerService) {
    requireNonNull(songplayerService, "songplayerService cannot be null");
    this.songplayerService = songplayerService;
  }

  @Override
  @GetMapping("/nowPlayingSong")
  public NowPlayingSongDto getNowPlayingSong() {
    return songplayerService.getNowPlayingSong();
  }
}