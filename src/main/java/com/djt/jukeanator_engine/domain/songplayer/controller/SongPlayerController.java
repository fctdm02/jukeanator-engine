package com.djt.jukeanator_engine.domain.songplayer.controller;

import static java.util.Objects.requireNonNull;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;

/**
 * Every endpoint is scoped by {@code locationId} -- on a standalone/slave instance this is always
 * its own one location; on master it forwards to whichever location is currently connected over
 * {@code /ws-slave} (see {@code SongPlayerServiceMasterImpl}).
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations/{locationId}/song-player")
public class SongPlayerController {

  private final SongPlayerService songPlayerService;

  public SongPlayerController(@Qualifier("songPlayerService") SongPlayerService songPlayerService) {

    this.songPlayerService = requireNonNull(songPlayerService, "songPlayerService cannot be null");
  }


  @GetMapping("/nowPlayingSong")
  public SongDto getNowPlayingSong(@PathVariable Integer locationId) {

    return songPlayerService.getNowPlayingSong(locationId);
  }


  @GetMapping("/playbackStatus")
  public SongPlaybackStatusDto getPlaybackStatus(@PathVariable Integer locationId) {

    return songPlayerService.getPlaybackStatus(locationId);
  }


  @PostMapping("/next")
  public void playNextTrack(@PathVariable Integer locationId) {

    songPlayerService.playNextTrack(locationId);
  }


  @PostMapping("/pause")
  public void pause(@PathVariable Integer locationId) {

    songPlayerService.pause(locationId);
  }


  @PostMapping("/stop")
  public void stop(@PathVariable Integer locationId) {

    songPlayerService.stop(locationId);
  }


  @PostMapping("/lockQueue")
  public void lockQueue(@PathVariable Integer locationId) {

    songPlayerService.lockQueue(locationId);
  }


  @PostMapping("/unlockQueue")
  public void unlockQueue(@PathVariable Integer locationId) {

    songPlayerService.unlockQueue(locationId);
  }


}
