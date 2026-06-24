package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    String artist = "Billy Idol";
    String album = "Vital Idol";


    // STEP 2: ACT
    List<AlbumMetadataDto> albumMetadataResults = discogsClientWrapper.searchForAlbumMetadata(artist, album, 3);


    // STEP 3: ASSERT
    assertNotNull(albumMetadataResults, "albumMetadataResults was null");
    assertFalse(albumMetadataResults.isEmpty(), "albumMetadataResults expected to be non-empty");
  }

  public static DiscogsClientWrapper createDiscogsClientWrapper() {

    String consumerKey = "vBSFEvNtGflHQnULBNnL";
    String consumerSecret = "AOOYhlvSshYkJieLRrdTCUoLcsWACfWW";
    return new DiscogsClientWrapper(consumerKey, consumerSecret);
  }
}
