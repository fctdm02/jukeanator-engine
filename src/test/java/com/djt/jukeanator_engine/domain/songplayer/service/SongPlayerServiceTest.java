package com.djt.jukeanator_engine.domain.songplayer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml
public class SongPlayerServiceTest extends AbstractServiceIntegrationTest {

  @Autowired
  private SongPlayerService songPlayerService;

  @Autowired
  private SongQueueService songQueueService;

  @Autowired
  private SongLibraryService songLibraryService;

  @Test
  void shouldInitializeService() {
    assertNotNull(songLibraryService, "songLibraryService should be injected");
    assertNotNull(songQueueService, "songQueueService should be injected");
    assertNotNull(songPlayerService, "songPlayerService should be injected");
  }

  @Test
  @Disabled
  void lifecycle() throws IOException {

    // Scan for songs
    ScanRequest scanRequest = new ScanRequest(
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
            + "utils/SongScannerTest/RequireMetadataUseGenreTopFolder");
    Integer numAlbums = songLibraryService.scanFileSystemForSongs(scanRequest);
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
    AddSongToQueueRequest addSongToQueueRequest =
        new AddSongToQueueRequest(SongQueueService.LOCAL_USERNAME, albumId, songId, priority);
    SongQueueEntryDto queueEntry = songQueueService.addSongToQueue(addSongToQueueRequest);
    assertNotNull(queueEntry, "queueEntry should not be null");

    // Get the currently playing song
    SongDto nowPlayingSongDto = songPlayerService.getNowPlayingSong();
    assertNotNull(nowPlayingSongDto, "nowPlayingSongDto should not be null");

    SongPlaybackStatusDto songPlaybackStatusDto = songPlayerService.getPlaybackStatus();
    assertNotNull(songPlaybackStatusDto, "songPlaybackStatusDto should not be null");

    // Verify that the song queue is now empty
    List<SongQueueEntryDto> queuedSongs = songQueueService.getQueuedSongs();
    assertNotNull(queuedSongs, "queuedSongs should not be null");
    assertTrue(queuedSongs.size() == 0, "queuedSongs size should be zero");
  }
}