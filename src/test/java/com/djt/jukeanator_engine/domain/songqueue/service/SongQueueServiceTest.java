package com.djt.jukeanator_engine.domain.songqueue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongPlayHistoryEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

/**
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml
public class SongQueueServiceTest extends AbstractServiceIntegrationTest {

  @Autowired
  private SongQueueService songQueueService;

  @Autowired
  private SongLibraryService songLibraryService;

  @Test
  void shouldInitializeService() {
    assertNotNull(songLibraryService, "Service should be injected");
    assertNotNull(songQueueService, "Service should be injected");
  }

  @Test  
  void lifecycle() throws IOException {

    // Scan for songs
    ScanRequest scanRequest = new ScanRequest(
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
            + "utils/SongScannerTest/RequireMetadataUseGenreTopFolder");
    Integer numAlbums = songLibraryService.scanFileSystemForSongs(scanRequest);
    assertNotNull(numAlbums, "numAlbums should not be null");
    List<AlbumDto> albums = songLibraryService.getAlbums(songLibraryService.getOwnLocationId());
    assertNotNull(albums, "albums should not be null");
    assertFalse(albums.isEmpty(), "albums should not be empty");

    // Get a song from an album
    AlbumDto album = albums.get(0);
    SongDto song = album.songs().get(0);
    
    // Lock the queue so that the queued song is not dequeued and played out from under this test
    // by SongPlayerServiceImpl's background queue-processing thread, which reacts to
    // SongAddedToQueueEvent (fired by addSongToQueue() below) by asynchronously calling
    // songQueueService.dequeueNextSong() on its own thread.
    songQueueService.lock();

    // Add a song to the song queue
    Integer albumId = album.albumId();
    Integer songId = song.songId();
    Integer priority = Integer.valueOf(1);
    AddSongToQueueRequest addSongToQueueRequest =
        new AddSongToQueueRequest(SongQueueService.LOCAL_USERNAME, albumId, songId, priority, false);
    SongQueueEntryDto queueEntry = songQueueService.addSongToQueue(
        songLibraryService.getOwnLocationId(), addSongToQueueRequest);
    assertNotNull(queueEntry, "queueEntry should not be null");

    // Get the contents of the song queue
    List<SongQueueEntryDto> queuedSongs = songQueueService.getQueuedSongs(songLibraryService.getOwnLocationId());
    assertNotNull(queuedSongs, "queuedSongs should not be null");
    assertTrue(queuedSongs.size() > 0, "queuedSongs size should be non-zero");
    SongQueueEntryDto queuedSong = queuedSongs.get(0);
    assertEquals(queuedSong.song().songName(), song.songName(), "Song name is incorrect");

    // Unlock the queue so that this test can now dequeue the song itself.
    songQueueService.unlock();

    // Remove the first entry in the queue (normally only the song player service should do this)
    SongQueueEntryDto firstQueuedSong = songQueueService.dequeueNextSong();
    assertNotNull(firstQueuedSong, "firstQueuedSong should not be null");
    assertEquals(firstQueuedSong.song().songName(), song.songName(),
        "Song name is incorrect");

    // Verify that the song queue is now empty
    queuedSongs = songQueueService.getQueuedSongs(songLibraryService.getOwnLocationId());
    assertNotNull(queuedSongs, "queuedSongs should not be null");
    assertTrue(queuedSongs.size() == 0, "queuedSongs size should be zero");
  }

  @Test
  void lockPreventsDequeueButAllowsQueueing() throws IOException, InterruptedException {

    // Scan for songs
    ScanRequest scanRequest = new ScanRequest(
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
            + "utils/SongScannerTest/RequireMetadataUseGenreTopFolder");
    songLibraryService.scanFileSystemForSongs(scanRequest);
    List<AlbumDto> albums = songLibraryService.getAlbums(songLibraryService.getOwnLocationId());
    AlbumDto album = albums.get(0);
    SongDto song = album.songs().get(0);

    songQueueService.lock();
    try {

      assertTrue(songQueueService.isLocked(), "isLocked() should return true after lock()");

      // Queueing a song should still be allowed while the queue is locked.
      AddSongToQueueRequest addSongToQueueRequest = new AddSongToQueueRequest(
          SongQueueService.LOCAL_USERNAME, album.albumId(), song.songId(), Integer.valueOf(1), false);
      SongQueueEntryDto queueEntry = songQueueService.addSongToQueue(
          songLibraryService.getOwnLocationId(), addSongToQueueRequest);
      assertNotNull(queueEntry, "Songs should still be queueable while the queue is locked");

      // Give SongPlayerServiceImpl's background queue-processing thread (triggered by the
      // SongAddedToQueueEvent published above) a moment to attempt -- and be blocked from --
      // dequeuing the song we just queued.
      Thread.sleep(500);

      List<SongQueueEntryDto> queuedSongs =
          songQueueService.getQueuedSongs(songLibraryService.getOwnLocationId());
      boolean stillQueued = queuedSongs.stream()
          .anyMatch(entry -> entry.song().songName().equals(song.songName()));
      assertTrue(stillQueued, "Song should not have been dequeued while the queue is locked");

      // A direct call to dequeueNextSong() should also be refused while locked.
      assertNull(songQueueService.dequeueNextSong(),
          "dequeueNextSong() should return null while the queue is locked");

    } finally {
      songQueueService.unlock();
    }

    assertFalse(songQueueService.isLocked(), "isLocked() should return false after unlock()");

    // Once unlocked, dequeueing should work again.
    SongQueueEntryDto dequeuedSong = songQueueService.dequeueNextSong();
    assertNotNull(dequeuedSong, "dequeueNextSong() should work again once the queue is unlocked");
    assertEquals(song.songName(), dequeuedSong.song().songName(), "Song name is incorrect");
  }

  @Test
  void dequeuedSongIsRecordedAtTopOfPlayHistory() throws IOException {

    ScanRequest scanRequest = new ScanRequest(
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
            + "utils/SongScannerTest/RequireMetadataUseGenreTopFolder");
    songLibraryService.scanFileSystemForSongs(scanRequest);
    List<AlbumDto> albums = songLibraryService.getAlbums(songLibraryService.getOwnLocationId());
    // The other tests queue the first song of the first album; use a different one so the
    // minimum-minutes-between-plays rule does not reject it when the tests share a context.
    AlbumDto album = albums.get(albums.size() - 1);
    SongDto song = album.songs().get(album.songs().size() - 1);

    // Locked so SongPlayerServiceImpl's background thread does not dequeue it first.
    songQueueService.lock();
    try {
      songQueueService.addSongToQueue(songLibraryService.getOwnLocationId(),
          new AddSongToQueueRequest(SongQueueService.LOCAL_USERNAME, album.albumId(),
              song.songId(), Integer.valueOf(2), false));
    } finally {
      songQueueService.unlock();
    }
    SongQueueEntryDto dequeued = songQueueService.dequeueNextSong();

    List<SongPlayHistoryEntryDto> history = songQueueService.getSongPlayHistory();
    assertFalse(history.isEmpty(), "History should hold the dequeued song");
    assertTrue(history.size() <= 100, "History should stay within its cap");

    // Whichever thread dequeued it, it is the most recent play, so it is first.
    SongPlayHistoryEntryDto newest = history.get(0);
    assertEquals(song.songName(), newest.entry().song().songName(), "Newest entry is wrong");
    assertEquals(Integer.valueOf(2), newest.entry().priority(), "Priority should be kept");
    assertNotNull(newest.startedAt(), "Start time should be recorded");
    if (dequeued != null) {
      assertEquals(dequeued, newest.entry(), "Newest entry should be the dequeued song");
    }

    for (int i = 1; i < history.size(); i++) {
      assertFalse(history.get(i).startedAt().isAfter(history.get(i - 1).startedAt()),
          "History should be newest first");
    }
  }

  @Test
  void isLockedReflectsLockUnlockState() {

    assertFalse(songQueueService.isLocked(), "Queue should start unlocked");

    songQueueService.lock();
    assertTrue(songQueueService.isLocked(), "isLocked() should return true after lock()");

    songQueueService.unlock();
    assertFalse(songQueueService.isLocked(), "isLocked() should return false after unlock()");
  }
}