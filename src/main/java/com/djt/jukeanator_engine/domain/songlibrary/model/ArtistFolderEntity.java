package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ArtistFolderEntity extends FolderEntity implements LibraryItem {
  private static final long serialVersionUID = 1L;

  private transient String coverArtPath;
  private transient Integer albumCount;
  private transient Integer songCount;
  private transient Integer numPlays;
  private transient List<AlbumFolderEntity> albums;
  private transient GenreFolderEntity parentGenre;
  private transient Year releaseDate;

  public ArtistFolderEntity(FolderEntity parentFolder, String name) {
    this(parentFolder, name, null);
  }

  public ArtistFolderEntity(FolderEntity parentFolder, String name,
      Set<FolderEntity> childFolders) {
    super(parentFolder, name, childFolders);
  }

  public String getCoverArtPath() {

    if (albums == null) {
      getAlbums();
    }

    if (coverArtPath == null) {

      String cap = "";

      // Return the first, most popular album that is not a compilation
      // If all are compilations, then return t
      int maxAlbumNumPlays = 0;
      for (AlbumFolderEntity album : albums) {

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
        cap = albums.get(0).getCoverArtPath();
      }
      coverArtPath = cap;
    }
    return coverArtPath;
  }

  public Integer getAlbumCount() {

    if (albums == null) {
      getAlbums();
    }

    if (albumCount == null) {
      albumCount = Integer.valueOf(albums.size());
    }
    return albumCount;
  }

  public Integer getSongCount() {

    if (albums == null) {
      getAlbums();
    }

    if (songCount == null) {

      int sc = 0;
      for (AlbumFolderEntity album : albums) {

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

    if (albums == null) {
      getAlbums();
    }

    if (numPlays == null) {

      int np = 0;
      for (AlbumFolderEntity album : albums) {

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

    if (parentGenre == null) {

      FolderEntity parentFolder = this.getParentFolder();
      while (parentFolder instanceof RootFolderEntity == false) {

        if (parentFolder instanceof GenreFolderEntity) {
          parentGenre = (GenreFolderEntity) parentFolder;
          break;
        } else {
          parentFolder = parentFolder.getParentFolder();
        }
      }
      if (parentGenre == null) {
        parentGenre = new GenreFolderEntity(parentFolder, "None");
      }
    }
    return parentGenre;
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
      for (AlbumFolderEntity album : albums) {
        Year year = album.getReleaseDate();
        if (year.isAfter(newestReleaseDate)) {
          newestReleaseDate = year;
        }
      }
      releaseDate = newestReleaseDate;
    }
    return releaseDate;
  }

  public List<AlbumFolderEntity> getAlbums() {

    if (albums == null) {

      albums = new ArrayList<>();
      for (FolderEntity childFolder : getChildFolders()) {

        if (childFolder instanceof AlbumFolderEntity) {
          albums.add((AlbumFolderEntity) childFolder);
        }
      }
    }
    return albums;
  }
}
