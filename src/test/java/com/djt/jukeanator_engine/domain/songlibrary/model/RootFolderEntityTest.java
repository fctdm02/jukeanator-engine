package com.djt.jukeanator_engine.domain.songlibrary.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

/**
 * Unit tests for {@link RootFolderEntity}.
 *
 * <p>These tests build a fully wired in-memory song library without touching the filesystem, using
 * the same persistent-identity assignment pattern as {@link
 * com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner}:
 *
 * <ul>
 *   <li>Albums are numbered sequentially in {@link RootFolderEntity#getAllAlbums()} order (which is
 *       sorted by {@code getNaturalIdentity()}, i.e. file path).
 *   <li>Genres and artists are numbered sequentially as they are first encountered while iterating
 *       over those albums.
 *   <li>A song's persistent identity is its 0-based position in its parent album's
 *       track-number-sorted song list.
 *   <li>The composite key used to look up a song is {@code albumId + "__" + songId}.
 * </ul>
 *
 * <p>Library structure assembled by {@link MockLibraryBuilder}:
 *
 * <pre>
 * Root "/test-root"
 * ├── Genre "Rock"   (genreId=0)
 * │   ├── Artist "Led Zeppelin"   (artistId=0)
 * │   │   ├── Album "Led Zeppelin IV"      (albumId=0)
 * │   │   │   ├── Track 1: "Led Zeppelin-01-Black Dog.mp3"          (songId=0)
 * │   │   │   └── Track 2: "Led Zeppelin-02-Rock and Roll.mp3"      (songId=1)
 * │   │   └── Album "Physical Graffiti"    (albumId=1)
 * │   │       ├── Track 1: "Led Zeppelin-01-Kashmir.mp3"             (songId=0)
 * │   │       └── Track 2: "Led Zeppelin-02-Houses of the Holy.mp3" (songId=1)
 * │   └── Artist "Compilations"
 * │       └── Album "Classic Rock Mix"     (albumId=2)   ← compilation
 * │           ├── Track 1: "Led Zeppelin-01-Whole Lotta Love.mp3"   (songId=0)  artistName="Led Zeppelin"
 * │           └── Track 2: "AC DC-02-Back in Black.mp3"             (songId=1)  artistName="AC DC"
 * └── Genre "Pop"    (genreId=1)
 *     └── Artist "Madonna"   (artistId=1)
 *         └── Album "Like a Virgin"        (albumId=3)
 *             ├── Track 1: "Madonna-01-Material Girl.mp3"           (songId=0)
 *             └── Track 2: "Madonna-02-Like a Virgin.mp3"           (songId=1)
 * </pre>
 *
 * <p>Album sort order (by path, which drives albumId assignment):
 *
 * <ol>
 *   <li>{@code /test-root/Pop/Madonna/Like a Virgin} → albumId=0
 *   <li>{@code /test-root/Rock/Compilations/Classic Rock Mix} → albumId=1
 *   <li>{@code /test-root/Rock/Led Zeppelin/Led Zeppelin IV} → albumId=2
 *   <li>{@code /test-root/Rock/Led Zeppelin/Physical Graffiti} → albumId=3
 * </ol>
 *
 * <p>Genre assignment order (first album encountered per genre):
 *
 * <ol>
 *   <li>Album 0 ("Like a Virgin") → genre "Pop" → genreId=0
 *   <li>Album 1 ("Classic Rock Mix") → genre "Rock" → genreId=1
 * </ol>
 *
 * <p>Artist assignment order (first album encountered per artist folder, "Compilations" excluded):
 *
 * <ol>
 *   <li>Album 0 ("Like a Virgin") → artist "Madonna" → artistId=0
 *   <li>Album 2 ("Led Zeppelin IV") → artist "Led Zeppelin" → artistId=1
 * </ol>
 */
public class RootFolderEntityTest extends AbstractSongLibraryEntityTest {

  // ─────────────────────────────────────────────────────────────────────────
  // getRootPath / setRootPath
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getRootPath_returnsRootPath() {
    assertEquals(ROOT_PATH, root.getRootPath());
  }

