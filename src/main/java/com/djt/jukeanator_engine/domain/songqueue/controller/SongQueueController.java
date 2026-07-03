package com.djt.jukeanator_engine.domain.songqueue.controller;

import static java.util.Objects.requireNonNull;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.service.UserService;

/**
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-queue")
public class SongQueueController {

  private final SongQueueService songQueueService;
  private final UserService userService;

  public SongQueueController(@Qualifier("songQueueService") SongQueueService songQueueService,
      UserService userService) {

    requireNonNull(songQueueService, "songQueueService cannot be null");
    requireNonNull(userService, "userService cannot be null");
    this.songQueueService = songQueueService;
    this.userService = userService;
  }

  /**
   * Looks up a queue entry's current priority, used to price a reorder/remove action before it
   * runs — {@code removeSongDownFromQueue} makes the entry disappear, so this must be read
   * beforehand.
   */
  private Integer findQueuedPriority(int albumId, int songId) {
    return songQueueService.getQueuedSongs().stream()
        .filter(entry -> entry.getSong().getAlbumId() == albumId
            && entry.getSong().getSongId() == songId)
        .map(SongQueueEntryDto::getPriority)
        .findFirst()
        .orElse(1);
  }

  /**
   * Web UI patrons pay for reordering/removing a queued song, same as adding one. A
   * {@code LOCAL_USERNAME}/JFC caller's {@code Authentication#getPrincipal()} is a
   * {@code LocalPrincipal}, not a {@code String}, so it is naturally excluded here.
   */
  private void chargeWebUserForQueueAction(Authentication authentication, Integer priority) {
    if (authentication != null && authentication.getPrincipal() instanceof String email) {
      userService.chargeCreditsForQueueAction(email, priority);
    }
  }



  @GetMapping("/highestPriority")
  public Integer getHighestPriority() {

    return songQueueService.getHighestPriority();
  }


  @GetMapping("/queuedSongs")
  public List<SongQueueEntryDto> getQueuedSongs() {
    return songQueueService.getQueuedSongs();
  }


  @GetMapping("/isSongEligibleForQueue")
  public String isSongEligibleForQueue(@RequestParam Integer albumId, @RequestParam Integer songId,
      @RequestParam Integer priority) {
    return songQueueService.isSongEligibleForQueue(albumId, songId, priority);
  }


  @PostMapping("/addSong")
  public SongQueueEntryDto addSongToQueue(
      @RequestBody AddSongToQueueRequest addSongToQueueRequest,
      Authentication authentication) {

    // For JWT-authenticated web users the principal is the email string; override the request body
    // username so that the server is authoritative and clients cannot impersonate other users.
    if (authentication != null && authentication.getPrincipal() instanceof String email) {
      addSongToQueueRequest = new AddSongToQueueRequest(
          email,
          addSongToQueueRequest.getAlbumId(),
          addSongToQueueRequest.getSongId(),
          addSongToQueueRequest.getPriority());
    }

    return songQueueService.addSongToQueue(addSongToQueueRequest);
  }


  @PostMapping("/addAlbum")
  public List<SongQueueEntryDto> addAlbumToQueue(
      @RequestBody AddAlbumToQueueRequest addAlbumToQueueRequest) {

    return songQueueService.addAlbumToQueue(addAlbumToQueueRequest);
  }


  @PostMapping("/addMultipleSongs")
  public List<SongQueueEntryDto> addMultipleSongsToQueue(
      @RequestBody AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest) {

    return songQueueService.addMultipleSongsToQueue(addMultipleSongsToQueueRequest);
  }


  @PostMapping("/flushQueue")
  public Integer flushQueue() {

    return songQueueService.flushQueue();
  }


  @PostMapping("/randomizeQueue")
  public Integer randomizeQueue() {

    return songQueueService.randomizeQueue();
  }


  @PostMapping("/moveSongUpInQueue")
  public Integer moveSongUpInQueue(@RequestBody ChangeSongQueueRequest changeSongQueueRequest,
      Authentication authentication) {

    Integer priority =
        findQueuedPriority(changeSongQueueRequest.getAlbumId(), changeSongQueueRequest.getSongId());
    Integer result = songQueueService.moveSongUpInQueue(changeSongQueueRequest);
    if (result != null && result > 0) {
      chargeWebUserForQueueAction(authentication, priority);
    }
    return result;
  }


  @PostMapping("/moveSongDownInQueue")
  public Integer moveSongDownInQueue(@RequestBody ChangeSongQueueRequest changeSongQueueRequest,
      Authentication authentication) {

    Integer priority =
        findQueuedPriority(changeSongQueueRequest.getAlbumId(), changeSongQueueRequest.getSongId());
    Integer result = songQueueService.moveSongDownInQueue(changeSongQueueRequest);
    if (result != null && result > 0) {
      chargeWebUserForQueueAction(authentication, priority);
    }
    return result;
  }


  @PostMapping("/removeSongDownFromQueue")
  public Integer removeSongDownFromQueue(
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication) {

    Integer priority =
        findQueuedPriority(changeSongQueueRequest.getAlbumId(), changeSongQueueRequest.getSongId());
    Integer result = songQueueService.removeSongDownFromQueue(changeSongQueueRequest);
    if (result != null && result > 0) {
      chargeWebUserForQueueAction(authentication, priority);
    }
    return result;
  }


  @PostMapping("/saveQueueAsPlaylist")
  public Integer saveQueueAsPlaylist(@RequestBody String filename) {

    return songQueueService.saveQueueAsPlaylist(filename);
  }


  @PostMapping("/loadPlaylistIntoQueue")
  public Integer loadPlaylistIntoQueue(
      @RequestBody LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest) {

    return songQueueService.loadPlaylistIntoQueue(loadPlaylistIntoQueueRequest);
  }


}
