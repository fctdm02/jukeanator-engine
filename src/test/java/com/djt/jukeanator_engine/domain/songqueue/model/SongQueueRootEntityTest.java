package com.djt.jukeanator_engine.domain.songqueue.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.songlibrary.model.AbstractSongLibraryEntityTest;

/**
 * Unit tests for {@link SongQueueRootEntity}.
 *
 * <p>Songs for queue entries are drawn from the in-memory library assembled by
 * {@link AbstractSongLibraryEntityTest}.
 */
public class SongQueueRootEntityTest extends AbstractSongLibraryEntityTest {

  private static final String QUEUE_ROOT_PATH = "/test-root";
  private static final String USER_A = "alice@example.com";
  private static final String USER_B = "bob@example.com";

  private static final int PRIORITY_HIGH = 10;
  private static final int PRIORITY_NORMAL = 5;
  private static final int PRIORITY_LOW = 1;

  private SongQueueRootEntity queue;

  @BeforeEach
  void setUpQueue() {
    queue = new SongQueueRootEntity(QUEUE_ROOT_PATH);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Constructor / getRootPath / setRootPath / getNaturalIdentity
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getRootPath_returnsPathPassedToConstructor() {
    assertEquals(QUEUE_ROOT_PATH, queue.getRootPath());
  }

  @Test
  void setRootPath_updatesRootPath() {
    queue.setRootPath("/new-root");
    assertEquals("/new-root", queue.getRootPath());
  }

  @Test
  void getNaturalIdentity_returnsRootPath() {
    assertEquals(QUEUE_ROOT_PATH, queue.getNaturalIdentity());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // isQueueEmpty / getSongs
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void isQueueEmpty_returnsTrueWhenNoSongsAdded() {
    assertTrue(queue.isQueueEmpty());
  }

  @Test
  void getSongs_returnsEmptyListWhenNoSongsAdded() {
    assertTrue(queue.getSongs().isEmpty());
  }

  @Test
  void isQueueEmpty_returnsFalseAfterAddingSong() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    assertFalse(queue.isQueueEmpty());
  }

  @Test
  void getSongs_returnsAddedSong() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    List<SongQueueEntryEntity> songs = queue.getSongs();
    assertEquals(1, songs.size());
    assertEquals(songBlackDog, songs.get(0).getSong());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // addSongToQueue — ordering
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void addSongToQueue_returnsEntryWithCorrectSongAndPriority() {
    SongQueueEntryEntity entry = queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    assertNotNull(entry);
    assertEquals(songBlackDog, entry.getSong());
    assertEquals(PRIORITY_NORMAL, entry.getPriority());
    assertEquals(USER_A, entry.getUsername());
  }

  @Test
  void addSongToQueue_highPrioritySongInsertedBeforeNormalPriority() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_HIGH);

