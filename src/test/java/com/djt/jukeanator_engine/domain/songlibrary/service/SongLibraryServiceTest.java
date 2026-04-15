package com.djt.jukeanator_engine.domain.songlibrary.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

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
    String scanPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/SongScannerTest/RequireMetadataUseGenreTopFolder";
    ((SongLibraryServiceImpl)songLibraryService).setScanPath(scanPath);
    Set<String> acceptedSongFileExtensions = Set.of(".mp3");
    
    
    // STEP 2: ACT
    RootFolderEntity root = songLibraryService.scanFileSystemForSongs(scanPath, acceptedSongFileExtensions);
    
    
    // STEP 3: ASSERT    
    assertNotNull(root, "Root should not be null");
    List<AlbumFolderEntity> albums = songLibraryService.getAlbums();    
    assertNotNull(albums, "albums should not be null");
    assertFalse(albums.isEmpty(), "albums should not be empty");
  }

  @Test
  void getLists() throws IOException {
    
    // STEP 1: ARRANGE
    String scanPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/SongScannerTest/RequireMetadataUseGenreTopFolder";
    ((SongLibraryServiceImpl)songLibraryService).setScanPath(scanPath);
    ((SongLibraryServiceImpl)songLibraryService).initializeSongLibrary();

    
    // STEP 2: ACT
    List<String> genres = songLibraryService.getGenres();
    List<String> artists = songLibraryService.getArtists();
    List<AlbumFolderEntity> albums = songLibraryService.getAlbums();
    
    
    // STEP 3: ASSERT
    assertNotNull(genres, "genres should not be null");
    assertFalse(genres.isEmpty(), "genres should not be empty");

    assertNotNull(artists, "artists should not be null");
    assertFalse(artists.isEmpty(), "artists should not be empty");
    
    assertNotNull(albums, "albums should not be null");
    assertFalse(albums.isEmpty(), "albums should not be empty");
  }   
}