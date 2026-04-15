package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumMetaDataFileEntity;

/**
 * @author tmyers
 */
public class JAudioTaggerClientTest {

	@BeforeAll
	public static void beforeAll() throws IOException {

		cleanup();
	}

	@AfterAll
	public static void afterAll() throws IOException {

		cleanup();
	}

	public static void cleanup() throws IOException {

		String coverArtPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/JAudioTaggerClientTest/cover.jpg";
		Path path = Path.of(coverArtPath);
		Files.deleteIfExists(path);

		String songPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/JAudioTaggerClientTest/Billy Idol-01-Rebel Yell.mp3";
		File songFile = new File(songPath);
		if (songFile.exists()) {
			
			Path source = Paths.get(songPath);		
	        Path target = Paths.get("src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/JAudioTaggerClientTest/01 Rebel Yell.mp3");                
			try {
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);	
			} catch (NoSuchFileException nsfe) {
			}				
		}
	}

	@Test
	public void getTags() throws IOException {

		// STEP 1: ARRANGE
		JAudioTaggerClient jAudioTaggerClient = new JAudioTaggerClient();
		String songFile = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/JAudioTaggerClientTest/01 Rebel Yell.mp3";

		
		// STEP 2: ACT
		Map<String, String> tags = jAudioTaggerClient.getTags(songFile);

		
		// STEP 3: ASSERT
		assertNotNull(tags, "tags was null");
		assertFalse(tags.isEmpty(), "tags expected to be non-empty");

		String expected = "Billy Idol";
		String actual = tags.get(JAudioTaggerClient.ARTIST_NAME);
		assertEquals(expected, actual, "artist is incorrect");

		expected = "Rebel Yell";
		actual = tags.get(JAudioTaggerClient.ALBUM_NAME);
		assertEquals(expected, actual, "album is incorrect");

		expected = "Rebel Yell";
		actual = tags.get(JAudioTaggerClient.SONG_NAME);
		assertEquals(expected, actual, "song is incorrect");

		expected = "1";
		actual = tags.get(JAudioTaggerClient.TRACK_NUMBER);
		assertEquals(expected, actual, "track number is incorrect");
	}

	@Test
	public void extractCoverArt() throws IOException {

		// STEP 1: ARRANGE
		JAudioTaggerClient jAudioTaggerClient = new JAudioTaggerClient();
		String songFile = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/JAudioTaggerClientTest/01 Rebel Yell.mp3";
		String coverArtPath = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/JAudioTaggerClientTest/cover.jpg";

		
		// STEP 2: ACT
		boolean result = jAudioTaggerClient.extractCoverArt(coverArtPath, songFile);

		
		// STEP 3: ASSERT
		assertTrue(result, "result expected to be true");
	}
	
	@Test
	public void renameSongFromTag() throws IOException {

		// STEP 1: ARRANGE
		JAudioTaggerClient jAudioTaggerClient = new JAudioTaggerClient();
		String songFile = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/JAudioTaggerClientTest/01 Rebel Yell.mp3";
		
		
		// STEP 2: ACT
		Path renamedSongFile = jAudioTaggerClient.renameSongFromTag(songFile);

		
		// STEP 3: ASSERT
		String expectedRenamedSongFile = "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/JAudioTaggerClientTest/Billy Idol-01-Rebel Yell.mp3";
		assertEquals(expectedRenamedSongFile.toString(), renamedSongFile.toString(), "renamedSongFile expected to be: " + expectedRenamedSongFile + ", but instead was: " + renamedSongFile);
		cleanup();
	}

	@Test
	public void renameSongFromTag_ONEOFF() throws IOException {

		// STEP 1: ARRANGE
	    DiscogsClientWrapper discogsClientWrapper = DiscogsClientWrapperTest.createDiscogsClientWrapper();
	    MusicBrainzClientWrapper musicBrainzClientWrapper = new MusicBrainzClientWrapper();
	    CoverArtDownloader coverArtDownloader = new CoverArtDownloader();
		
		JAudioTaggerClient jAudioTaggerClient = new JAudioTaggerClient();
		File parentDir = new File("/home/tmyers/Music/Spotify");
		File[] files = parentDir.listFiles();
		int size = files.length;
		for (int i=0; i < size; i++) {
			
			File file = files[i];
			if (file.getAbsolutePath().endsWith(".mp3")) {
				
				if (!jAudioTaggerClient.hasGenre(file.getAbsolutePath())) {
					
					String genre = null;
					Map<String, String> albumMetadataResults = new HashMap<>();

					Map<String, String> tags = jAudioTaggerClient.getTags(file.getAbsolutePath());
					String artist = tags.get(JAudioTaggerClient.ARTIST_NAME);
					String album = tags.get(JAudioTaggerClient.ALBUM_NAME);
					albumMetadataResults = musicBrainzClientWrapper.searchForAlbumMetadata(artist, album);
					genre = albumMetadataResults.get(AlbumMetaDataFileEntity.Genre);
					
					if ((genre == null || genre.isBlank()) && discogsClientWrapper.hasValidApiKey()) {

						tags = jAudioTaggerClient.getTags(file.getAbsolutePath());
						artist = tags.get(JAudioTaggerClient.ARTIST_NAME);
						album = tags.get(JAudioTaggerClient.ALBUM_NAME);
						albumMetadataResults = discogsClientWrapper.searchForAlbumMetadata(artist, album);
						genre = albumMetadataResults.get(AlbumMetaDataFileEntity.Genre);
					}					
					
					if (genre != null && !genre.isBlank()) {

					    jAudioTaggerClient.embedGenre(genre, file.getAbsolutePath());
					}					
				}
				
				if (!jAudioTaggerClient.hasCoverArt(file.getAbsolutePath())) {
					
					String coverArtUrl = null;
					Map<String, String> albumMetadataResults = new HashMap<>();

					Map<String, String> tags = jAudioTaggerClient.getTags(file.getAbsolutePath());
					String artist = tags.get(JAudioTaggerClient.ARTIST_NAME);
					String album = tags.get(JAudioTaggerClient.ALBUM_NAME);
					albumMetadataResults = musicBrainzClientWrapper.searchForAlbumMetadata(artist, album);
					coverArtUrl = albumMetadataResults.get(AlbumMetaDataFileEntity.CoverArtURL);
					
					if ((coverArtUrl == null || coverArtUrl.isBlank()) && discogsClientWrapper.hasValidApiKey()) {

						tags = jAudioTaggerClient.getTags(file.getAbsolutePath());
						artist = tags.get(JAudioTaggerClient.ARTIST_NAME);
						album = tags.get(JAudioTaggerClient.ALBUM_NAME);
						albumMetadataResults = discogsClientWrapper.searchForAlbumMetadata(artist, album);
						coverArtUrl = albumMetadataResults.get(AlbumMetaDataFileEntity.CoverArtURL);
					}					
					
					if (coverArtUrl != null && !coverArtUrl.isBlank()) {

					    String coverArtPath = "cover.jpg";
					    coverArtDownloader.downloadCoverArt(coverArtPath, coverArtUrl);
					    
					    jAudioTaggerClient.embedCoverArt(coverArtPath, file.getAbsolutePath());
					}
				}
				
				Path renamedFile = jAudioTaggerClient.renameSongFromTag(file.getAbsolutePath(), false);
				System.out.println(i + " of " + size + ": Renamed " + file.getAbsolutePath() + " to: " + renamedFile);				
			}
		}
	}
		
}