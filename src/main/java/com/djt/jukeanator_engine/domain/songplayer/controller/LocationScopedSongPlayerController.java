package com.djt.jukeanator_engine.domain.songplayer.controller;

import static java.util.Objects.requireNonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.location.service.LocationServiceRegistry;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;

/**
 * Master-only. Same shape as {@link SongPlayerController}, scoped to a specific location. This is
 * an additive, standalone controller — {@code SongPlayerController} itself is untouched, so
 * standalone/slave-mode risk stays zero.
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations/{locationId}/song-player")
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class LocationScopedSongPlayerController {

  private final LocationServiceRegistry locationServiceRegistry;

  public LocationScopedSongPlayerController(LocationServiceRegistry locationServiceRegistry) {
    this.locationServiceRegistry = requireNonNull(locationServiceRegistry,
        "locationServiceRegistry cannot be null");
  }

  @GetMapping("/nowPlayingSong")
  public SongDto getNowPlayingSong(@PathVariable String locationId) {
    return locationServiceRegistry.resolveSongPlayerService(locationId).getNowPlayingSong();
  }

  @GetMapping("/playbackStatus")
  public SongPlaybackStatusDto getPlaybackStatus(@PathVariable String locationId) {
    return locationServiceRegistry.resolveSongPlayerService(locationId).getPlaybackStatus();
  }

  @PostMapping("/next")
  public void playNextTrack(@PathVariable String locationId) {
    locationServiceRegistry.resolveSongPlayerService(locationId).playNextTrack();
  }

  @PostMapping("/pause")
  public void pause(@PathVariable String locationId) {
    locationServiceRegistry.resolveSongPlayerService(locationId).pause();
  }

  @PostMapping("/stop")
  public void stop(@PathVariable String locationId) {
    locationServiceRegistry.resolveSongPlayerService(locationId).stop();
  }

  @PostMapping("/lockQueue")
  public void lockQueue(@PathVariable String locationId) {
    locationServiceRegistry.resolveSongPlayerService(locationId).lockQueue();
  }

  @PostMapping("/unlockQueue")
  public void unlockQueue(@PathVariable String locationId) {
    locationServiceRegistry.resolveSongPlayerService(locationId).unlockQueue();
  }
}
