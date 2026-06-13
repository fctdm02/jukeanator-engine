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

  private transient String coverArtPath;
  private transient Integer albumCount;
  private transient Integer songCount;
  private transient Integer numPlays;
  private transient List<AlbumFolderEntity> childAlbumList;
  private transient GenreFolderEntity genre; // Will be the most prevalent genre from all the child
                                             // albums
  private transient Year releaseDate;

  public ArtistFromSongEntity(RootFolderEntity root, String name) {
    super(root, name);
  }

  public boolean addAlbum(AlbumFolderEntity album) {
    return this.childAlbums.add(album);
  }

  @Override
  public List<AlbumFolderEntity> getAlbums() {

    if (childAlbumList == null) {

      childAlbumList = new ArrayList<>();
      for (FolderEntity childFolder : childAlbums) {

        if (childFolder instanceof AlbumFolderEntity) {
          childAlbumList.add((AlbumFolderEntity) childFolder);
        }
      }
    }
    return childAlbumList;
  }

  @Override
  public String getCoverArtPath() {

    if (coverArtPath == null) {

      String cap = "";

      // Return the first, most popular album that is not a compilation
      // If all are compilations, then return t
      int maxAlbumNumPlays = 0;
      for (AlbumFolderEntity album : childAlbums) {

        int albumNumPlays = album.getNumPlays();
        if (!album.isCompilation() && albumNumPlays > maxAlbumNumPlays) {

          maxAlbumNumPlays = album.getNumPlays();
          cap = album.getCoverArtPath();
        }
      }

      // As a failsafe, if coverArtPath is still empty, then
      // set it to be from the first album: NOTE: All artists
      // are going to have at least one album.
      if (cap.equals("")) {
        List<AlbumFolderEntity> list = new ArrayList<>();
        list.addAll(childAlbums);
        cap = list.get(0).getCoverArtPath();
      }
      coverArtPath = cap;
    }
    return coverArtPath;
  }

  public Integer getAlbumCount() {

    if (albumCount == null) {
      albumCount = Integer.valueOf(childAlbums.size());
    }
    return albumCount;
  }

  @Override
  public Integer getSongCount() {

    if (songCount == null) {

      int sc = 0;
      for (AlbumFolderEntity album : childAlbums) {

        if (!album.isCompilation()) {
          sc = sc + album.getChildSongs().size();
        } else {
          for (SongFileEntity song : album.getChildSongs()) {
            if (song.getArtistName().equals(getName())) {
              sc = sc + 1;
            }
          }
        }
      }
      songCount = Integer.valueOf(sc);
    }
    return songCount;
  }

  @Override
  public Integer getNumPlays() {

    if (numPlays == null) {

      int np = 0;
      for (AlbumFolderEntity album : childAlbums) {

        if (!album.isCompilation()) {
          np = np + album.getNumPlays();
        } else {
          for (SongFileEntity song : album.getChildSongs()) {
            if (song.getArtistName().equals(getName())) {
              np = np + song.getNumPlays();
            }
          }
        }
      }
      numPlays = Integer.valueOf(np);
    }
    return numPlays;
  }

  @Override
  public GenreFolderEntity getParentGenre() {

    if (genre == null) {

      Map<GenreFolderEntity, Integer> genreMap = new HashMap<>();

      for (AlbumFolderEntity album : childAlbums) {

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

    if (releaseDate == null) {
      Year newestReleaseDate = Year.parse("1950");
      for (AlbumFolderEntity album : childAlbums) {
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
