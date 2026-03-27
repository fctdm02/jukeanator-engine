package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.util.Set;

public class GenreFolderEntity extends FolderEntity {
  private static final long serialVersionUID = 1L;

  public GenreFolderEntity() {}

  public GenreFolderEntity(FolderEntity parentFolder, String name) {
    this(parentFolder, name, null);
  }
  
  public GenreFolderEntity(FolderEntity parentFolder, String name, Set<FolderEntity> childFolders) {
    super(parentFolder, name, childFolders);
  }    
}