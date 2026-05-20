package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ArtistFolderEntity extends FolderEntity {
  private static final long serialVersionUID = 1L;
  
  private transient List<AlbumFolderEntity> albums;

  public ArtistFolderEntity() {}

  public ArtistFolderEntity(FolderEntity parentFolder, String name) {
    this(parentFolder, name, null);
  }
  
  public ArtistFolderEntity(FolderEntity parentFolder, String name, Set<FolderEntity> childFolders) {
    super(parentFolder, name, childFolders);
  }
  
  public List<AlbumFolderEntity> getAlbums() {
    
    if (albums == null) {
      
      albums = new ArrayList<>();
      for (FolderEntity childFolder : getChildFolders()) {
        
        if (childFolder instanceof AlbumFolderEntity) {
          albums.add((AlbumFolderEntity)childFolder);
        }
      }
    }
    return albums;
  }
  
  public Integer getSongPlays() {
    
    if (albums == null) {
      getAlbums();
    }
    
    int numPlays = 0;
    for (AlbumFolderEntity album : albums) {

      numPlays = numPlays + album.getNumPlays();
    }
    return Integer.valueOf(numPlays);
  }  
}