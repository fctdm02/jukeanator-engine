package com.djt.jukeanator_engine.domain.songlibrary.model;

import static java.util.Objects.requireNonNull;
import java.io.File;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

public abstract class AbstractLibraryEntity extends AbstractPersistentEntity {
  private static final long serialVersionUID = 1L;

  private FolderEntity parentFolder;
  private String name;

  public AbstractLibraryEntity() {}

  public AbstractLibraryEntity(FolderEntity parentFolder, String name) {
    super();
    requireNonNull(name, "name cannot be null");
    this.parentFolder = parentFolder;
    this.name = name;
  }

  public FolderEntity getParentFolder() {
    return this.parentFolder;
  }

  public void setParentFolder(FolderEntity folder) {
    this.parentFolder = folder;
  }

  public String getName() {
    return this.name;
  }

  public boolean existsOnFilesystem() {

    File file = new File(this.getNaturalIdentity());
    return file.exists();
  }

  @Override
  public String getNaturalIdentity() {

    StringBuilder sb = new StringBuilder();
    if (this.parentFolder != null) {
      sb.append(this.parentFolder.getNaturalIdentity());
    }
    sb.append(File.separatorChar);
    sb.append(this.name);
    return sb.toString();
  }
}
