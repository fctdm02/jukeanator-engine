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
public class MusicBrainzClientWrapperTest {

  @Test
  public void searchForAlbumMetadata() throws IOException {

    // STEP 1: ARRANGE
    MusicBrainzClientWrapper musicBrainzClientWrapper = new MusicBrainzClientWrapper();
    String artist = "Billy Idol";
    String album = "Vital Idol";
    boolean useGenre = true;


    // STEP 2: ACT
    List<AlbumMetadataDto> albumMetadataResults =
        musicBrainzClientWrapper.searchForAlbumMetadata(artist, album, useGenre);


    // STEP 3: ASSERT
    assertNotNull(albumMetadataResults, "albumMetadataResults was null");
    assertFalse(albumMetadataResults.isEmpty(), "albumMetadataResults expected to be non-empty");
    AlbumMetadataDto albumMetadataResult = albumMetadataResults.get(0);

    String expected =
        "https://coverartarchive.org/release/005d4e48-e2be-410b-a56f-e6d64622f48b/front";
    String actual = albumMetadataResult.getCoverArtUrl();
    assertEquals(expected, actual, "coverArtUrl is incorrect");

    expected = "Chrysalis";
    actual = albumMetadataResult.getRecordLabel();
    assertEquals(expected, actual, "recordLabel is incorrect");

    expected = "1985";
    actual = albumMetadataResult.getReleaseDate();
    assertEquals(expected, actual, "releaseDate is incorrect");

    expected = "Rock";
    actual = albumMetadataResult.getGenre();
    assertEquals(expected, actual, "genre is incorrect");
  }
}
