package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumMetaDataFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * Unit tests for {@link SongLibraryStructuralComparator}. Builds two independently-constructed but
 * structurally-identical {@link RootFolderEntity} trees (a real album plus a compilation-style
 * album with two different song-embedded artist names), then mutates one field at a time in the
 * second tree to prove each category of difference is caught and reported.
 */
class SongLibraryStructuralComparatorTest {

  @Test
  void findDifferences_returnsEmpty_whenTreesAreIdentical() throws Exception {

    RootFolderEntity expected = buildRoot();
    RootFolderEntity actual = buildRoot();

    assertTrue(SongLibraryStructuralComparator.findDifferences(expected, actual).isEmpty());
  }

  @Test
  void findDifferences_reportsRootPathDifference() throws Exception {

    RootFolderEntity expected = buildRoot();
    RootFolderEntity actual = buildRoot();
    actual.setRootPath("/different/path");

    List<String> differences = SongLibraryStructuralComparator.findDifferences(expected, actual);

    assertFalse(differences.isEmpty());
    assertTrue(differences.get(0).contains("rootPath"));
  }

  @Test
  void findDifferences_reportsGenreDifference_whenGenreIdDiffers() throws Exception {

    RootFolderEntity expected = buildRoot();
    RootFolderEntity actual = buildRoot();
    // Mutate id, not name -- album descriptions embed the parent genre's *name*, so renaming would
    // cascade into an "albums differ" entry too and defeat this test's isolation.
    findGenre(actual, "Rock").setId(999);

    List<String> differences = SongLibraryStructuralComparator.findDifferences(expected, actual);

    assertSingleCategoryDiffers(differences, "genres");
  }

  @Test
  void findDifferences_reportsArtistDifference_whenFolderArtistIdDiffers() throws Exception {

    RootFolderEntity expected = buildRoot();
    RootFolderEntity actual = buildRoot();
    // Mutate id, not name -- same reasoning as the genre test above (album descriptions embed the
    // parent artist's *name*).
    findArtist(actual, "Artist One").setId(999);

    List<String> differences = SongLibraryStructuralComparator.findDifferences(expected, actual);

    assertSingleCategoryDiffers(differences, "artists");
  }

  @Test
  void findDifferences_reportsAlbumDifference_whenMetadataFieldDiffers() throws Exception {

    RootFolderEntity expected = buildRoot();
    RootFolderEntity actual = buildRoot();
    findAlbum(actual, "Album One").getMetaData().setCoverArtUrl("http://different-cover.jpg");

    List<String> differences = SongLibraryStructuralComparator.findDifferences(expected, actual);

    assertSingleCategoryDiffers(differences, "albums");
  }

  @Test
  void findDifferences_reportsAlbumDifference_whenMissingAlbum() throws Exception {

    RootFolderEntity expected = buildRoot();
    RootFolderEntity actual = buildRoot();
    AlbumFolderEntity albumOne = findAlbum(actual, "Album One");
    albumOne.getParentFolder().removeChild(albumOne);

    List<String> differences = SongLibraryStructuralComparator.findDifferences(expected, actual);

    assertTrue(differences.stream().anyMatch(d -> d.startsWith("albums differ")));
    assertTrue(differences.stream().anyMatch(d -> d.startsWith("songs differ")));
  }

  @Test
  void findDifferences_reportsSongDifference_whenNumPlaysDiffers() throws Exception {

    RootFolderEntity expected = buildRoot();
    RootFolderEntity actual = buildRoot();
    findSong(actual, "Song A").setNumPlays(999);

    List<String> differences = SongLibraryStructuralComparator.findDifferences(expected, actual);

    assertSingleCategoryDiffers(differences, "songs");
  }

  @Test
  void findDifferences_reportsSongDifference_whenTrackNumberDiffers() throws Exception {

    RootFolderEntity expected = buildRoot();
    RootFolderEntity actual = buildRoot();
    findSong(actual, "Song B").setTrackNumber(99);

    List<String> differences = SongLibraryStructuralComparator.findDifferences(expected, actual);

    assertSingleCategoryDiffers(differences, "songs");
  }

  @Test
  void findDifferences_reportsSongEmbeddedArtistDifference_whenEmbeddedArtistNameDiffers()
      throws Exception {

    // artistsFromSongs is derived once, inside initialize() -- mutating a song's artistName on an
    // already-initialized tree wouldn't retroactively update it (isEmpty() guards against
    // re-derivation), so the divergent name has to be baked in at construction time instead. This
    // also better matches how a real round-trip mismatch would actually appear: the reloaded tree
    // reads different data from the start, not mutated after the fact.
    RootFolderEntity expected = buildRoot("Guest Artist");
    RootFolderEntity actual = buildRoot("Some Other Artist");

    List<String> differences = SongLibraryStructuralComparator.findDifferences(expected, actual);

    // Changing a song's embedded artist name changes both the song's own description and which
    // song-embedded artist it's grouped under.
    assertTrue(differences.stream().anyMatch(d -> d.startsWith("songs differ")));
    assertTrue(differences.stream().anyMatch(d -> d.startsWith("song-embedded artists differ")));
  }

