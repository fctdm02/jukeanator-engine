package com.djt.jukeanator_engine.domain.songlibrary.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;

/**
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml
public class SongLibraryServiceTest {
  
  @Autowired
  private SongLibraryService songLibraryService;

  @Test
  void shouldInitializeService() {
      assertNotNull(songLibraryService, "Service should be injected");
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
  void scanFileSystemForSongs() throws IOException {
    
    // STEP 1: ARRANGE
    ScanRequest scanRequest = new ScanRequest("src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/SongScannerTest/RequireMetadataUseGenreTopFolder");
    ((SongLibraryServiceImpl)songLibraryService).setScanPath(scanRequest);
    
    
    // STEP 2: ACT
    Integer numAlbums = songLibraryService.scanFileSystemForSongs(scanRequest);
    
    
    // STEP 3: ASSERT    
    assertNotNull(numAlbums, "numAlbums should not be null");
    List<AlbumDto> albums = songLibraryService.getAlbums();    
    assertNotNull(albums, "albums should not be null");
    assertFalse(albums.isEmpty(), "albums should not be empty");
  }

  @Test
  void getLists() throws IOException {
    
    // STEP 1: ARRANGE
    ScanRequest scanRequest = new ScanRequest("src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/SongScannerTest/RequireMetadataUseGenreTopFolder");
    ((SongLibraryServiceImpl)songLibraryService).setScanPath(scanRequest);
    ((SongLibraryServiceImpl)songLibraryService).initializeSongLibrary();

    
    // STEP 2: ACT
    List<String> genres = songLibraryService.getGenres();
    List<String> artists = songLibraryService.getArtists();
    List<AlbumDto> albums = songLibraryService.getAlbums();
    
    
    // STEP 3: ASSERT
    assertNotNull(genres, "genres should not be null");
    assertFalse(genres.isEmpty(), "genres should not be empty");

    assertNotNull(artists, "artists should not be null");
    assertFalse(artists.isEmpty(), "artists should not be empty");
    
    assertNotNull(albums, "albums should not be null");
    assertFalse(albums.isEmpty(), "albums should not be empty");
  }   
}