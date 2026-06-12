package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ArtistFromSongEntity extends ArtistFolderEntity {

  private static final long serialVersionUID = 1L;

  private final Set<AlbumFolderEntity> childAlbums = new TreeSet<AlbumFolderEntity>();

  private transient List<AlbumFolderEntity> albums;
  private transient GenreFolderEntity genre; // Will be the most prevalent genre from all the child
                                             // albums
  private transient Year releaseDate;

  public ArtistFromSongEntity(RootFolderEntity root, String name) {
    super(root, name);
  }

  public boolean addAlbum(AlbumFolderEntity album) {
    return this.childAlbums.add(album);
  }

  public List<AlbumFolderEntity> getAlbums() {

    if (albums == null) {

      albums = new ArrayList<>();
      for (FolderEntity childFolder : childAlbums) {

        if (childFolder instanceof AlbumFolderEntity) {
          albums.add((AlbumFolderEntity) childFolder);
        }
      }
    }
    return albums;
  }

  @Override
  public Integer getNumPlays() {

    if (albums == null) {
      getAlbums();
    }

    int numPlays = 0;
    String thisName = getName();
    for (AlbumFolderEntity album : albums) {
      for (SongFileEntity song : album.getChildSongs()) {

        if (song.getArtistName().equals(thisName)) {

          numPlays = numPlays + song.getNumPlays();
        }
      }
    }
    return Integer.valueOf(numPlays);
  }

  @Override
  public GenreFolderEntity getParentGenre() {

    if (genre == null) {

      if (albums == null) {
        getAlbums();
      }

      Map<GenreFolderEntity, Integer> genreMap = new HashMap<>();

      for (AlbumFolderEntity album : albums) {

        GenreFolderEntity albumGenre = album.getParentGenre();
        if (albumGenre == null) {
          continue;
        }

        genreMap.merge(albumGenre, 1, Integer::sum);
      }

      genre = genreMap.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
          .orElse(null);
    }

    return genre;
  }

  @Override
  public String getTitle() {

    return getName();
  }

  @Override
  public Year getReleaseDate() {

    if (albums == null) {
      getAlbums();
    }

    if (releaseDate == null) {
      Year newestReleaseDate = Year.parse("1950");
      for (AlbumFolderEntity album : getAlbums()) {
        Year year = album.getReleaseDate();
        if (year.isAfter(newestReleaseDate)) {
          newestReleaseDate = year;
        }
      }
      releaseDate = newestReleaseDate;
    }
    return releaseDate;
  }

  @Override
  public String getNaturalIdentity() {

    return getName();
  }
}
