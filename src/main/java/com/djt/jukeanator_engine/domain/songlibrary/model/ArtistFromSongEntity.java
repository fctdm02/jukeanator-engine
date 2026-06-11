package com.djt.jukeanator_engine.domain.songlibrary.model;

import static java.util.Objects.requireNonNull;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

public final class ArtistFromSongEntity extends AbstractPersistentEntity implements LibraryItem {

  private static final long serialVersionUID = 1L;

  private final String name;
  private final Set<AlbumFolderEntity> childAlbums = new TreeSet<AlbumFolderEntity>();

  private transient List<AlbumFolderEntity> albums;
  private transient GenreFolderEntity genre; // Will be the most prevalent genre from all the child
                                             // albums
  private transient Year releaseDate;

  public ArtistFromSongEntity(String name, AlbumFolderEntity album) {
    super();
    requireNonNull(name, "name cannot be null");
    this.name = name;
    this.childAlbums.add(album);
  }

  public String getName() {
    return this.name;
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
    for (AlbumFolderEntity album : albums) {

      numPlays = numPlays + album.getNumPlays();
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

        genre = album.getParentGenre();
        if (!genreMap.containsKey(genre)) {
          genreMap.put(genre, Integer.valueOf(1));
        } else {
          Integer count = genreMap.get(genre);
          genreMap.put(genre, Integer.valueOf(count.intValue() + 1));
        }
      }

      // TODO: Iterate through the genre map and set/return the one with the highest count

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

    return this.name;
  }
}
