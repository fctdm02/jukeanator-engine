package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public class SongScannerTest {

  @Test
  public void scanFileSystemForSongs() throws IOException {

    // STEP 1: ARRANGE
    DiscogsClientWrapper discogsClientWrapper = DiscogsClientWrapperTest.createDiscogsClientWrapper();
    MusicBrainzClientWrapper musicBrainzClientWrapper = new MusicBrainzClientWrapper();
    JAudioTaggerClient jAudioTaggerClient = new JAudioTaggerClient();
    CoverArtDownloader coverArtDownloader = new CoverArtDownloader();
    boolean requiresMetadata = true;
    boolean useGenre = true;
    boolean useTopFolderForGenre = true;
    Set<String> acceptedSongFileExtensions = Set.of(".mp3");
    SongScanner songScanner = new SongScanner(
        discogsClientWrapper,
        musicBrainzClientWrapper,
        jAudioTaggerClient,
        coverArtDownloader,
        requiresMetadata, 
        useGenre, 
        useTopFolderForGenre,
        acceptedSongFileExtensions);
    String scanPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/SongScannerTest/RequireMetadataUseGenreTopFolder";
    


    // STEP 2: ACT
    RootFolderEntity root = songScanner.scanFileSystemForSongs(scanPath);


    // STEP 3: ASSERT
    assertNotNull(root, "root was null");
    
    List<AlbumFolderEntity> albums = root.getAllAlbums();
    assertNotNull(root, "albums was null");
    assertFalse(albums.isEmpty(), "albums expected to be non-empty");    
  }
}
