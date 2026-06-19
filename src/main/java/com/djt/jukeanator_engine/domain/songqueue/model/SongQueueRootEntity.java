package com.djt.jukeanator_engine.domain.songqueue.model;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

public class SongQueueRootEntity extends AbstractPersistentEntity {
  private static final long serialVersionUID = 2L;

  public static final String SONG_QUEUE_FILENAME = "JukeANator.PL";

  // Declaring the static constant for Jan 1st, 2020 at midnight UTC
  public static final Instant JAN_1_2020 = Instant.parse("2020-01-01T00:00:00Z");

  private String location;

  private ArrayList<SongQueueEntryEntity> queueEntries;

  public SongQueueRootEntity(String location, boolean resetQueuedAtTime) {
    super();
    requireNonNull(location, "location cannot be null");
    this.location = location;

    initialize(resetQueuedAtTime);
  }

  public void initialize(boolean resetQueuedAtTime) {

    queueEntries = new ArrayList<>();

    // Set the queuedAt time to be 01-01-2020, as we don't
    // care about the play history after a restart
    if (resetQueuedAtTime) {
      for (SongQueueEntryEntity song : queueEntries) {
        song.setQueuedAtTime(JAN_1_2020);
      }
    }
  }

  public String getLocation() {
    return location;
  }

  @Override
  public String getNaturalIdentity() {
    return location;
  }

  public List<SongQueueEntryEntity> getSongs() {

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
    return queueEntries;
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

    // Insert AFTER all queueEntries with the same or higher priority, and BEFORE any song with a
    // strictly lower priority. Walk forward until we find the first entry whose priority
    // is less than the new song's priority; that position is our insertion point.
    int index = queueEntries.size(); // default: append at end if no lower-priority song is found
    for (int i = 0; i < queueEntries.size(); i++) {

      if (queueEntries.get(i).getPriority() < priority) {
        index = i;
        break;
      }
    }

    queueEntries.add(index, entry);
    return entry;
  }

  /**
   * 
   * @return
   */
  public Integer flushQueue() {

    Integer numSongsFlushed = Integer.valueOf(this.queueEntries.size());
    this.queueEntries.clear();
    return numSongsFlushed;
  }

  /**
   * 
   * @return
   */
  public Integer randomizeQueue() {

    Collections.shuffle(queueEntries);
    return Integer.valueOf(this.queueEntries.size());
  }

  /**
   * Moves song up one position in the song queue.
   * 
   * @param song The song to move
   * @return The number of queueEntries in the song queue if the song was moved. Otherwise, if the
   *         song is already at the top of the queue, then -1 is removed
   */
  public Integer moveSongUpInQueue(SongFileEntity song) {

    int index = getIndexForSongQueueEntry(song);
    if (index > 0) {

      SongQueueEntryEntity current = this.queueEntries.get(index);
      this.queueEntries.set(index, this.queueEntries.get(index - 1));
      this.queueEntries.set(index - 1, current);

      return Integer.valueOf(this.queueEntries.size());
    }
    return -1;
  }

  /**
   * Moves song down one position in the song queue.
   * 
   * @param song The song to move
   * @return The number of queueEntries in the song queue if the song was moved. Otherwise, if the
   *         song is already at the bottom of the queue, then -1 is removed
   */
  public Integer moveSongDownInQueue(SongFileEntity song) {

    int index = getIndexForSongQueueEntry(song);
    if (index > 0) {

      SongQueueEntryEntity current = this.queueEntries.get(index);
      this.queueEntries.set(index, this.queueEntries.get(index + 1));
      this.queueEntries.set(index + 1, current);

      return Integer.valueOf(this.queueEntries.size());
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

      SongQueueEntryEntity current = this.queueEntries.get(index);
      this.queueEntries.remove(current);

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

    return this.queueEntries.remove(songQueueEntry);
  }

  private int getIndexForSongQueueEntry(SongFileEntity song) {

    for (SongQueueEntryEntity entry : queueEntries) {
      if (entry.getSong().equals(song)) {
        return queueEntries.indexOf(entry);
      }
    }
    return -1;
  }
}
