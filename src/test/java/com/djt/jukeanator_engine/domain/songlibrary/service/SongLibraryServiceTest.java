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
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

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
    songLibraryService.setScanPath(scanPath);
    Set<String> acceptedSongFileExtensions = Set.of(".mp3");
    
    
    // STEP 2: ACT
    RootFolderEntity root = songLibraryService.scanFileSystemForSongs(scanPath, acceptedSongFileExtensions);
    
    
    // STEP 3: ASSERT    
    assertNotNull(root, "Root should not be null");
    List<GenreFolderEntity> genres = songLibraryService.getGenres();
    List<ArtistFolderEntity> artists = songLibraryService.getArtists();
    List<AlbumFolderEntity> albums = songLibraryService.getAlbums();    
    assertNotNull(genres, "genres should not be null");
    assertFalse(genres.isEmpty(), "genres should not be empty");
    assertNotNull(artists, "artists should not be null");
    assertFalse(artists.isEmpty(), "artists should not be empty");
    assertNotNull(albums, "albums should not be null");
    assertFalse(albums.isEmpty(), "albums should not be empty");
  }

  @Test
  void getLists() throws IOException {
    
    // STEP 1: ARRANGE
    String scanPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/SongScannerTest/RequireMetadataUseGenreTopFolder";
    songLibraryService.setScanPath(scanPath);
    songLibraryService.initializeSongLibrary();

    
    // STEP 2: ACT
    List<GenreFolderEntity> genres = songLibraryService.getGenres();
    List<ArtistFolderEntity> artists = songLibraryService.getArtists();
    List<AlbumFolderEntity> albums = songLibraryService.getAlbums();
    List<SongFileEntity> songs = songLibraryService.getSongs();
    
    
    // STEP 3: ASSERT
    assertNotNull(genres, "genres should not be null");
    assertFalse(genres.isEmpty(), "genres should not be empty");
    assertNotNull(artists, "artists should not be null");
    assertFalse(artists.isEmpty(), "artists should not be empty");
    assertNotNull(albums, "albums should not be null");
    assertFalse(albums.isEmpty(), "albums should not be empty");
    assertNotNull(songs, "songs should not be null");
    assertFalse(songs.isEmpty(), "songs should not be empty");    
  }
   
}