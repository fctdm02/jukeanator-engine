package com.djt.jukeanator_engine.domain.songqueue.controller;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.djt.jukeanator_engine.domain.location.controller.GeoPositionHeaders;
import com.djt.jukeanator_engine.domain.location.service.GeoFenceService;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.CheckSongsEligibilityRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongEligibilityDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.user.service.UserService;

import jakarta.servlet.http.HttpServletRequest;

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
 * <p>
 * The client's own affordability check is never trusted: every charged web-user operation is
 * refused with 402 ({@code InsufficientCreditsException}) before it runs unless the user's balance
 * covers it, so an unpaid play can never reach the queue or the location's mobile revenue.
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations/{locationId}/song-queue")
public class SongQueueController {

  private static final String ROLE_ADMIN = "ROLE_ADMIN";

  private final SongQueueService songQueueService;
  private final UserService userService;
  private final SongLibraryService songLibraryService;
  private final GeoFenceService geoFenceService;
  private final ConcurrentMap<String, Object> userLocks = new ConcurrentHashMap<>();

  public SongQueueController(@Qualifier("songQueueService") SongQueueService songQueueService,
      UserService userService, SongLibraryService songLibraryService,
      GeoFenceService geoFenceService) {

    requireNonNull(songQueueService, "songQueueService cannot be null");
    requireNonNull(userService, "userService cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    requireNonNull(geoFenceService, "geoFenceService cannot be null");
    this.songQueueService = songQueueService;
    this.userService = userService;
    this.songLibraryService = songLibraryService;
    this.geoFenceService = geoFenceService;
  }

  private boolean isOwnLocation(Integer locationId) {
    return Objects.equals(locationId, songLibraryService.getOwnLocationId());
  }

  /**
   * Refuses a Web/Mobile UI patron's queue operation at a geo-fenced location unless the request's
   * {@code X-Geo-*} headers place the patron inside the fence (see {@link GeoFenceService}). Admin
   * web users are exempt, as are the JFC/Swing kiosk ({@code LocalPrincipal}) and system callers,
   * whose principal is not the email string. Must run before any credit is charged.
   */
  private void requireAtLocation(Integer locationId, Authentication authentication,
      HttpServletRequest request) {

    if (authentication == null || !(authentication.getPrincipal() instanceof String)) {
      return;
    }
    boolean isAdmin = authentication.getAuthorities().stream()
        .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    if (isAdmin) {
      return;
    }
    geoFenceService.verifyWithinFence(locationId, GeoPositionHeaders.fromRequest(request));
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
   * One lock per web user, held across a charged operation's affordability check, the queue change
   * itself and the charge -- otherwise two near-simultaneous requests from the same user (a double
   * tap, or two devices) could both pass the check on a balance that only covers one of them.
   * Only that user's own requests wait on it. A {@code LOCAL_USERNAME}/JFC caller's
   * {@code Authentication#getPrincipal()} is a {@code LocalPrincipal}, not a {@code String}, so it
   * is never charged and never takes a lock.
   */
  private Object lockFor(String email) {
    return userLocks.computeIfAbsent(email, key -> new Object());
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

  /**
   * Vets songs for the web UI's playlist Multi-Select Mode, always as normal plays (priority 1).
   * A song tagged with a different location cannot be queued here, so it is marked ineligible
   * without asking the owning instance; the rest are checked in one call, and the results are
   * returned in request order.
   */
  @PostMapping("/checkSongsEligibility")
  public List<SongEligibilityDto> checkSongsEligibility(@PathVariable Integer locationId,
      @RequestBody CheckSongsEligibilityRequest checkSongsEligibilityRequest) {

    if (checkSongsEligibilityRequest == null
        || checkSongsEligibilityRequest.songIdentifiers() == null) {
      return List.of();
    }

    List<SongIdentifier> requested = checkSongsEligibilityRequest.songIdentifiers().stream()
        .filter(Objects::nonNull)
        .toList();
    List<SongIdentifier> songsAtLocation = requested.stream()
        .filter(id -> id.getLocationId() == null || locationId.equals(id.getLocationId()))
        .toList();

    List<SongEligibilityDto> checked = songsAtLocation.isEmpty()
        ? List.of()
        : songQueueService.checkSongsEligibility(locationId, songsAtLocation, 1);

    List<SongEligibilityDto> results = new ArrayList<>();
    int checkedIndex = 0;
    for (SongIdentifier id : requested) {
      if (id.getLocationId() == null || locationId.equals(id.getLocationId())) {
        SongEligibilityDto result = checkedIndex < checked.size() ? checked.get(checkedIndex) : null;
        checkedIndex++;
        results.add(new SongEligibilityDto(id.getLocationId(), id.getAlbumId(), id.getSongId(),
            result != null ? result.ineligibleReason() : "the song cannot be found"));
      } else {
        results.add(new SongEligibilityDto(id.getLocationId(), id.getAlbumId(), id.getSongId(),
            "is not available at this location"));
      }
    }
    return results;
  }

  /**
   * A web user's add is refused with 402 (before anything is queued or forwarded to the owning
   * slave) unless their balance covers it; the check, the add and the charge run under that user's
   * lock (see {@link #lockFor}).
   */
  @PostMapping("/addSong")
  public SongQueueEntryDto addSongToQueue(@PathVariable Integer locationId,
      @RequestBody AddSongToQueueRequest addSongToQueueRequest, Authentication authentication,
      HttpServletRequest request) {

    requireAtLocation(locationId, authentication, request);

    if (authentication == null || !(authentication.getPrincipal() instanceof String email)) {
      return songQueueService.addSongToQueue(locationId, addSongToQueueRequest);
    }

    // For JWT-authenticated web users the principal is the email string; override the request body
    // username so that the server is authoritative and clients cannot impersonate other users.
    AddSongToQueueRequest webUserRequest = new AddSongToQueueRequest(
        email,
        addSongToQueueRequest.albumId(),
        addSongToQueueRequest.songId(),
        addSongToQueueRequest.priority(),
        addSongToQueueRequest.priorityPlay());
    int priority = webUserRequest.priority() != null ? webUserRequest.priority() : 1;

    synchronized (lockFor(email)) {
      userService.requireAffordableQueueAdd(email, locationId, priority,
          webUserRequest.priorityPlay());

      SongQueueEntryDto entry = songQueueService.addSongToQueue(locationId, webUserRequest);

      if (!isOwnLocation(locationId)) {
        userService.handleSongAddedToQueueEvent(
            new SongAddedToQueueEvent(entry, webUserRequest.priorityPlay()), locationId);
      }
      return entry;
    }
  }

  /** Admin-only (see {@code SecurityConfig}) -- web patrons cannot queue a whole album. */
  @PostMapping("/addAlbum")
  public List<SongQueueEntryDto> addAlbumToQueue(@PathVariable Integer locationId,
      @RequestBody AddAlbumToQueueRequest addAlbumToQueueRequest) {

    return songQueueService.addAlbumToQueue(locationId, addAlbumToQueueRequest);
  }

  /**
   * For a web user (playing a playlist), songs tagged with a different location are dropped, and
   * the rest go to the owning instance in a single {@code addMultipleSongsToQueue} command as
   * normal plays (priority 1) with {@code skipIneligibleSongs} set and {@code maxSongs} capped at
   * what the user can afford. Each song actually queued is then charged exactly as a single
   * {@code addSong} normal play -- explicitly, for own and remote locations alike, since a batch
   * add publishes {@code MultipleSongsAddedToQueueEvent}, which no credit listener handles. Any
   * other caller (local/admin) gets the unchanged, uncharged batch add.
   */
  @PostMapping("/addMultipleSongs")
  public List<SongQueueEntryDto> addMultipleSongsToQueue(@PathVariable Integer locationId,
      @RequestBody AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest,
      Authentication authentication, HttpServletRequest request) {

    if (authentication == null || !(authentication.getPrincipal() instanceof String email)) {
      return songQueueService.addMultipleSongsToQueue(locationId, addMultipleSongsToQueueRequest);
    }

    requireAtLocation(locationId, authentication, request);

    if (addMultipleSongsToQueueRequest == null
        || addMultipleSongsToQueueRequest.songIdentifiers() == null) {
      return List.of();
    }

    List<SongIdentifier> songsAtLocation = addMultipleSongsToQueueRequest.songIdentifiers()
        .stream()
        .filter(id -> id != null && locationId.equals(id.getLocationId()))
        .toList();

    int priority = 1;
    boolean priorityPlay = false;

    synchronized (lockFor(email)) {
      int maxSongs =
          userService.getAffordableQueueAddCount(email, locationId, priority, priorityPlay);
      if (songsAtLocation.isEmpty() || maxSongs <= 0) {
        return List.of();
      }

      List<SongQueueEntryDto> queued = songQueueService.addMultipleSongsToQueue(locationId,
          new AddMultipleSongsToQueueRequest(email, songsAtLocation, priority, true, maxSongs));

      for (SongQueueEntryDto entry : queued) {
        userService.handleSongAddedToQueueEvent(new SongAddedToQueueEvent(entry, priorityPlay),
            locationId);
      }
      return queued;
    }
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
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication,
      HttpServletRequest request) {

    return changeQueue(locationId, changeSongQueueRequest, authentication, request,
        songQueueService::moveSongUpInQueue);
  }

  @PostMapping("/moveSongDownInQueue")
  public Integer moveSongDownInQueue(@PathVariable Integer locationId,
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication,
      HttpServletRequest request) {

    return changeQueue(locationId, changeSongQueueRequest, authentication, request,
        songQueueService::moveSongDownInQueue);
  }

  @PostMapping("/removeSongDownFromQueue")
  public Integer removeSongDownFromQueue(@PathVariable Integer locationId,
      @RequestBody ChangeSongQueueRequest changeSongQueueRequest, Authentication authentication,
      HttpServletRequest request) {

    return changeQueue(locationId, changeSongQueueRequest, authentication, request,
        songQueueService::removeSongDownFromQueue);
  }

  /**
   * Runs a reorder/remove. A web user's is refused with 402 (before the queue changes) unless
   * their balance covers it, and is charged only when it actually changed the queue; the check,
   * the change and the charge run under that user's lock (see {@link #lockFor}).
   */
  private Integer changeQueue(Integer locationId, ChangeSongQueueRequest changeSongQueueRequest,
      Authentication authentication, HttpServletRequest request,
      BiFunction<Integer, ChangeSongQueueRequest, Integer> queueChange) {

    requireAtLocation(locationId, authentication, request);

    if (authentication == null || !(authentication.getPrincipal() instanceof String email)) {
      return queueChange.apply(locationId, changeSongQueueRequest);
    }

    synchronized (lockFor(email)) {
      Integer priority = findQueuedPriority(locationId, changeSongQueueRequest.albumId(),
          changeSongQueueRequest.songId());
      userService.requireAffordableQueueAction(email, priority, locationId);

      Integer result = queueChange.apply(locationId, changeSongQueueRequest);
      if (result != null && result > 0) {
        userService.chargeCreditsForQueueAction(email, priority, locationId);
      }
      return result;
    }
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
