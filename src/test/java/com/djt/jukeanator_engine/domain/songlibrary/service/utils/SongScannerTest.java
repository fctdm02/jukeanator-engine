package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.FolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * @author tmyers
 */
public class SongScannerTest {

  private static final Set<String> MP3_ONLY = Set.of(".mp3");

  private DiscogsClientWrapper discogsClientWrapper;
  private MusicBrainzClientWrapper musicBrainzClientWrapper;
  private JAudioTaggerClient jAudioTaggerClient;
  private CoverArtDownloader coverArtDownloader;

  @BeforeEach
  public void setUp() {
    discogsClientWrapper = mock(DiscogsClientWrapper.class);
    musicBrainzClientWrapper = mock(MusicBrainzClientWrapper.class);
    jAudioTaggerClient = mock(JAudioTaggerClient.class);
    coverArtDownloader = mock(CoverArtDownloader.class);
  }

  private SongScanner newScanner(boolean requiresMetadata, boolean useGenre,
      boolean useTopFolderForGenre) {
    return new SongScanner(discogsClientWrapper, musicBrainzClientWrapper, jAudioTaggerClient,
        coverArtDownloader, requiresMetadata, useGenre, useTopFolderForGenre, MP3_ONLY);
  }

  private void writeFile(Path dir, String filename) throws IOException {
    Files.createDirectories(dir);
    Files.writeString(dir.resolve(filename), "content", StandardCharsets.UTF_8);
  }

  // =========================================================================================
  // CONSTRUCTOR VALIDATION
  // =========================================================================================

  @Test
  public void constructorRejectsNullDiscogsClientWrapper() {
    assertThrows(NullPointerException.class, () -> new SongScanner(null, musicBrainzClientWrapper,
        jAudioTaggerClient, coverArtDownloader, false, false, false, MP3_ONLY));
  }

  @Test
  public void constructorRejectsNullMusicBrainzClientWrapper() {
    assertThrows(NullPointerException.class, () -> new SongScanner(discogsClientWrapper, null,
        jAudioTaggerClient, coverArtDownloader, false, false, false, MP3_ONLY));
  }

  @Test
  public void constructorRejectsNullJAudioTaggerClient() {
    assertThrows(NullPointerException.class, () -> new SongScanner(discogsClientWrapper,
        musicBrainzClientWrapper, null, coverArtDownloader, false, false, false, MP3_ONLY));
  }

  @Test
  public void constructorRejectsNullCoverArtDownloader() {
    assertThrows(NullPointerException.class, () -> new SongScanner(discogsClientWrapper,
        musicBrainzClientWrapper, jAudioTaggerClient, null, false, false, false, MP3_ONLY));
  }

  @Test
  public void constructorRejectsNullAcceptedExtensions() {
    assertThrows(NullPointerException.class,
        () -> new SongScanner(discogsClientWrapper, musicBrainzClientWrapper, jAudioTaggerClient,
            coverArtDownloader, false, false, false, null));
  }

  @Test
  public void constructorRejectsEmptyAcceptedExtensions() {
    assertThrows(IllegalStateException.class,
        () -> new SongScanner(discogsClientWrapper, musicBrainzClientWrapper, jAudioTaggerClient,
            coverArtDownloader, false, false, false, Set.of()));
  }

  @Test
  public void getAcceptedSongFileExtensionsReturnsConfiguredSet() {
    SongScanner scanner = newScanner(false, false, false);
    assertEquals(MP3_ONLY, scanner.getAcceptedSongFileExtensions());
  }

  // =========================================================================================
  // SCANNING STRUCTURE
  // =========================================================================================

  @Test
  public void scanFileSystemForSongsBuildsArtistAlbumSongHierarchy(@TempDir Path root)
      throws IOException {

    writeFile(root.resolve("ArtistA/AlbumA"), "ArtistA-01-SongOne.mp3");
    writeFile(root.resolve("ArtistA/AlbumA"), "ArtistA-02-SongTwo.mp3");
    writeFile(root.resolve("ArtistB/AlbumB"), "ArtistB-01-SongThree.mp3");

    SongScanner scanner = newScanner(false, false, false);

    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    assertNotNull(result);
    List<AlbumFolderEntity> albums = result.getAllAlbums();
    assertEquals(2, albums.size());

    for (AlbumFolderEntity album : albums) {
      assertTrue(album.getParentFolder() instanceof ArtistFolderEntity);
      for (SongFileEntity song : album.getChildSongs()) {
        assertFalse(song.getArtistName().isEmpty());
        assertFalse(song.getSongName().isEmpty());
        assertNotNull(song.getTrackNumber());
      }
    }
  }

