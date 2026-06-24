package com.djt.jukeanator_engine.domain.songplayer.controller;

import static java.util.Objects.requireNonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

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
  public SongDto getNowPlayingSong() {

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

  @Override
  @PostMapping("/lockQueue")
  public void lockQueue() {

    songPlayerService.lockQueue();
  }

  @Override
  @PostMapping("/unlockQueue")
  public void unlockQueue() {

    songPlayerService.unlockQueue();
  }

  @Override
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }

  @Override
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }
}
