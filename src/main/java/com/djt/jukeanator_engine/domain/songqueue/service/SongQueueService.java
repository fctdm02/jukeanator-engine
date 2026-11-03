package com.djt.jukeanator_engine.domain.songqueue.service;

import java.util.List;
import java.util.function.Predicate;
import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongEligibilityDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongPlayHistoryEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

/**
 * @author tmyers
 */
public interface SongQueueService {
  
  /**
   * 
   */
  String LOCAL_USERNAME = "LOCAL";

  /**
   * A priority play exists to put the current user's songs ahead of other users' selections, so it
   * is offered only when the queue holds at least one user-selected song (priority 1 or higher)
   * that is not the current user's own. Never over an empty queue or background music alone
   * (priority 0), and never just to jump the current user's own songs. A priority play therefore
   * always queues at priority 2 or higher, and any entry above priority 1 is a priority play.
   *
   * @param isCurrentUsers whether a queued entry was selected by the current user (on the web,
   *        their email address; on the jukebox, a song queued during the current credit session)
   */
  static boolean isPriorityPlayAllowed(List<SongQueueEntryDto> queuedSongs,
      Predicate<SongQueueEntryDto> isCurrentUsers) {
    if (queuedSongs == null) {
      return false;
    }
    return queuedSongs.stream().anyMatch(entry -> entry.priority() != null
        && entry.priority() >= 1 && !isCurrentUsers.test(entry));
  }

  /**
   * NOTE: System method, not to be invoked on behalf of a user
   * 
   * @return
   */
  @PublicServiceMethod
  SongQueueEntryDto dequeueNextSong();

  /**
   * NOTE: System method, not to be invoked on behalf of a user
   * 
   * @return
   */
  @PublicServiceMethod
  boolean isQueueEmpty();

  /**
   * NOTE: System method, not to be invoked on behalf of a user
   * 
   * @return
   */
  @PublicServiceMethod
  boolean isBackgroundMusicEnabled();  

  // Every method below takes locationId as its first parameter -- standalone/slave instances have
  // exactly one location (their own) and always execute locally; the master instance forwards to
  // whichever location owns the request over the existing SlaveCommandGateway/ws-slave channel.
  // See SongQueueServiceImpl#isOwnLocation.
  /**
   *
   * @return a priority value that is one higher than the highest priority that is currently in the
   *         queue. For example, if the largest priority value of a song that is currently in the
   *         queue is 2, then return 3 If there are no songs in the queue, then return 2 (a random
   *         song will be always be priority 0 and a normal cost user selected song will always be
   *         priority 1).
   */
  Integer getHighestPriority(Integer locationId);

  /**
   *
   * @return
   */
  List<SongQueueEntryDto> getQueuedSongs(Integer locationId);

  /**
   *
   * @param albumId
   * @param songId
   * @param priority
   * @return The reason why the song was not eligible
   */
  String isSongEligibleForQueue(Integer locationId, Integer albumId, Integer songId,
      Integer priority);

  /**
   * Checks each song independently against the current queue, as
   * {@link #isSongEligibleForQueue} does for one song.
   *
   * @param songIdentifiers
   * @param priority
   * @return One result per song, in request order
   */
  List<SongEligibilityDto> checkSongsEligibility(Integer locationId,
      List<SongIdentifier> songIdentifiers, Integer priority);

  /**
   * @param addSongToQueueRequest
   * @return
   */
  SongQueueEntryDto addSongToQueue(Integer locationId, AddSongToQueueRequest addSongToQueueRequest);

  /**
   *
   * @param addAlbumToQueueRequest
   * @return
   */
  List<SongQueueEntryDto> addAlbumToQueue(Integer locationId,
      AddAlbumToQueueRequest addAlbumToQueueRequest);

  /**
   *
   * @param addMultipleSongsToQueueRequest
   * @return
   */
  List<SongQueueEntryDto> addMultipleSongsToQueue(Integer locationId,
      AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest);

  /**
   * @return
   */
  Integer flushQueue(Integer locationId);

  /**
   * @return
   */
  Integer randomizeQueue(Integer locationId);

  /**
   *
   * @param changeSongQueueRequest
   * @return
   */
  Integer moveSongUpInQueue(Integer locationId, ChangeSongQueueRequest changeSongQueueRequest);

  /**
   *
   * @param changeSongQueueRequest
   * @return
   */
  Integer moveSongDownInQueue(Integer locationId, ChangeSongQueueRequest changeSongQueueRequest);

  /**
   *
   * @param changeSongQueueRequest
   * @return
   */
  Integer removeSongDownFromQueue(Integer locationId, ChangeSongQueueRequest changeSongQueueRequest);

  /**
   * Locks the song queue. While locked, songs may still be queued (see {@link #addSongToQueue},
   * {@link #addAlbumToQueue}, {@link #addMultipleSongsToQueue}), but {@link #dequeueNextSong()}
   * will not dequeue/play any song.
   */
  void lock();

  /**
   * Unlocks the song queue, so that {@link #dequeueNextSong()} is once again allowed to
   * dequeue/play songs.
   */
  void unlock();

  /**
   * @return {@code true} if the song queue is currently locked (see {@link #lock()}), {@code
   *         false} otherwise.
   */
  boolean isLocked();

  /**
   * The songs this instance has dequeued for playback, newest first, starting with the song that
   * is playing now. Kept in memory only (empty after a restart) and capped to the most recent
   * entries. Local to this instance, so it takes no locationId and is never forwarded.
   *
   * @return the play history, newest first
   */
  List<SongPlayHistoryEntryDto> getSongPlayHistory();

  /**
   *
   * @param filename
   * @return
   */
  Integer saveQueueAsPlaylist(Integer locationId, String filename);

  /**
   *
   * @param loadPlaylistIntoQueueRequest
   * @return
   */
  Integer loadPlaylistIntoQueue(Integer locationId,
      LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest);

  /**
   * Persists the current song queue to disk.
   *
   * @return the number of songs currently in the queue
   */
  Integer storeSongQueue();

  /**
   *
   * @param event
   */
  @PublicServiceMethod
  void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event);

  /**
   * The locationId of the one location this instance itself owns, or {@code null} on the master
   * instance. Convenience for callers (e.g. Swing UI panels) that hold a {@code SongQueueService}
   * reference but not a {@code SongLibraryService} one -- delegates to {@code
   * SongLibraryService#getOwnLocationId()} under the hood.
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   */
  @PublicServiceMethod
  Integer getOwnLocationId();
}
