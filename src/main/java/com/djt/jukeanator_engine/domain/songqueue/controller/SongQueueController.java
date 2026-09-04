package com.djt.jukeanator_engine.domain.songqueue.controller;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
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
 * Every endpoint is scoped by {@code locationId} -- on a standalone/slave instance this is always
 * its own one location; on master it can be any location currently connected over {@code
 * /ws-slave} (see {@code SongQueueServiceImpl#isOwnLocation}, which decides locally-execute vs.
 * forward-via-{@code SlaveCommandGateway} per call).
 *
 * <p>
 * Credit charging for {@code addSong} has a real behavioral split, preserved here rather than
 * simplified away: a local add publishes {@code SongAddedToQueueEvent} on this process's own event
 * bus, which {@code UserServiceImpl} already listens for and charges credits from -- charging again
 * explicitly here would double-charge. A remote add's mutation happens on the owning slave's own
 * process, so that event never reaches this process, and credits must be charged explicitly
 * instead. The three reorder/remove endpoints were never event-driven in either the old unscoped or
 * location-scoped controller, so they always charge explicitly regardless of location.
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations/{locationId}/song-queue")
public class SongQueueController {

  private final SongQueueService songQueueService;
  private final UserService userService;
  private final SongLibraryService songLibraryService;

  public SongQueueController(@Qualifier("songQueueService") SongQueueService songQueueService,
      UserService userService, SongLibraryService songLibraryService) {

    requireNonNull(songQueueService, "songQueueService cannot be null");
    requireNonNull(userService, "userService cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    this.songQueueService = songQueueService;
    this.userService = userService;
    this.songLibraryService = songLibraryService;
  }

  private boolean isOwnLocation(Integer locationId) {
    return Objects.equals(locationId, songLibraryService.getOwnLocationId());
  }

  /**
   * Looks up a queue entry's current priority, used to price a reorder/remove action before it
   * runs — {@code removeSongDownFromQueue} makes the entry disappear, so this must be read
   * beforehand.
   */
  private Integer findQueuedPriority(Integer locationId, int albumId, int songId) {
    return songQueueService.getQueuedSongs(locationId).stream()
        .filter(entry -> entry.song().albumId() == albumId
            && entry.song().songId() == songId)
        .map(SongQueueEntryDto::priority)
        .findFirst()
        .orElse(1);
  }

  /**
   * Web UI patrons pay for reordering/removing a queued song, same as adding one. A
   * {@code LOCAL_USERNAME}/JFC caller's {@code Authentication#getPrincipal()} is a
   * {@code LocalPrincipal}, not a {@code String}, so it is naturally excluded here.
   */
  private void chargeWebUserForQueueAction(Authentication authentication, Integer priority,
      Integer locationId) {
    if (authentication != null && authentication.getPrincipal() instanceof String email) {
      userService.chargeCreditsForQueueAction(email, priority, locationId);
    }
  }

  @GetMapping("/highestPriority")
  public Integer getHighestPriority(@PathVariable Integer locationId) {

    return songQueueService.getHighestPriority(locationId);
  }

  @GetMapping("/queuedSongs")
  public List<SongQueueEntryDto> getQueuedSongs(@PathVariable Integer locationId) {
    return songQueueService.getQueuedSongs(locationId);
  }

  @GetMapping("/isSongEligibleForQueue")
  public String isSongEligibleForQueue(@PathVariable Integer locationId,
      @RequestParam Integer albumId, @RequestParam Integer songId,
      @RequestParam Integer priority) {
    return songQueueService.isSongEligibleForQueue(locationId, albumId, songId, priority);
  }

  @PostMapping("/addSong")
  public SongQueueEntryDto addSongToQueue(@PathVariable Integer locationId,
      @RequestBody AddSongToQueueRequest addSongToQueueRequest, Authentication authentication) {

    // For JWT-authenticated web users the principal is the email string; override the request body
    // username so that the server is authoritative and clients cannot impersonate other users.
    if (authentication != null && authentication.getPrincipal() instanceof String email) {
      addSongToQueueRequest = new AddSongToQueueRequest(
          email,
          addSongToQueueRequest.albumId(),
          addSongToQueueRequest.songId(),
          addSongToQueueRequest.priority(),
          addSongToQueueRequest.priorityPlay());
    }

    SongQueueEntryDto entry = songQueueService.addSongToQueue(locationId, addSongToQueueRequest);

    if (!isOwnLocation(locationId) && authentication != null
        && authentication.getPrincipal() instanceof String) {
      userService.handleSongAddedToQueueEvent(
          new SongAddedToQueueEvent(entry, addSongToQueueRequest.priorityPlay()), locationId);
    }

    return entry;
  }

  @PostMapping("/addAlbum")
  public List<SongQueueEntryDto> addAlbumToQueue(@PathVariable Integer locationId,
      @RequestBody AddAlbumToQueueRequest addAlbumToQueueRequest) {

    return songQueueService.addAlbumToQueue(locationId, addAlbumToQueueRequest);
  }

  @PostMapping("/addMultipleSongs")
  public List<SongQueueEntryDto> addMultipleSongsToQueue(@PathVariable Integer locationId,
      @RequestBody AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest) {

    return songQueueService.addMultipleSongsToQueue(locationId, addMultipleSongsToQueueRequest);
  }

  @PostMapping("/flushQueue")
  public Integer flushQueue(@PathVariable Integer locationId) {

    return songQueueService.flushQueue(locationId);
  }

  @PostMapping("/randomizeQueue")
  public Integer randomizeQueue(@PathVariable Integer locationId) {

    return songQueueService.randomizeQueue(locationId);
  }

  @PostMapping("/moveSongUpInQueue")
  public Integer moveSongUpInQueue(@PathVariable Integer locationId,
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication) {

    Integer priority = findQueuedPriority(locationId, changeSongQueueRequest.albumId(),
        changeSongQueueRequest.songId());
    Integer result = songQueueService.moveSongUpInQueue(locationId, changeSongQueueRequest);
    if (result != null && result > 0) {
      chargeWebUserForQueueAction(authentication, priority, locationId);
    }
    return result;
  }

  @PostMapping("/moveSongDownInQueue")
  public Integer moveSongDownInQueue(@PathVariable Integer locationId,
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication) {

    Integer priority = findQueuedPriority(locationId, changeSongQueueRequest.albumId(),
        changeSongQueueRequest.songId());
    Integer result = songQueueService.moveSongDownInQueue(locationId, changeSongQueueRequest);
    if (result != null && result > 0) {
      chargeWebUserForQueueAction(authentication, priority, locationId);
    }
    return result;
  }

  @PostMapping("/removeSongDownFromQueue")
  public Integer removeSongDownFromQueue(@PathVariable Integer locationId,
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication) {

    Integer priority = findQueuedPriority(locationId, changeSongQueueRequest.albumId(),
        changeSongQueueRequest.songId());
    Integer result = songQueueService.removeSongDownFromQueue(locationId, changeSongQueueRequest);
    if (result != null && result > 0) {
      chargeWebUserForQueueAction(authentication, priority, locationId);
    }
    return result;
  }

  @PostMapping("/saveQueueAsPlaylist")
  public Integer saveQueueAsPlaylist(@PathVariable Integer locationId,
      @RequestBody String filename) {

    return songQueueService.saveQueueAsPlaylist(locationId, filename);
  }

  @PostMapping("/loadPlaylistIntoQueue")
  public Integer loadPlaylistIntoQueue(@PathVariable Integer locationId,
      @RequestBody LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest) {

    return songQueueService.loadPlaylistIntoQueue(locationId, loadPlaylistIntoQueueRequest);
  }
}
