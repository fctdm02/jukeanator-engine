package com.djt.jukeanator_engine.domain.songlibrary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.djt.jukeanator_engine.AbstractServiceIntegrationTest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test") // loads application-test.yml
public class SongLibraryServiceTest extends AbstractServiceIntegrationTest {

  @Autowired
  private SongLibraryService songLibraryService;

  @Test
  void shouldInitializeService() {
    assertNotNull(songLibraryService, "Service should be injected");
  }

  @Test
  void scanFileSystemForSongs() throws IOException {

    // STEP 1: ARRANGE
    ScanRequest scanRequest = new ScanRequest(
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
            + "utils/SongScannerTest/RequireMetadataUseGenreTopFolder");

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
    ScanRequest scanRequest = new ScanRequest(
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
            + "utils/SongScannerTest/RequireMetadataUseGenreTopFolder");
    Integer numAlbums = songLibraryService.scanFileSystemForSongs(scanRequest);
    assertNotNull(numAlbums, "numAlbums should not be null");

    // STEP 2: ACT
    List<GenreDto> genres = songLibraryService.getGenres();
    List<ArtistDto> artists = songLibraryService.getArtists();
    List<AlbumDto> albums = songLibraryService.getAlbums();

    // STEP 3: ASSERT
    assertNotNull(genres, "genres should not be null");
    assertFalse(genres.isEmpty(), "genres should not be empty");

    assertNotNull(artists, "artists should not be null");
    assertFalse(artists.isEmpty(), "artists should not be empty");

    assertNotNull(albums, "albums should not be null");
    assertFalse(albums.isEmpty(), "albums should not be empty");
  }

  /**
   * NOTE: {@code scanFileSystemForSongs()} persists whatever num-plays counts are currently held
   * in memory to the CD stats file located alongside the scan folder (as a side effect of
   * preserving statistics across rescans), and {@code storeSongLibraryAndStatistics()} does the
   * same. Both the in-memory song entities and that file are reverted in the {@code tearDown()}
   * call below so this test leaves no trace behind for other tests -- or for a subsequent run of
   * this same test -- to trip over. A hard kill of the process cannot be intercepted, but any
   * normal completion or exception path is covered.
   */
  @Test
  void storeSongLibraryAndStatistics() throws IOException {

    // STEP 1: ARRANGE - load a song library
    ScanRequest scanRequest = new ScanRequest(
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
            + "utils/SongScannerTest/RequireMetadataUseGenreTopFolder");
    songLibraryService.scanFileSystemForSongs(scanRequest);

    Path cdStatsPath = Path.of(
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/"
            + "utils/SongScannerTest/RequireMetadataUseGenreTopFolderCDStats.TXT");
    byte[] originalCdStatsBytes =
        Files.exists(cdStatsPath) ? Files.readAllBytes(cdStatsPath) : null;

    RootFolderEntity root = songLibraryService.getSongLibraryRoot();
    List<SongFileEntity> songs = new ArrayList<>(root.getSongs());
    assertTrue(songs.size() >= 2, "Fixture should contain at least 2 songs to increment");

    SongFileEntity songA = songs.get(0);
    SongFileEntity songB = songs.get(1);
    Integer originalPlaysA = songA.getNumPlays();
    Integer originalPlaysB = songB.getNumPlays();

    try {

      // STEP 1 (cont'd): ARRANGE - increment the song plays for a couple of songs
      songA.incrementNumPlays();
      songA.incrementNumPlays();
      songA.incrementNumPlays();
      songB.incrementNumPlays();

      int expectedPlaysA = originalPlaysA + 3;
      int expectedPlaysB = originalPlaysB + 1;

      // STEP 2: ACT
      Integer numAlbums = songLibraryService.storeSongLibraryAndStatistics();

      // STEP 3: ASSERT - load the newly saved CD stats file and verify the play counts
      assertNotNull(numAlbums, "numAlbums should not be null");

      Map<String, Integer> numPlaysBySongPath = new HashMap<>();
      for (String rawLine : Files.readAllLines(cdStatsPath, StandardCharsets.UTF_8)) {
        String line = rawLine.trim();
        if (line.isEmpty()) {
          continue;
        }
        String[] parts = line.split(" ", 2);
        numPlaysBySongPath.put(parts[1], Integer.valueOf(parts[0]));
      }

      assertEquals(Integer.valueOf(expectedPlaysA),
          numPlaysBySongPath.get(songA.getNaturalIdentity()),
          "songA's num plays should have been persisted to the CD stats file");
      assertEquals(Integer.valueOf(expectedPlaysB),
          numPlaysBySongPath.get(songB.getNaturalIdentity()),
          "songB's num plays should have been persisted to the CD stats file");

    } finally {
      tearDown(cdStatsPath, originalCdStatsBytes, songA, originalPlaysA, songB, originalPlaysB);
    }
  }

  /**
   * Reverts the in-memory num-plays counts and restores the CD stats file to the bytes captured
   * before the test ran, so {@link #storeSongLibraryAndStatistics()} produces no side effects
   * regardless of whether it passed, failed, or threw.
   */
  private void tearDown(Path cdStatsPath, byte[] originalCdStatsBytes, SongFileEntity songA,
      Integer originalPlaysA, SongFileEntity songB, Integer originalPlaysB) throws IOException {

    songA.setNumPlays(originalPlaysA);
    songB.setNumPlays(originalPlaysB);

    if (originalCdStatsBytes != null) {
      Files.write(cdStatsPath, originalCdStatsBytes);
    } else {
      Files.deleteIfExists(cdStatsPath);
    }
  }
}