  @Test
  public void scanFileSystemForSongsStripsWhitespaceFromRootPath(@TempDir Path root)
      throws IOException {

    writeFile(root.resolve("ArtistA/AlbumA"), "ArtistA-01-SongOne.mp3");

    SongScanner scanner = newScanner(false, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs("  " + root.toString() + "  ");

    assertEquals(1, result.getAllAlbums().size());
  }

  @Test
  public void scanFileSystemForSongsSoundtracksAlbumHasNoArtistFolder(@TempDir Path root)
      throws IOException {

    writeFile(root.resolve("Soundtracks/The Wedding Singer"),
        "Stray Cats-01-Rock This Town.mp3");

    SongScanner scanner = newScanner(false, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    List<AlbumFolderEntity> albums = result.getAllAlbums();
    assertEquals(1, albums.size());

    AlbumFolderEntity album = albums.get(0);
    assertFalse(album.getParentFolder() instanceof ArtistFolderEntity);
    assertEquals("Compilations", album.getParentArtist().getName());
  }

  @Test
  public void scanFileSystemForSongsUsesTopFolderAsGenre(@TempDir Path root) throws IOException {

    writeFile(root.resolve("Rock/ArtistA/AlbumA"), "ArtistA-01-SongOne.mp3");
    writeFile(root.resolve("Rock/ArtistB/AlbumB"), "ArtistB-01-SongTwo.mp3");

    SongScanner scanner = newScanner(false, true, true);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    List<AlbumFolderEntity> albums = result.getAllAlbums();
    assertEquals(2, albums.size());

    GenreFolderEntity genre1 = albums.get(0).getParentGenre();
    GenreFolderEntity genre2 = albums.get(1).getParentGenre();

    assertNotNull(genre1);
    assertEquals("Rock", genre1.getName());
    assertSame(genre1, genre2, "both albums should share the same genre folder instance");
  }

  @Test
  public void scanFileSystemForSongsThrowsWhenUseTopFolderForGenreIsFalse(@TempDir Path root)
      throws IOException {

    writeFile(root.resolve("Rock/ArtistA/AlbumA"), "ArtistA-01-SongOne.mp3");

    SongScanner scanner = newScanner(false, true, false);

    SongLibraryServiceException ex = assertThrows(SongLibraryServiceException.class,
        () -> scanner.scanFileSystemForSongs(root.toString()));
    assertTrue(ex.getMessage().contains("not implemented"));
  }

  @Test
  public void scanFileSystemForSongsIgnoresEntireRootWhenMarkerPresent(@TempDir Path root)
      throws IOException {

    writeFile(root.resolve("ArtistA/AlbumA"), "ArtistA-01-SongOne.mp3");
    Files.writeString(root.resolve("ignore.me"), "");

    SongScanner scanner = newScanner(false, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    assertTrue(result.getAllAlbums().isEmpty());
  }

  @Test
  public void scanFileSystemForSongsIgnoresSubtreeWithMarker(@TempDir Path root)
      throws IOException {

    writeFile(root.resolve("ArtistA/AlbumA"), "ArtistA-01-SongOne.mp3");
    Files.writeString(root.resolve("ArtistA/ignore.me"), "");
    writeFile(root.resolve("ArtistB/AlbumB"), "ArtistB-01-SongTwo.mp3");

    SongScanner scanner = newScanner(false, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    List<AlbumFolderEntity> albums = result.getAllAlbums();
    assertEquals(1, albums.size());
    assertEquals("AlbumB", albums.get(0).getName());
  }

  @Test
  public void scanFileSystemForSongsSkipsHiddenFoldersAndFiles(@TempDir Path root)
      throws IOException {

    writeFile(root.resolve(".HiddenArtist/AlbumA"), "ArtistA-01-SongOne.mp3");
    Path visibleAlbum = root.resolve("ArtistB/AlbumB");
    writeFile(visibleAlbum, "ArtistB-01-SongTwo.mp3");
    Files.writeString(visibleAlbum.resolve(".DS_Store"), "junk");

    SongScanner scanner = newScanner(false, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    List<AlbumFolderEntity> albums = result.getAllAlbums();
    assertEquals(1, albums.size());
    assertEquals(1, albums.get(0).getChildSongs().size());
  }

  @Test
  public void scanFileSystemForSongsIgnoresFilesWithUnacceptedExtensionsAndPrunesEmptyFolders(
      @TempDir Path root) throws IOException {

    writeFile(root.resolve("ArtistA/AlbumA"), "ArtistA-01-SongOne.flac");

    SongScanner scanner = newScanner(false, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    assertTrue(result.getAllAlbums().isEmpty());
    assertTrue(result.getChildFolders().isEmpty(), "non-album folders should be pruned");
  }

  @Test
  public void scanFileSystemForSongsMatchesExtensionsCaseInsensitively(@TempDir Path root)
      throws IOException {

    writeFile(root.resolve("ArtistA/AlbumA"), "ArtistA-01-SongOne.MP3");

    SongScanner scanner = newScanner(false, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    assertEquals(1, result.getAllAlbums().size());
  }

  @Test
  public void scanFileSystemForSongsHandlesMultiDottedFilenamesAndFallsBackTrackNumber(
      @TempDir Path root) throws IOException {

    writeFile(root.resolve("ArtistA/AlbumA"), "Y.M.C.A..mp3");

    SongScanner scanner = newScanner(false, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    List<AlbumFolderEntity> albums = result.getAllAlbums();
    assertEquals(1, albums.size());

    SongFileEntity song = albums.get(0).getChildSongs().get(0);
    assertEquals("Unknown", song.getArtistName());
    assertEquals(Integer.valueOf(1), song.getTrackNumber());
  }

  @Test
  public void scanFileSystemForSongsStripsNonPrintableCharactersFromTagValues(@TempDir Path root)
      throws IOException {

    Path albumDir = root.resolve("ArtistA/AlbumA");
    writeFile(albumDir, "ArtistA-01-SongOne.mp3");
    Files.writeString(albumDir.resolve("cover.jpg"), "fake-cover");

    Map<String, String> tags = Map.of(JAudioTaggerClient.RECORD_LABEL, "Chrysalis",
        JAudioTaggerClient.RELEASE_DATE, "1984", JAudioTaggerClient.ARTIST_NAME, "Art\u0001ist",
        JAudioTaggerClient.SONG_NAME, "So\u200bng");
    when(jAudioTaggerClient.getTags(anyString())).thenReturn(tags);

    SongScanner scanner = newScanner(true, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    SongFileEntity song = result.getAllAlbums().get(0).getChildSongs().get(0);
    assertEquals("Artist", song.getArtistName());
    assertEquals("Song", song.getSongName());
  }

  // =========================================================================================
  // COVER ART / METADATA ENRICHMENT
  // =========================================================================================

  @Test
  public void scanFileSystemForSongsDownloadsCoverArtFromInternetWhenMissing(@TempDir Path root)
      throws IOException {

    writeFile(root.resolve("ArtistA/AlbumA"), "ArtistA-01-SongOne.mp3");

    AlbumMetadataDto metadataDto =
        new AlbumMetadataDto("ArtistA", "AlbumA", "", "", "", "http://example.com/cover.jpg", false);
    when(musicBrainzClientWrapper.searchForAlbumMetadata(anyString(), anyString(), anyBoolean(),
        anyInt())).thenReturn(List.of(metadataDto));

    SongScanner scanner = newScanner(false, false, false);
    scanner.scanFileSystemForSongs(root.toString());

    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(coverArtDownloader).downloadCoverArt(pathCaptor.capture(),
        eq("http://example.com/cover.jpg"));
    assertTrue(pathCaptor.getValue()
        .endsWith(File.separator + "AlbumA" + File.separator + "cover.jpg"));
  }

  @Test
  public void scanFileSystemForSongsWritesMetadataFromEmbeddedTagsWhenValid(@TempDir Path root)
      throws IOException {

    Path albumDir = root.resolve("ArtistA/AlbumA");
    writeFile(albumDir, "ArtistA-01-SongOne.mp3");
    Files.writeString(albumDir.resolve("cover.jpg"), "fake-cover");

    Map<String, String> tags = Map.of(JAudioTaggerClient.RECORD_LABEL, "Chrysalis",
        JAudioTaggerClient.RELEASE_DATE, "1984", JAudioTaggerClient.ARTIST_NAME, "Tag Artist",
        JAudioTaggerClient.SONG_NAME, "Tag Song");
    when(jAudioTaggerClient.getTags(anyString())).thenReturn(tags);

    SongScanner scanner = newScanner(true, false, false);
    RootFolderEntity result = scanner.scanFileSystemForSongs(root.toString());

    AlbumFolderEntity album = result.getAllAlbums().get(0);
    assertTrue(album.hasValidMetadata());
    assertEquals("Tag Artist", album.getChildSongs().get(0).getArtistName());
    assertEquals("Tag Song", album.getChildSongs().get(0).getSongName());

    verify(musicBrainzClientWrapper, never()).searchForAlbumMetadata(anyString(), anyString(),
        anyBoolean(), anyInt());

    List<String> metadataLines = Files.readAllLines(albumDir.resolve("metadata.txt"));
    assertTrue(metadataLines.contains("RecordLabel=Chrysalis"));
    assertTrue(metadataLines.contains("ReleaseDate=1984"));
  }

  @Test
  public void scanFileSystemForSongsFallsBackToInternetForMetadataWhenTagsInsufficient(
      @TempDir Path root) throws IOException {

    Path albumDir = root.resolve("ArtistA/AlbumA");
    writeFile(albumDir, "ArtistA-01-SongOne.mp3");
    Files.writeString(albumDir.resolve("cover.jpg"), "fake-cover");

    when(jAudioTaggerClient.getTags(anyString())).thenReturn(Map.of());

    AlbumMetadataDto internetMetadata =
        new AlbumMetadataDto("ArtistA", "AlbumA", "Atlantic", "1990", "Rock", "", false);
    when(musicBrainzClientWrapper.searchForAlbumMetadata(anyString(), anyString(), anyBoolean(),
        anyInt())).thenReturn(List.of(internetMetadata));

    SongScanner scanner = newScanner(true, false, false);
    scanner.scanFileSystemForSongs(root.toString());

    List<String> metadataLines = Files.readAllLines(albumDir.resolve("metadata.txt"));
    assertTrue(metadataLines.contains("RecordLabel=Atlantic"));
    assertTrue(metadataLines.contains("ReleaseDate=1990"));
  }

  // =========================================================================================
  // searchInternetForAlbumMetadata / downloadCoverArt
  // =========================================================================================

  @Test
  public void searchInternetForAlbumMetadataReturnsMusicBrainzResultsWithoutQueryingDiscogs() {

    AlbumMetadataDto dto = new AlbumMetadataDto("Artist", "Album", "Label", "2000", "Rock", "", false);
    when(musicBrainzClientWrapper.searchForAlbumMetadata("Artist", "Album", false, 3))
        .thenReturn(List.of(dto));

    SongScanner scanner = newScanner(false, false, false);
    List<AlbumMetadataDto> results = scanner.searchInternetForAlbumMetadata("Artist", "Album", 3);

    assertEquals(1, results.size());
    assertSame(dto, results.get(0));
    verify(discogsClientWrapper, never()).searchForAlbumMetadata(anyString(), anyString(), anyInt());
  }

  @Test
  public void searchInternetForAlbumMetadataFallsBackToDiscogsWhenMusicBrainzEmptyAndApiKeyValid() {

    when(musicBrainzClientWrapper.searchForAlbumMetadata("Artist", "Album", false, 3))
        .thenReturn(List.of());
    when(discogsClientWrapper.hasValidApiKey()).thenReturn(true);

    AlbumMetadataDto dto = new AlbumMetadataDto("Artist", "Album", "Label", "2000", "Rock", "", false);
    when(discogsClientWrapper.searchForAlbumMetadata("Artist", "Album", 3)).thenReturn(List.of(dto));

    SongScanner scanner = newScanner(false, false, false);
    List<AlbumMetadataDto> results = scanner.searchInternetForAlbumMetadata("Artist", "Album", 3);

    assertEquals(1, results.size());
    assertSame(dto, results.get(0));
  }

  @Test
  public void searchInternetForAlbumMetadataDoesNotQueryDiscogsWithoutValidApiKey() {

    when(musicBrainzClientWrapper.searchForAlbumMetadata("Artist", "Album", false, 3))
        .thenReturn(List.of());
    when(discogsClientWrapper.hasValidApiKey()).thenReturn(false);

    SongScanner scanner = newScanner(false, false, false);
    List<AlbumMetadataDto> results = scanner.searchInternetForAlbumMetadata("Artist", "Album", 3);

    assertTrue(results.isEmpty());
    verify(discogsClientWrapper, never()).searchForAlbumMetadata(anyString(), anyString(), anyInt());
  }

  @Test
  public void searchInternetForAlbumMetadataOverloadUsesParentFolderNameAndDefaultLimit() {

    FolderEntity parentFolder = new FolderEntity(new RootFolderEntity("root"), "SomeArtist");
    AlbumFolderEntity album = new AlbumFolderEntity(parentFolder, "SomeAlbum");

    when(musicBrainzClientWrapper.searchForAlbumMetadata("SomeArtist", "SomeAlbum", false, 3))
        .thenReturn(List.of());
    when(discogsClientWrapper.hasValidApiKey()).thenReturn(false);

    SongScanner scanner = newScanner(false, false, false);
    List<AlbumMetadataDto> results = scanner.searchInternetForAlbumMetadata(album);

    assertTrue(results.isEmpty());
    verify(musicBrainzClientWrapper).searchForAlbumMetadata("SomeArtist", "SomeAlbum", false, 3);
  }

  @Test
  public void downloadCoverArtDelegatesToCoverArtDownloader() {

    SongScanner scanner = newScanner(false, false, false);
    scanner.downloadCoverArt("/path/to/cover.jpg", "http://example.com/x.jpg");

    verify(coverArtDownloader).downloadCoverArt("/path/to/cover.jpg", "http://example.com/x.jpg");
  }

  // =========================================================================================
  // FULL FIXTURE INTEGRATION TEST
  // =========================================================================================

  @Test
  public void scanFileSystemForSongsAgainstRealFixtureFiles() throws IOException {

    // STEP 1: ARRANGE
    DiscogsClientWrapper discogsClientWrapper =
        DiscogsClientWrapperTest.createDiscogsClientWrapper();
    MusicBrainzClientWrapper musicBrainzClientWrapper = new MusicBrainzClientWrapper();
    JAudioTaggerClient jAudioTaggerClient = new JAudioTaggerClient();
    CoverArtDownloader coverArtDownloader = new CoverArtDownloader();
    boolean requiresMetadata = true;
    boolean useGenre = true;
    boolean useTopFolderForGenre = true;
    Set<String> acceptedSongFileExtensions = Set.of(".mp3");
    SongScanner songScanner = new SongScanner(discogsClientWrapper, musicBrainzClientWrapper,
        jAudioTaggerClient, coverArtDownloader, requiresMetadata, useGenre, useTopFolderForGenre,
        acceptedSongFileExtensions);
    String rootPath =
        "src/test/resources/com/djt/jukeanator_engine/domain/songlibrary/service/utils/SongScannerTest/RequireMetadataUseGenreTopFolder/";


    // STEP 2: ACT
    RootFolderEntity root = songScanner.scanFileSystemForSongs(rootPath);


    // STEP 3: ASSERT
    assertNotNull(root, "root was null");
    List<AlbumFolderEntity> albums = root.getAllAlbums();
    assertNotNull(root, "albums was null");
    assertFalse(albums.isEmpty(), "albums expected to be non-empty");

    for (AlbumFolderEntity album : albums) {
      for (SongFileEntity song : album.getChildSongs()) {

        System.out.println("artistName: " + song.getArtistName() + ", songName: "
            + song.getSongName() + ", trackNumber: " + song.getTrackNumber());
        assertFalse(song.getArtistName().isEmpty(), "song artist name expected to be non-empty");
        assertFalse(song.getSongName().isEmpty(), "song name expected to be non-empty");
        assertFalse(song.getTrackNumber().equals(Integer.valueOf(0)),
            "track number expected to be non-zero");
      }
    }
  }
}
