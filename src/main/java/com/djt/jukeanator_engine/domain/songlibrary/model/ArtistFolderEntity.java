package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ArtistFolderEntity extends FolderEntity implements LibraryItem {
  private static final long serialVersionUID = 1L;

  private transient List<AlbumFolderEntity> albums;
  private transient GenreFolderEntity parentGenre;
  private transient Year releaseDate;

  public ArtistFolderEntity() {}

  public ArtistFolderEntity(FolderEntity parentFolder, String name) {
    this(parentFolder, name, null);
  }

  public ArtistFolderEntity(FolderEntity parentFolder, String name,
      Set<FolderEntity> childFolders) {
    super(parentFolder, name, childFolders);
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
      for (AlbumFolderEntity album: getAlbums()) {
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
