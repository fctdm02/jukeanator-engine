package com.djt.jukeanator_engine.domain.songqueue.controller;

import static java.util.Objects.requireNonNull;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.location.service.LocationServiceRegistry;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.service.UserService;

/**
 * Master-only. Same shape as {@link SongQueueController}, scoped to a specific location — every
 * mutation is a round trip through that location's real slave (see
 * {@code SongQueueServiceLocationProxy}/{@code SlaveCommandGateway}). This is an additive,
 * standalone controller; {@code SongQueueController} itself is untouched, so standalone/slave-mode
 * risk stays zero.
 *
 * <p>
 * Credit charging here is explicit rather than event-driven: the original controller relies on
 * {@code SongAddedToQueueEvent} being published on the same process's event bus that
 * {@code UserServiceImpl} listens on, but a location-scoped add executes on the remote slave's own
 * process, so that event never reaches master. This controller calls
 * {@code UserService.handleSongAddedToQueueEvent}/{@code chargeCreditsForQueueAction} directly
 * instead, achieving the same charging behavior.
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations/{locationId}/song-queue")
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class LocationScopedSongQueueController {

  private final LocationServiceRegistry locationServiceRegistry;
  private final UserService userService;

  public LocationScopedSongQueueController(LocationServiceRegistry locationServiceRegistry,
      UserService userService) {

    this.locationServiceRegistry = requireNonNull(locationServiceRegistry,
        "locationServiceRegistry cannot be null");
    this.userService = requireNonNull(userService, "userService cannot be null");
  }

  private SongQueueService queueService(String locationId) {
    return locationServiceRegistry.resolveSongQueueService(locationId);
  }

  private Integer findQueuedPriority(String locationId, int albumId, int songId) {
    return queueService(locationId).getQueuedSongs().stream()
        .filter(entry -> entry.getSong().getAlbumId() == albumId
            && entry.getSong().getSongId() == songId)
        .map(SongQueueEntryDto::getPriority).findFirst().orElse(1);
  }

  private void chargeWebUserForQueueAction(Authentication authentication, Integer priority,
      String locationId) {
    if (authentication != null && authentication.getPrincipal() instanceof String email) {
      userService.chargeCreditsForQueueAction(email, priority, locationId);
    }
  }

  @GetMapping("/highestPriority")
  public Integer getHighestPriority(@PathVariable String locationId) {
    return queueService(locationId).getHighestPriority();
  }

  @GetMapping("/queuedSongs")
  public List<SongQueueEntryDto> getQueuedSongs(@PathVariable String locationId) {
    return queueService(locationId).getQueuedSongs();
  }

  @GetMapping("/isSongEligibleForQueue")
  public String isSongEligibleForQueue(@PathVariable String locationId,
      @RequestParam Integer albumId, @RequestParam Integer songId,
      @RequestParam Integer priority) {
    return queueService(locationId).isSongEligibleForQueue(albumId, songId, priority);
  }

  @PostMapping("/addSong")
  public SongQueueEntryDto addSongToQueue(@PathVariable String locationId,
      @RequestBody AddSongToQueueRequest addSongToQueueRequest, Authentication authentication) {

    if (authentication != null && authentication.getPrincipal() instanceof String email) {
      addSongToQueueRequest = new AddSongToQueueRequest(email, addSongToQueueRequest.getAlbumId(),
          addSongToQueueRequest.getSongId(), addSongToQueueRequest.getPriority());
    }

    SongQueueEntryDto entry = queueService(locationId).addSongToQueue(addSongToQueueRequest);
    if (authentication != null && authentication.getPrincipal() instanceof String) {
      userService.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry), locationId);
    }
    return entry;
  }

  @PostMapping("/addAlbum")
  public List<SongQueueEntryDto> addAlbumToQueue(@PathVariable String locationId,
      @RequestBody AddAlbumToQueueRequest addAlbumToQueueRequest) {
    return queueService(locationId).addAlbumToQueue(addAlbumToQueueRequest);
  }

  @PostMapping("/addMultipleSongs")
  public List<SongQueueEntryDto> addMultipleSongsToQueue(@PathVariable String locationId,
      @RequestBody AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest) {
    return queueService(locationId).addMultipleSongsToQueue(addMultipleSongsToQueueRequest);
  }

  @PostMapping("/flushQueue")
  public Integer flushQueue(@PathVariable String locationId) {
    return queueService(locationId).flushQueue();
  }

  @PostMapping("/randomizeQueue")
  public Integer randomizeQueue(@PathVariable String locationId) {
    return queueService(locationId).randomizeQueue();
  }

  @PostMapping("/moveSongUpInQueue")
  public Integer moveSongUpInQueue(@PathVariable String locationId,
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication) {

    Integer priority = findQueuedPriority(locationId, changeSongQueueRequest.getAlbumId(),
        changeSongQueueRequest.getSongId());
    Integer result = queueService(locationId).moveSongUpInQueue(changeSongQueueRequest);
    if (result != null && result > 0) {
      chargeWebUserForQueueAction(authentication, priority, locationId);
    }
    return result;
  }

  @PostMapping("/moveSongDownInQueue")
  public Integer moveSongDownInQueue(@PathVariable String locationId,
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication) {

    Integer priority = findQueuedPriority(locationId, changeSongQueueRequest.getAlbumId(),
        changeSongQueueRequest.getSongId());
    Integer result = queueService(locationId).moveSongDownInQueue(changeSongQueueRequest);
    if (result != null && result > 0) {
      chargeWebUserForQueueAction(authentication, priority, locationId);
    }
    return result;
  }

  @PostMapping("/removeSongDownFromQueue")
  public Integer removeSongDownFromQueue(@PathVariable String locationId,
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication) {

    Integer priority = findQueuedPriority(locationId, changeSongQueueRequest.getAlbumId(),
        changeSongQueueRequest.getSongId());
    Integer result = queueService(locationId).removeSongDownFromQueue(changeSongQueueRequest);
    if (result != null && result > 0) {
      chargeWebUserForQueueAction(authentication, priority, locationId);
    }
    return result;
  }

  @PostMapping("/saveQueueAsPlaylist")
  public Integer saveQueueAsPlaylist(@PathVariable String locationId,
      @RequestBody String filename) {
    return queueService(locationId).saveQueueAsPlaylist(filename);
  }

  @PostMapping("/loadPlaylistIntoQueue")
  public Integer loadPlaylistIntoQueue(@PathVariable String locationId,
      @RequestBody LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest) {
    return queueService(locationId).loadPlaylistIntoQueue(loadPlaylistIntoQueueRequest);
  }
}