  @Test
  void setRootPath_updatesRootPath() {
    root.setRootPath("/new-root");
    assertEquals("/new-root", root.getRootPath());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getNaturalIdentity
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getNaturalIdentity_returnsRootPath() {
    assertEquals(ROOT_PATH, root.getNaturalIdentity());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getParentFolder
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getParentFolder_throwsIllegalStateException() {
    assertThrows(IllegalStateException.class, root::getParentFolder);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // addArtistFromSong / getArtistFromSong
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getArtistFromSong_returnsNull_whenNotAdded() {
    assertNull(root.getArtistFromSong("Unknown Artist"));
  }

  @Test
  void addArtistFromSong_thenGetArtistFromSong_returnsAdded() {

    // getArtistFromSong must be called first to initialise the internal map
    root.getArtistFromSong("Stray Cats");

    ArtistFromSongEntity artistFromSong = new ArtistFromSongEntity(root, "Stray Cats");
    artistFromSong.setPersistentIdentity(99);
    root.addArtistFromSong(artistFromSong);

    ArtistFromSongEntity retrieved = root.getArtistFromSong("Stray Cats");
    assertNotNull(retrieved);
    assertEquals("Stray Cats", retrieved.getName());
    assertEquals(Integer.valueOf(99), retrieved.getPersistentIdentity());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getAllAlbums
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getAllAlbums_returnsAllFourAlbums() {
    List<AlbumFolderEntity> albums = root.getAllAlbums();
    assertEquals(4, albums.size());
  }

  @Test
  void getAllAlbums_albumsAreSortedByNaturalIdentity() {
    List<AlbumFolderEntity> albums = root.getAllAlbums();
    // Path order: Pop/.../Like a Virgin < Rock/Compilations/... < Rock/Led Zeppelin/Led Zeppelin IV
    //             < Rock/Led Zeppelin/Physical Graffiti
    assertEquals("Like a Virgin", albums.get(0).getName());
    assertEquals("Classic Rock Mix", albums.get(1).getName());
    assertEquals("Led Zeppelin IV", albums.get(2).getName());
    assertEquals("Physical Graffiti", albums.get(3).getName());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getGenres / getGenreById
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getGenres_returnsBothGenres() {
    Collection<GenreFolderEntity> genres = root.getGenres();
    assertEquals(2, genres.size());
  }

  @Test
  void getGenres_containsRockAndPop() {
    Collection<GenreFolderEntity> genres = root.getGenres();
    List<String> names = genres.stream().map(GenreFolderEntity::getName).toList();
    assertTrue(names.contains("Rock"), "Rock genre expected");
    assertTrue(names.contains("Pop"), "Pop genre expected");
  }

  @Test
  void getGenreById_returnsGenre_whenFound() throws EntityDoesNotExistException {
    GenreFolderEntity pop = root.getGenreById(GENRE_ID_POP);
    assertNotNull(pop);
    assertEquals("Pop", pop.getName());
  }

  @Test
  void getGenreById_returnsRockGenre_whenFound() throws EntityDoesNotExistException {
    GenreFolderEntity rock = root.getGenreById(GENRE_ID_ROCK);
    assertNotNull(rock);
    assertEquals("Rock", rock.getName());
  }

  @Test
  void getGenreById_throwsEntityDoesNotExistException_whenNotFound() {
    assertThrows(EntityDoesNotExistException.class, () -> root.getGenreById(999));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getAlbumsForGenre
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getAlbumsForGenre_returnsAlbumsForPop() {
    Collection<AlbumFolderEntity> popAlbums = root.getAlbumsForGenre(GENRE_ID_POP);
    assertNotNull(popAlbums);
    assertEquals(1, popAlbums.size());
    assertEquals("Like a Virgin", popAlbums.iterator().next().getName());
  }

  @Test
  void getAlbumsForGenre_returnsThreeAlbumsForRock() {
    Collection<AlbumFolderEntity> rockAlbums = root.getAlbumsForGenre(GENRE_ID_ROCK);
    assertNotNull(rockAlbums);
    assertEquals(3, rockAlbums.size());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getArtists / getArtistByName
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getArtists_excludesCompilations() {
    Collection<ArtistFolderEntity> artists = root.getArtists();
    List<String> names = artists.stream().map(ArtistFolderEntity::getName).toList();
    assertFalse(names.contains("Compilations"), "Compilations should be excluded from getArtists()");
  }

  @Test
  void getArtists_containsMadonnaAndLedZeppelin() {
    Collection<ArtistFolderEntity> artists = root.getArtists();
    List<String> names = artists.stream().map(ArtistFolderEntity::getName).toList();
    assertTrue(names.contains("Madonna"), "Madonna expected in artists");
    assertTrue(names.contains("Led Zeppelin"), "Led Zeppelin expected in artists");
  }

  @Test
  void getArtistByName_returnsArtist_whenFound() throws EntityDoesNotExistException {
    ArtistFolderEntity madonna = root.getArtistByName("Madonna");
    assertNotNull(madonna);
    assertEquals("Madonna", madonna.getName());
  }

  @Test
  void getArtistByName_returnsLedZeppelin_whenFound() throws EntityDoesNotExistException {
    ArtistFolderEntity lz = root.getArtistByName("Led Zeppelin");
    assertNotNull(lz);
    assertEquals("Led Zeppelin", lz.getName());
  }

  @Test
  void getArtistByName_throwsEntityDoesNotExistException_whenNotFound() {
    assertThrows(EntityDoesNotExistException.class, () -> root.getArtistByName("Metallica"));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getAlbums / getAlbumById
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getAlbums_returnsAllFourAlbums() {
    Collection<AlbumFolderEntity> albums = root.getAlbums();
    assertEquals(4, albums.size());
  }

  @Test
  void getAlbumById_returnsLikeAVirgin_whenFound() throws EntityDoesNotExistException {
    AlbumFolderEntity album = root.getAlbumById(ALBUM_ID_LIKE_A_VIRGIN);
    assertNotNull(album);
    assertEquals("Like a Virgin", album.getName());
  }

  @Test
  void getAlbumById_returnsLedZeppelinIV_whenFound() throws EntityDoesNotExistException {
    AlbumFolderEntity album = root.getAlbumById(ALBUM_ID_LED_ZEPPELIN_IV);
    assertNotNull(album);
    assertEquals("Led Zeppelin IV", album.getName());
  }

  @Test
  void getAlbumById_returnsPhysicalGraffiti_whenFound() throws EntityDoesNotExistException {
    AlbumFolderEntity album = root.getAlbumById(ALBUM_ID_PHYSICAL_GRAFFITI);
    assertNotNull(album);
    assertEquals("Physical Graffiti", album.getName());
  }

  @Test
  void getAlbumById_throwsEntityDoesNotExistException_whenNotFound() {
    assertThrows(EntityDoesNotExistException.class, () -> root.getAlbumById(999));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getSongs
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getSongs_returnsAllEightSongs() {
    Collection<SongFileEntity> songs = root.getSongs();
    assertEquals(8, songs.size());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getSongById
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getSongById_returnsBlackDog_byAlbumAndSongId() throws EntityDoesNotExistException {
    SongFileEntity song = root.getSongById(ALBUM_ID_LED_ZEPPELIN_IV, 0);
    assertNotNull(song);
    assertEquals("Black Dog", song.getSongName());
    assertEquals("Led Zeppelin", song.getArtistName());
  }

  @Test
  void getSongById_returnsRockAndRoll_byAlbumAndSongId() throws EntityDoesNotExistException {
    SongFileEntity song = root.getSongById(ALBUM_ID_LED_ZEPPELIN_IV, 1);
    assertNotNull(song);
    assertEquals("Rock and Roll", song.getSongName());
  }

  @Test
  void getSongById_returnsMaterialGirl_byAlbumAndSongId() throws EntityDoesNotExistException {
    SongFileEntity song = root.getSongById(ALBUM_ID_LIKE_A_VIRGIN, 0);
    assertNotNull(song);
    assertEquals("Material Girl", song.getSongName());
  }

  @Test
  void getSongById_throwsEntityDoesNotExistException_whenAlbumNotFound() {
    assertThrows(EntityDoesNotExistException.class, () -> root.getSongById(999, 0));
  }

  @Test
  void getSongById_throwsEntityDoesNotExistException_whenSongNotFound() {
    assertThrows(EntityDoesNotExistException.class,
        () -> root.getSongById(ALBUM_ID_LED_ZEPPELIN_IV, 999));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getSongByPath
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void getSongByPath_returnsSong_whenFound() throws EntityDoesNotExistException {
    String path = songBlackDog.getNaturalIdentity();
    SongFileEntity found = root.getSongByPath(path);
    assertNotNull(found);
    assertEquals("Black Dog", found.getSongName());
  }

  @Test
  void getSongByPath_throwsEntityDoesNotExistException_whenNotFound() {
    assertThrows(EntityDoesNotExistException.class,
        () -> root.getSongByPath("/test-root/Rock/Led Zeppelin/Led Zeppelin IV/nonexistent.mp3"));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // resetSongStatistics
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void resetSongStatistics_setsAllNumPlaysToZero() throws EntityDoesNotExistException {

    // Set some plays
    songBlackDog.setNumPlays(10);
    songKashmir.setNumPlays(7);
    songMaterialGirl.setNumPlays(3);

    root.resetSongStatistics();

    assertEquals(Integer.valueOf(0), root.getSongById(ALBUM_ID_LED_ZEPPELIN_IV, 0).getNumPlays(),
        "Black Dog numPlays should be 0");
    assertEquals(Integer.valueOf(0), root.getSongById(ALBUM_ID_PHYSICAL_GRAFFITI, 0).getNumPlays(),
        "Kashmir numPlays should be 0");
    assertEquals(Integer.valueOf(0), root.getSongById(ALBUM_ID_LIKE_A_VIRGIN, 0).getNumPlays(),
        "Material Girl numPlays should be 0");
  }

  // ─────────────────────────────────────────────────────────────────────────
  // pruneNonAlbumContainingChildFolders
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void pruneNonAlbumContainingChildFolders_removesEmptyLeafFolders() throws Exception {

    // Build a small library that has an empty branch to prune
    RootFolderEntity pruneRoot = new RootFolderEntity("/prune-root");

    // Genre "Rock" → Artist "Empty" (leaf, no albums) — should be pruned along with "EmptyGenre"
    GenreFolderEntity emptyGenre = new GenreFolderEntity(pruneRoot, "EmptyGenre");
    pruneRoot.addChildFolder(emptyGenre);
    FolderEntity emptyArtist = new FolderEntity(emptyGenre, "Empty Artist");
    emptyGenre.addChildFolder(emptyArtist);

    // Genre "Pop" → Artist "Madonna" → Album "Like a Virgin" — should survive
    GenreFolderEntity popGenre = new GenreFolderEntity(pruneRoot, "Pop");
    pruneRoot.addChildFolder(popGenre);
    ArtistFolderEntity madonna = new ArtistFolderEntity(popGenre, "Madonna");
    popGenre.addChildFolder(madonna);
    AlbumFolderEntity likeAVirgin = buildAlbum(madonna, "Like a Virgin");
    buildSong(likeAVirgin, "Madonna-01-Material Girl.mp3", "Madonna", "Material Girl", 1, 0);

    Set<FolderEntity> pruned = pruneRoot.pruneNonAlbumContainingChildFolders();

    // "Empty Artist" should have been identified as the prune candidate
    assertFalse(pruned.isEmpty(), "Expected folders to be pruned");

    // The empty genre branch should no longer be a child of the root
    boolean emptyGenreStillPresent =
        pruneRoot.getChildFolders().stream().anyMatch(f -> f.getName().equals("EmptyGenre"));
    assertFalse(emptyGenreStillPresent, "EmptyGenre should have been pruned from root");

    // The Pop branch should survive
    boolean popStillPresent =
        pruneRoot.getChildFolders().stream().anyMatch(f -> f.getName().equals("Pop"));
    assertTrue(popStillPresent, "Pop genre should survive pruning");
  }

  @Test
  void pruneNonAlbumContainingChildFolders_doesNotPruneAlbumFolders() throws Exception {

    // Library that is fully populated — nothing to prune
    RootFolderEntity fullRoot = new RootFolderEntity("/full-root");
    GenreFolderEntity rock = new GenreFolderEntity(fullRoot, "Rock");
    fullRoot.addChildFolder(rock);
    ArtistFolderEntity lz = new ArtistFolderEntity(rock, "Led Zeppelin");
    rock.addChildFolder(lz);
    AlbumFolderEntity lzIV = buildAlbum(lz, "Led Zeppelin IV");
    buildSong(lzIV, "Led Zeppelin-01-Black Dog.mp3", "Led Zeppelin", "Black Dog", 1, 0);

    Set<FolderEntity> pruned = fullRoot.pruneNonAlbumContainingChildFolders();

    assertTrue(pruned.isEmpty(), "No folders should be pruned when all branches lead to albums");
    assertEquals(1, fullRoot.getChildFolders().size(), "Rock genre should still be present");
  }

}
