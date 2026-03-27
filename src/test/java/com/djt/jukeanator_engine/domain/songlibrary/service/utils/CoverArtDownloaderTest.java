package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author tmyers
 */
public class CoverArtDownloaderTest {
  
  @BeforeAll
  public static void beforeAll() throws IOException {
    
    cleanup();
  }

  @AfterAll
  public static void afterAll() throws IOException {
    
    cleanup();
  }
  
  public static void cleanup() throws IOException {
    
    String coverArtPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/CoverArtDownloaderTest/cover.jpg";
    Path path = Path.of(coverArtPath);
    Files.deleteIfExists(path);    
  }  
  
  @Test
  public void downloadCoverArt() throws IOException {

    // STEP 1: ARRANGE
    CoverArtDownloader coverArtFetcher = new CoverArtDownloader();
    String coverArtPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/CoverArtDownloaderTest/cover.jpg";
    String coverArtUrl = "https://i.discogs.com/cl5sG3Y7_n9cVne4vq0A5-0-3k0zoFYUVZioZsJ_LYs/rs:fit/g:sm/q:90/h:595/w:600/czM6Ly9kaXNjb2dz/LWRhdGFiYXNlLWlt/YWdlcy9SLTEwODE4/MDEtMTYxMjA1MTcy/NS00Njg3LmpwZWc.jpeg";


    // STEP 2: ACT
    coverArtFetcher.downloadCoverArt(coverArtPath, coverArtUrl);


    // STEP 3: ASSERT
    File coverArtFile = new File(coverArtPath);
    assertTrue(coverArtFile.exists(), "cover art expected to exist");
  }
}