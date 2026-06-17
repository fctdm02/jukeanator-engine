package com.djt.jukeanator_engine.domain.songqueue.model;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

public class SongQueueRootEntity extends AbstractPersistentEntity {
  private static final long serialVersionUID = 2L;

  public static final String SONG_QUEUE_FILENAME = "JukeANator.PL";

  private String location;

  private ArrayList<SongQueueEntryEntity> songs = new ArrayList<>();

  public SongQueueRootEntity() {}

  public SongQueueRootEntity(String location) {
    super();
    requireNonNull(location, "location cannot be null");
    this.location = location;
  }

  public String getLocation() {
    return location;
  }

  @Override
  public String getNaturalIdentity() {
    return location;
  }

  public List<SongQueueEntryEntity> getSongs() {
    return songs;
  }

  /**
   * 
   * @param song
   * @param priority
   * @return
   */
  public SongQueueEntryEntity addSongToQueue(String username, SongFileEntity song,
      Integer priority) {

    SongQueueEntryEntity entry = new SongQueueEntryEntity(username, song, priority);

    // Insert AFTER all songs with the same or higher priority, and BEFORE any song with a
    // strictly lower priority. Walk forward until we find the first entry whose priority
    // is less than the new song's priority; that position is our insertion point.
    int index = songs.size(); // default: append at end if no lower-priority song is found
    for (int i = 0; i < songs.size(); i++) {

      if (songs.get(i).getPriority() < priority) {
        index = i;
        break;
      }
    }

    songs.add(index, entry);
    return entry;
  }

  /**
   * 
   * @return
   */
  public Integer flushQueue() {

    Integer numSongsFlushed = Integer.valueOf(this.songs.size());
    this.songs.clear();
    return numSongsFlushed;
  }

  /**
   * 
   * @return
   */
  public Integer randomizeQueue() {

    Collections.shuffle(songs);
    return Integer.valueOf(this.songs.size());
  }

  /**
   * Moves song up one position in the song queue.
   * 
   * @param song The song to move
   * @return The number of songs in the song queue if the song was moved. Otherwise, if the song is
   *         already at the top of the queue, then -1 is removed
   */
  public Integer moveSongUpInQueue(SongFileEntity song) {

    int index = getIndexForSongQueueEntry(song);
    if (index > 0) {

      SongQueueEntryEntity current = this.songs.get(index);
      this.songs.set(index, this.songs.get(index - 1));
      this.songs.set(index - 1, current);

      return Integer.valueOf(this.songs.size());
    }
    return -1;
  }

  /**
   * Moves song down one position in the song queue.
   * 
   * @param song The song to move
   * @return The number of songs in the song queue if the song was moved. Otherwise, if the song is
   *         already at the bottom of the queue, then -1 is removed
   */
  public Integer moveSongDownInQueue(SongFileEntity song) {

    int index = getIndexForSongQueueEntry(song);
    if (index > 0) {

      SongQueueEntryEntity current = this.songs.get(index);
      this.songs.set(index, this.songs.get(index + 1));
      this.songs.set(index + 1, current);

      return Integer.valueOf(this.songs.size());
    }
    return -1;
  }

  /**
   * 
   * @param song
   * @return
   */
  public Integer removeSongFromQueue(SongFileEntity song) {

    int index = getIndexForSongQueueEntry(song);
    if (index >= 0) {

      SongQueueEntryEntity current = this.songs.get(index);
      this.songs.remove(current);

      return Integer.valueOf(1);
    }
    return Integer.valueOf(0);
  }

  /**
   * 
   * @param songQueueEntry
   * @return
   */
  public boolean removeSongFromQueue(SongQueueEntryEntity songQueueEntry) {

    return this.songs.remove(songQueueEntry);
  }

  private int getIndexForSongQueueEntry(SongFileEntity song) {

    for (SongQueueEntryEntity entry : songs) {
      if (entry.getSong().equals(song)) {
        return songs.indexOf(entry);
      }
    }
    return -1;
  }
}
