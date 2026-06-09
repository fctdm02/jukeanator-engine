package com.djt.jukeanator_engine.domain.songqueue.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

/**
 * @author tmyers
 */
public interface SongQueueService {

  /**
   * NOTE: This method should only be involved by SongPlayerService when playing the next song
   * 
   * @return
   */
  SongQueueEntryDto dequeueNextSong();

  /**
   * 
   * @return a priority value that is one higher than the highest priority that is currently in the
   *         queue. For example, if the largest priority value of a song that is currently in the
   *         queue is 2, then return 3 If there are no songs in the queue, then return 2 (a random
   *         song will be always be priority 0 and a normal cost user selected song will always be
   *         priority 1).
   */
  Integer getHighestPriority();

  /**
   * 
   * @return
   */
  List<SongQueueEntryDto> getQueuedSongs();

  /**
   * @param addSongToQueueRequest
   * @return
   */
  SongQueueEntryDto addSongToQueue(AddSongToQueueRequest addSongToQueueRequest);

  /**
   * 
   * @param addAlbumToQueueRequest
   * @return
   */
  List<SongQueueEntryDto> addAlbumToQueue(AddAlbumToQueueRequest addAlbumToQueueRequest);

  /**
   * 
   * @param addMultipleSongsToQueueRequest
   * @return
   */
  List<SongQueueEntryDto> addMultipleSongsToQueue(
      AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest);

  /**
   * @return
   */
  Integer flushQueue();

  /**
   * @return
   */
  Integer randomizeQueue();

  /**
   * 
   * @param changeSongQueueRequest
   * @return
   */
  Integer moveSongUpInQueue(ChangeSongQueueRequest changeSongQueueRequest);

  /**
   * 
   * @param changeSongQueueRequest
   * @return
   */
  Integer moveSongDownInQueue(ChangeSongQueueRequest changeSongQueueRequest);

  /**
   * 
   * @param changeSongQueueRequest
   * @return
   */
  Integer removeSongDownFromQueue(ChangeSongQueueRequest changeSongQueueRequest);

  /**
   * 
   * @param filename
   * @return
   */
  Integer saveQueueAsPlaylist(String filename);

  /**
   * 
   * @param filename
   * @return
   */
  Integer loadPlaylistIntoQueue(String filename);
}