  // ── assertion helpers ───────────────────────────────────────────────────

  private void assertSingleCategoryDiffers(List<String> differences, String label) {
    assertEquals(1, differences.size(), "Expected exactly one differing category: " + differences);
    assertTrue(differences.get(0).startsWith(label + " differ"));
  }

  // ── lookup helpers ──────────────────────────────────────────────────────

  private GenreFolderEntity findGenre(RootFolderEntity root, String name) {
    return root.getGenres().stream().filter(g -> g.getName().equals(name)).findFirst()
        .orElseThrow();
  }

  private ArtistFolderEntity findArtist(RootFolderEntity root, String name) {
    // Walk the folder tree directly (matching SongLibraryStructuralComparator's own "artists"
    // category) rather than using RootFolderEntity.getArtists(), which -- once artistsFromSongs is
    // populated -- prefers the song-derived ArtistFromSongEntity over the real folder artist for
    // any name they share, and would resolve to the wrong object here.
    Set<ArtistFolderEntity> artists = new HashSet<>();
    root.getAllArtists(artists);
    return artists.stream().filter(a -> a.getName().equals(name)).findFirst().orElseThrow();
  }

  private AlbumFolderEntity findAlbum(RootFolderEntity root, String name) {
    return root.getAllAlbums().stream().filter(a -> a.getName().equals(name)).findFirst()
        .orElseThrow();
  }

  private SongFileEntity findSong(RootFolderEntity root, String name) {
    for (AlbumFolderEntity album : root.getAllAlbums()) {
      for (SongFileEntity song : album.getChildSongs()) {
        if (song.getSongName().equals(name)) {
          return song;
        }
      }
    }
    throw new IllegalStateException("Song not found: " + name);
  }

  // ── fixture ──────────────────────────────────────────────────────────────

  /**
   * Builds a fresh, fully-initialized tree: a genre/artist/album with two songs, plus a
   * compilation-style album (under a "Compilations" artist) whose two songs carry different
   * embedded artist names -- one overlapping the real artist above, mirroring how
   * {@code ArtistFromSongEntity} derivation behaves for compilation albums.
   */
  private RootFolderEntity buildRoot() throws EntityAlreadyExistsException {
    return buildRoot("Guest Artist");
  }

  private RootFolderEntity buildRoot(String compSong2ArtistName)
      throws EntityAlreadyExistsException {

    RootFolderEntity root = new RootFolderEntity("/fixture/comparator");
    root.setId(1);

    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(2);
    root.addChildFolder(genre);

    ArtistFolderEntity artist = new ArtistFolderEntity(genre, "Artist One");
    artist.setId(3);
    genre.addChildFolder(artist);

    AlbumFolderEntity albumOne = new AlbumFolderEntity(artist, "Album One");
    albumOne.setId(4);
    artist.addChildFolder(albumOne);
    albumOne.createCoverArtEntity();
    albumOne.createMetadataEntity();
    setMetadata(albumOne, "Rock", "http://cover-one.jpg", "Test Records", "2020", false);
    albumOne.addChildSong(newSong(albumOne, "Song A", "Artist One", 5, 1, 3));
    albumOne.addChildSong(newSong(albumOne, "Song B", "Artist One", 6, 2, 5));

    ArtistFolderEntity compilations = new ArtistFolderEntity(genre, "Compilations");
    compilations.setId(7);
    genre.addChildFolder(compilations);

    AlbumFolderEntity mixAlbum = new AlbumFolderEntity(compilations, "Mix Album");
    mixAlbum.setId(8);
    compilations.addChildFolder(mixAlbum);
    mixAlbum.createCoverArtEntity();
    mixAlbum.createMetadataEntity();
    setMetadata(mixAlbum, "Rock", "http://cover-mix.jpg", "Various", "2021", true);
    mixAlbum.addChildSong(newSong(mixAlbum, "Comp Song 1", "Artist One", 9, 1, 1));
    mixAlbum.addChildSong(newSong(mixAlbum, "Comp Song 2", compSong2ArtistName, 10, 2, 2));

    root.initialize();
    return root;
  }

  private void setMetadata(AlbumFolderEntity album, String genre, String coverArtUrl,
      String recordLabel, String releaseDate, boolean hasExplicit) {

    AlbumMetaDataFileEntity metaData = album.getMetaData();
    metaData.setGenre(genre);
    metaData.setCoverArtUrl(coverArtUrl);
    metaData.setRecordLabel(recordLabel);
    metaData.setReleaseDate(releaseDate);
    metaData.setHasExplicit(hasExplicit);
    metaData.setLoaded(true);
  }

  private SongFileEntity newSong(AlbumFolderEntity album, String songName, String artistName,
      int songId, int trackNumber, int numPlays) {

    SongFileEntity song = new SongFileEntity(album, songName + ".mp3");
    song.setId(songId);
    song.setArtistName(artistName);
    song.setSongName(songName);
    song.setTrackNumber(trackNumber);
    song.setNumPlays(numPlays);
    return song;
  }
}
