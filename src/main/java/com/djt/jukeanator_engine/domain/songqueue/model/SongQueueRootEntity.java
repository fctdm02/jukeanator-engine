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

  private ArrayList<SongQueueEntryEntity> queueEntries = new ArrayList<>();
  private String rootPath;

  public SongQueueRootEntity(String rootPath) {

    super(null);
    requireNonNull(rootPath, "rootPath cannot be null");
    this.rootPath = rootPath;
  }

  public void resetQueuedAtTime() {

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }

    // Set the queuedAt time to be 01-01-2020, as we don't
    // care about the play history after a restart
    for (SongQueueEntryEntity song : queueEntries) {
      song.setQueuedAtTime(JAN_1_2020);
    }
  }

  public String getRootPath() {
    return this.rootPath;
  }

  public void setRootPath(String rootPath) {
    this.rootPath = rootPath;
  }

  @Override
  public String getNaturalIdentity() {
    return this.rootPath;
  }

  public boolean isQueueEmpty() {

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
    return this.queueEntries.isEmpty();
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

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }

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

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
    Integer numSongsFlushed = Integer.valueOf(this.queueEntries.size());
    this.queueEntries.clear();
    return numSongsFlushed;
  }

  /**
   * 
   * @return
   */
  public Integer randomizeQueue() {

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
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
    return moveSongUpInQueue(song, -1);
  }

  public Integer moveSongUpInQueue(SongFileEntity song, int preferredIndex) {

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
    int index = resolveIndex(song, preferredIndex);
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
    return moveSongDownInQueue(song, -1);
  }

  public Integer moveSongDownInQueue(SongFileEntity song, int preferredIndex) {

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
    int index = resolveIndex(song, preferredIndex);
    if (index >= 0 && index < this.queueEntries.size() - 1) {

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

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
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

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
    return this.queueEntries.remove(songQueueEntry);
  }

  /**
   * Resolves the queue index for a song. If {@code preferredIndex} is a valid index and the entry
   * there matches {@code song}, it is used directly (handles duplicate songs correctly). Otherwise
   * falls back to scanning for the first matching entry.
   */
  private int resolveIndex(SongFileEntity song, int preferredIndex) {

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
    if (preferredIndex >= 0 && preferredIndex < queueEntries.size()
        && queueEntries.get(preferredIndex).getSong().equals(song)) {
      return preferredIndex;
    }
    return getIndexForSongQueueEntry(song);
  }

  private int getIndexForSongQueueEntry(SongFileEntity song) {

    if (queueEntries == null) {
      queueEntries = new ArrayList<>();
    }
    for (SongQueueEntryEntity entry : queueEntries) {
      if (entry.getSong().equals(song)) {
        return queueEntries.indexOf(entry);
      }
    }
    return -1;
  }
}
