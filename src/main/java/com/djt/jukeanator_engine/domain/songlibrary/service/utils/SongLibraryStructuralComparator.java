package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFromSongEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

/**
 * Compares two {@link RootFolderEntity} trees for structural equivalence -- deliberately not via
 * {@code equals()}/{@code hashCode()}, which {@code AbstractLibraryEntity} implements id-only
 * (too weak a check to prove two independently-built trees hold the same data; see
 * {@code SongLibraryRepositoryEquivalenceTest}'s identical reasoning, which this class' shape
 * mirrors and is meant to eventually share code with).
 *
 * <p>Genres, folder-derived artists, albums, and songs are compared by every field that is
 * actually persisted to {@code song_library} (id included, since those ids round-trip through a
 * real column). Song-embedded artists ({@link ArtistFromSongEntity}) are compared by name and
 * associated album ids only, without id -- that id is a locally-derived value (never itself
 * written to {@code song_library}), and the derivation scheme differs depending on how the tree
 * was built (a real scan vs. a JPA reload), so it isn't a meaningful equivalence signal.
 *
 * @author tmyers
 */
public final class SongLibraryStructuralComparator {

  private SongLibraryStructuralComparator() {}

  public static List<String> findDifferences(RootFolderEntity expected, RootFolderEntity actual) {

    List<String> differences = new ArrayList<>();

    if (!Objects.equals(expected.getRootPath(), actual.getRootPath())) {
      differences.add("rootPath differs: expected=[" + expected.getRootPath() + "], actual=["
          + actual.getRootPath() + "]");
    }

    diff(differences, "genres", describeGenres(expected), describeGenres(actual));
    diff(differences, "artists", describeArtists(expected), describeArtists(actual));
    diff(differences, "albums", describeAlbums(expected), describeAlbums(actual));
    diff(differences, "songs", describeSongs(expected), describeSongs(actual));
    diff(differences, "song-embedded artists", describeArtistsFromSongs(expected),
        describeArtistsFromSongs(actual));

    return differences;
  }

  private static void diff(List<String> differences, String label, List<String> expected,
      List<String> actual) {

    if (!expected.equals(actual)) {
      differences.add(label + " differ:\n  expected=" + expected + "\n  actual=" + actual);
    }
  }

  private static List<String> describeGenres(RootFolderEntity root) {

    Set<GenreFolderEntity> genres = new HashSet<>();
    root.getAllGenres(genres);
    return genres.stream().map(g -> g.getId() + "|" + g.getName()).sorted().toList();
  }

  private static List<String> describeArtists(RootFolderEntity root) {

    Set<ArtistFolderEntity> artists = new HashSet<>();
    root.getAllArtists(artists);
    return artists.stream().map(a -> a.getId() + "|" + a.getName()).sorted().toList();
  }

  private static List<String> describeAlbums(RootFolderEntity root) {

    return root.getAllAlbums().stream()
        .map(a -> a.getId() + "|" + a.getName() + "|" + a.getParentGenre().getName() + "|"
            + a.getParentArtist().getName() + "|" + a.getMetaData().getGenre() + "|"
            + a.getMetaData().getCoverArtUrl() + "|" + a.getMetaData().getRecordLabel() + "|"
            + a.getMetaData().getReleaseDate() + "|" + a.getMetaData().hasExplicit())
        .sorted().toList();
  }

  private static List<String> describeSongs(RootFolderEntity root) {

    List<String> descriptions = new ArrayList<>();
    for (AlbumFolderEntity album : root.getAllAlbums()) {
      for (SongFileEntity song : album.getChildSongs()) {
        descriptions.add(song.getId() + "|" + album.getId() + "|" + song.getSongName() + "|"
            + song.getArtistName() + "|" + song.getTrackNumber() + "|" + song.getNumPlays());
      }
    }
    return descriptions.stream().sorted().toList();
  }

  private static List<String> describeArtistsFromSongs(RootFolderEntity root) {

    return root.getArtistsFromSongs().stream()
        .map(a -> a.getName() + "|" + a.getAlbums().stream().map(AlbumFolderEntity::getId)
            .sorted().toList())
        .sorted().toList();
  }
}
