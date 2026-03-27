package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.io.File;

public class AlbumCoverArtFileEntity extends AbstractFileEntity {
  private static final long serialVersionUID = 1L;
  
  public AlbumCoverArtFileEntity() {}

  public AlbumCoverArtFileEntity(AlbumFolderEntity parentAlbum, String name) {
    super(parentAlbum, name);
  }
  
  public boolean isValid() {
    
    File file = new File(this.getNaturalIdentity());
    if (file.exists()) {
      return true;
    }
    return false;    
  }
}