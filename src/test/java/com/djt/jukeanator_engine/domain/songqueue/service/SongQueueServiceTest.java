package com.djt.jukeanator_engine.domain.songqueue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    
    // Lock the queue so that the queued song is not dequeued and played.
    // TODO: CLAUDE
    // songQueueService.lock();    

    // Add a song to the song queue
    Integer albumId = album.albumId();
    Integer songId = song.songId();
    Integer priority = Integer.valueOf(1);
    AddSongToQueueRequest addSongToQueueRequest =
        new AddSongToQueueRequest(SongQueueService.LOCAL_USERNAME, albumId, songId, priority);
    SongQueueEntryDto queueEntry = songQueueService.addSongToQueue(
        songLibraryService.getOwnLocationId(), addSongToQueueRequest);
    assertNotNull(queueEntry, "queueEntry should not be null");

    // Get the contents of the song queue
    List<SongQueueEntryDto> queuedSongs = songQueueService.getQueuedSongs(songLibraryService.getOwnLocationId());
    assertNotNull(queuedSongs, "queuedSongs should not be null");
    assertTrue(queuedSongs.size() > 0, "queuedSongs size should be non-zero");
    SongQueueEntryDto queuedSong = queuedSongs.get(0);
    assertEquals(queuedSong.song().songName(), song.songName(), "Song name is incorrect");

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
}