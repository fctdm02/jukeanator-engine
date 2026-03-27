package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.util.Set;

public class ArtistFolderEntity extends FolderEntity {
  private static final long serialVersionUID = 1L;

  public ArtistFolderEntity() {}

  public ArtistFolderEntity(FolderEntity parentFolder, String name) {
    this(parentFolder, name, null);
  }
  
  public ArtistFolderEntity(FolderEntity parentFolder, String name, Set<FolderEntity> childFolders) {
    super(parentFolder, name, childFolders);
  }    
}