package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumMetaDataFileEntity;

/**
 * @author tmyers
 */
public class DiscogsClientWrapperTest {

  @Test
  public void searchForAlbumMetadata() throws IOException {

    // STEP 1: ARRANGE
    DiscogsClientWrapper discogsClientWrapper = createDiscogsClientWrapper();
    String artist = "Billy Idol";
    String album = "Vital Idol";


    // STEP 2: ACT
    Map<String, String> albumMetadataResults = discogsClientWrapper.searchForAlbumMetadata(artist, album);


    // STEP 3: ASSERT
    assertNotNull(albumMetadataResults, "albumMetadataResults was null");
    assertFalse(albumMetadataResults.isEmpty(), "albumMetadataResults expected to be non-empty");
    
    String expected = "https://i.discogs.com/cl5sG3Y7_n9cVne4vq0A5-0-3k0zoFYUVZioZsJ_LYs/rs:fit/g:sm/q:90/h:595/w:600/czM6Ly9kaXNjb2dz/LWRhdGFiYXNlLWlt/YWdlcy9SLTEwODE4/MDEtMTYxMjA1MTcy/NS00Njg3LmpwZWc.jpeg";
    String actual = albumMetadataResults.get(AlbumMetaDataFileEntity.CoverArtURL);     
    assertEquals(expected, actual, "coverArtUrl is incorrect");
    
    expected = "Chrysalis";
    actual = albumMetadataResults.get(AlbumMetaDataFileEntity.RecordLabel);     
    assertEquals(expected, actual, "recordLabel is incorrect");

    expected = "2002";
    actual = albumMetadataResults.get(AlbumMetaDataFileEntity.ReleaseDate);     
    assertEquals(expected, actual, "releaseDate is incorrect");
  }
  
  public static DiscogsClientWrapper createDiscogsClientWrapper() {
    
    String consumerKey = "vBSFEvNtGflHQnULBNnL";
    String consumerSecret = "AOOYhlvSshYkJieLRrdTCUoLcsWACfWW";
    return new DiscogsClientWrapper(consumerKey, consumerSecret);
  }
}