    List<SongQueueEntryEntity> songs = queue.getSongs();
    assertEquals(songKashmir, songs.get(0).getSong(), "High-priority song should be first");
    assertEquals(songBlackDog, songs.get(1).getSong(), "Normal-priority song should be second");
  }

  @Test
  void addSongToQueue_lowPrioritySongAppendedAfterNormalPriority() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_LOW);

    List<SongQueueEntryEntity> songs = queue.getSongs();
    assertEquals(songBlackDog, songs.get(0).getSong(), "Normal-priority song should be first");
    assertEquals(songKashmir, songs.get(1).getSong(), "Low-priority song should be last");
  }

  @Test
  void addSongToQueue_samePriorityAppendsAfterExistingEntries() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_A, songMaterialGirl, PRIORITY_NORMAL);

    List<SongQueueEntryEntity> songs = queue.getSongs();
    assertEquals(songBlackDog, songs.get(0).getSong());
    assertEquals(songKashmir, songs.get(1).getSong());
    assertEquals(songMaterialGirl, songs.get(2).getSong());
  }

  @Test
  void addSongToQueue_mixedPrioritiesAreOrderedCorrectly() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_A, songMaterialGirl, PRIORITY_LOW);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_HIGH);
    queue.addSongToQueue(USER_B, songRockAndRoll, PRIORITY_NORMAL);

    List<SongQueueEntryEntity> songs = queue.getSongs();
    assertEquals(songKashmir, songs.get(0).getSong(),     "HIGH first");
    assertEquals(songBlackDog, songs.get(1).getSong(),    "NORMAL (first added) second");
    assertEquals(songRockAndRoll, songs.get(2).getSong(), "NORMAL (second added) third");
    assertEquals(songMaterialGirl, songs.get(3).getSong(),"LOW last");
  }

  // ─────────────────────────────────────────────────────────────────────────
  // flushQueue
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void flushQueue_returnsZeroWhenQueueAlreadyEmpty() {
    assertEquals(Integer.valueOf(0), queue.flushQueue());
  }

  @Test
  void flushQueue_returnsCountOfRemovedSongs() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_NORMAL);
    assertEquals(Integer.valueOf(2), queue.flushQueue());
  }

  @Test
  void flushQueue_leavesQueueEmpty() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.flushQueue();
    assertTrue(queue.isQueueEmpty());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // randomizeQueue
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void randomizeQueue_returnsCurrentQueueSize() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_A, songMaterialGirl, PRIORITY_NORMAL);

    assertEquals(Integer.valueOf(3), queue.randomizeQueue());
  }

  @Test
  void randomizeQueue_retainsAllSongsAfterShuffle() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_A, songMaterialGirl, PRIORITY_NORMAL);

    queue.randomizeQueue();

    List<SongQueueEntryEntity> songs = queue.getSongs();
    assertEquals(3, songs.size());
    assertTrue(songs.stream().anyMatch(e -> e.getSong().equals(songBlackDog)));
    assertTrue(songs.stream().anyMatch(e -> e.getSong().equals(songKashmir)));
    assertTrue(songs.stream().anyMatch(e -> e.getSong().equals(songMaterialGirl)));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // moveSongUpInQueue
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void moveSongUpInQueue_returnsNegativeOneWhenSongIsAlreadyAtTop() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    assertEquals(Integer.valueOf(-1), queue.moveSongUpInQueue(songBlackDog));
  }

  @Test
  void moveSongUpInQueue_movesSongUpOnePosition() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songRockAndRoll, PRIORITY_NORMAL);

    queue.moveSongUpInQueue(songRockAndRoll);

    List<SongQueueEntryEntity> songs = queue.getSongs();
    assertEquals(songRockAndRoll, songs.get(0).getSong());
    assertEquals(songBlackDog, songs.get(1).getSong());
  }

  @Test
  void moveSongUpInQueue_returnsQueueSizeOnSuccess() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songRockAndRoll, PRIORITY_NORMAL);

    assertEquals(Integer.valueOf(2), queue.moveSongUpInQueue(songRockAndRoll));
  }

  @Test
  void moveSongUpInQueue_withPreferredIndex_movesDuplicateSongAtThatIndex() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songBlackDog, PRIORITY_NORMAL); // duplicate
    queue.addSongToQueue(USER_A, songKashmir, PRIORITY_NORMAL);

    // Move the second Black Dog (index 1) up using preferredIndex
    queue.moveSongUpInQueue(songBlackDog, 1);

    List<SongQueueEntryEntity> songs = queue.getSongs();
    // The entry at index 1 (second Black Dog) should now be at index 0
    assertEquals(songBlackDog, songs.get(0).getSong());
    assertEquals(USER_B, songs.get(0).getUsername());
    assertEquals(songBlackDog, songs.get(1).getSong());
    assertEquals(USER_A, songs.get(1).getUsername());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // moveSongDownInQueue
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void moveSongDownInQueue_returnsNegativeOneWhenSongIsAlreadyAtBottom() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    assertEquals(Integer.valueOf(-1), queue.moveSongDownInQueue(songBlackDog));
  }

  @Test
  void moveSongDownInQueue_movesSongDownOnePosition() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_NORMAL);

    queue.moveSongDownInQueue(songBlackDog);

    List<SongQueueEntryEntity> songs = queue.getSongs();
    assertEquals(songKashmir, songs.get(0).getSong());
    assertEquals(songBlackDog, songs.get(1).getSong());
  }

  @Test
  void moveSongDownInQueue_returnsQueueSizeOnSuccess() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_NORMAL);

    assertEquals(Integer.valueOf(2), queue.moveSongDownInQueue(songBlackDog));
  }

  @Test
  void moveSongDownInQueue_withPreferredIndex_movesDuplicateSongAtThatIndex() {
    queue.addSongToQueue(USER_A, songKashmir, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songBlackDog, PRIORITY_NORMAL); // duplicate at index 1
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL); // duplicate at index 2

    // Move the first Black Dog (index 1) down using preferredIndex
    queue.moveSongDownInQueue(songBlackDog, 1);

    List<SongQueueEntryEntity> songs = queue.getSongs();
    assertEquals(songKashmir, songs.get(0).getSong());
    assertEquals(songBlackDog, songs.get(1).getSong());
    assertEquals(USER_A, songs.get(1).getUsername());
    assertEquals(songBlackDog, songs.get(2).getSong());
    assertEquals(USER_B, songs.get(2).getUsername());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // removeSongFromQueue(SongFileEntity)
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void removeSongFromQueue_bySong_returnsOneAndRemovesSong() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_NORMAL);

    Integer removed = queue.removeSongFromQueue(songBlackDog);

    assertEquals(Integer.valueOf(1), removed);
    assertEquals(1, queue.getSongs().size());
    assertEquals(songKashmir, queue.getSongs().get(0).getSong());
  }

  @Test
  void removeSongFromQueue_bySong_returnsZeroWhenSongNotInQueue() {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);

    Integer removed = queue.removeSongFromQueue(songRockAndRoll);

    assertEquals(Integer.valueOf(0), removed);
    assertEquals(1, queue.getSongs().size());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // removeSongFromQueue(SongQueueEntryEntity)
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void removeSongFromQueue_byEntry_returnsTrueAndRemovesEntry() {
    SongQueueEntryEntity entry = queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_NORMAL);

    boolean removed = queue.removeSongFromQueue(entry);

    assertTrue(removed);
    assertEquals(1, queue.getSongs().size());
    assertEquals(songKashmir, queue.getSongs().get(0).getSong());
  }

  @Test
  void removeSongFromQueue_byEntry_returnsFalseForEntryNotInQueue() {
    SongQueueEntryEntity entry = queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.removeSongFromQueue(entry); // remove it first

    assertFalse(queue.removeSongFromQueue(entry));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // resetQueuedAtTime
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void resetQueuedAtTime_setsAllEntriesQueuedAtTimeToJan2020() throws InterruptedException {
    queue.addSongToQueue(USER_A, songBlackDog, PRIORITY_NORMAL);
    queue.addSongToQueue(USER_B, songKashmir, PRIORITY_NORMAL);

    queue.resetQueuedAtTime();

    for (SongQueueEntryEntity entry : queue.getSongs()) {
      assertEquals(SongQueueRootEntity.JAN_1_2020, entry.getQueuedAtTime(),
          "Expected queuedAtTime to be reset to JAN_1_2020 for: " + entry.getSong().getSongName());
    }
  }

  @Test
  void resetQueuedAtTime_onEmptyQueue_doesNotThrow() {
    queue.resetQueuedAtTime();
    assertTrue(queue.isQueueEmpty());
  }
}
