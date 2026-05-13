package com.djt.jukeanator_engine.domain.songqueue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryServiceImpl;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;

/**
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml
public class SongQueueServiceTest {
  
  @Autowired
  private SongQueueService songQueueService;
  
  @Autowired
  private SongLibraryService songLibraryService;

  @Test
  void shouldInitializeService() {
      assertNotNull(songLibraryService, "Service should be injected");
      assertNotNull(songQueueService, "Service should be injected");
  }
     
  @BeforeAll
  public static void beforeAll() throws IOException {
    
    cleanup();
  }

  @AfterAll
  public static void afterAll() throws IOException {
    
    cleanup();
  }
  
  public static void cleanup() throws IOException {
    
    String objectFilePath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/SongScannerTest/RequireMetadataUseGenreTopFolder/JukeANator.oos";
    Path path = Path.of(objectFilePath);
    Files.deleteIfExists(path);    
  }

  @Test
  void lifecycle() throws IOException {
    
    // Scan for songs
    String scanPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/SongScannerTest/RequireMetadataUseGenreTopFolder";
    ((SongLibraryServiceImpl)songLibraryService).setScanPath(scanPath);
    Set<String> acceptedSongFileExtensions = Set.of(".mp3");
    Integer numAlbums = songLibraryService.scanFileSystemForSongs(scanPath, acceptedSongFileExtensions);
    assertNotNull(numAlbums, "numAlbums should not be null");
    List<AlbumDto> albums = songLibraryService.getAlbums();    
    assertNotNull(albums, "albums should not be null");
    assertFalse(albums.isEmpty(), "albums should not be empty");
    
    
    // Get a song from an album
    AlbumDto album = albums.get(0);
    SongDto song = album.getSongs().get(0);
    
    
    // Add a song to the song queue
    Integer albumId = album.getAlbumId();
    Integer songId = song.getSongId();
    Integer priority = Integer.valueOf(1);
    Integer songQueueIndex = songQueueService.addSongToQueue(albumId, songId, priority);
    assertNotNull(songQueueIndex, "songQueueIndex should not be null");
    assertTrue(songQueueIndex >= 0, "songQueueIndex should be non-zero");
    
    
    // Get the contents of the song queue
    List<SongQueueEntryDto> queuedSongs = songQueueService.getQueuedSongs();
    assertNotNull(queuedSongs, "queuedSongs should not be null");
    assertTrue(queuedSongs.size() > 0, "queuedSongs size should be non-zero");
    SongQueueEntryDto queuedSong = queuedSongs.get(0);
    assertEquals(queuedSong.getName(), song.getName(), "Song name is incorrect");
    
    
    // Remove the first entry in the queue (normally, only the song player service should be doing this)
    SongQueueEntryDto firstQueuedSong = songQueueService.getFirstEntryInSongQueue();
    assertNotNull(firstQueuedSong, "firstQueuedSong should not be null");    
    assertEquals(firstQueuedSong.getName(), song.getName(), "Song name is incorrect");
    
    
    // Verify that the song queue is now empty
    queuedSongs = songQueueService.getQueuedSongs();
    assertNotNull(queuedSongs, "queuedSongs should not be null");
    assertTrue(queuedSongs.size() == 0, "queuedSongs size should be zero");    
  }
}