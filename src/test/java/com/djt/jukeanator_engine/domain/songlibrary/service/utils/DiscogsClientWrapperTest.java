package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;

/**
 * @author tmyers
 */
public class DiscogsClientWrapperTest {

  @Test
  public void searchForAlbumMetadata() throws IOException {

    // STEP 1: ARRANGE
    DiscogsClientWrapper discogsClientWrapper = createDiscogsClientWrapper();
    assumeTrue(discogsClientWrapper.hasValidApiKey(),
        "DISCOGS_CONSUMER_KEY / DISCOGS_CONSUMER_SECRET environment variables are not set");
    String artist = "Billy Idol";
    String album = "Vital Idol";


    // STEP 2: ACT
    List<AlbumMetadataDto> albumMetadataResults = discogsClientWrapper.searchForAlbumMetadata(artist, album, 3);


    // STEP 3: ASSERT
    assertNotNull(albumMetadataResults, "albumMetadataResults was null");
    assertFalse(albumMetadataResults.isEmpty(), "albumMetadataResults expected to be non-empty");
  }

  public static DiscogsClientWrapper createDiscogsClientWrapper() {

    String consumerKey = getEnvOrDummy("DISCOGS_CONSUMER_KEY");
    String consumerSecret = getEnvOrDummy("DISCOGS_CONSUMER_SECRET");
    return new DiscogsClientWrapper(consumerKey, consumerSecret);
  }

  private static String getEnvOrDummy(String name) {

    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return "DUMMY";
    }
    return value.trim();
  }
}
