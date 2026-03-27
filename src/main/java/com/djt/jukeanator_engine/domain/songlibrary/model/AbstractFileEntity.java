package com.djt.jukeanator_engine.domain.songlibrary.model;

public abstract class AbstractFileEntity extends AbstractLibraryEntity {
  private static final long serialVersionUID = 1L;
  
  public AbstractFileEntity() {}

  public AbstractFileEntity(FolderEntity parentFolder, String name) {
    super(parentFolder, name);
  } 
}