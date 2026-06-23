package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    AlbumMetadataDto albumMetadataResult = albumMetadataResults.get(0);

    String expected =
        "https://i.discogs.com/d_m3zdDiDw_v_ABtlF5-zOybNp-hyJBEnn4lZEx5whQ/rs:fit/g:sm/q:90/h:268/w:249/czM6Ly9kaXNjb2dz/LWRhdGFiYXNlLWlt/YWdlcy9SLTM3NDM5/ODItMTM0Mjc4NTE1/NS05MTUwLmpwZWc.jpeg";
    String actual = albumMetadataResult.getCoverArtUrl();
    assertEquals(expected, actual, "coverArtUrl is incorrect");

    expected = "Chrysalis";
    actual = albumMetadataResult.getRecordLabel();
    assertEquals(expected, actual, "recordLabel is incorrect");

    expected = "1985";
    actual = albumMetadataResult.getReleaseDate();
    assertEquals(expected, actual, "releaseDate is incorrect");
  }

  public static DiscogsClientWrapper createDiscogsClientWrapper() {

    String consumerKey = "vBSFEvNtGflHQnULBNnL";
    String consumerSecret = "AOOYhlvSshYkJieLRrdTCUoLcsWACfWW";
    return new DiscogsClientWrapper(consumerKey, consumerSecret);
  }
}
