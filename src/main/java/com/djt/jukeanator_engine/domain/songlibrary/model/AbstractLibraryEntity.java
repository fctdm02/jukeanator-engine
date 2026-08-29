package com.djt.jukeanator_engine.domain.songlibrary.model;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import com.djt.jukeanator_engine.domain.common.model.AbstractAssociativeEntity;

public abstract class AbstractLibraryEntity extends AbstractAssociativeEntity {
  private static final long serialVersionUID = 1L;

  private FolderEntity parentFolder;
  private String name;
  private Integer id;

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

  void setName(String name) {
    this.name = name;
  }

  public Integer getId() {
    return this.id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  // Database primary key -- a composite of the parent chain's own composite key plus this row's
  // own id (unique across the whole scan, see SongScanner). Distinct from getNaturalIdentity()
  // below, which stays a real filesystem path and must never be repurposed for this.
  @Override
  public Map<String, Integer> getPersistentIdentity() {

    Map<String, Integer> persistentIdentity = new LinkedHashMap<>(this.parentFolder.getPersistentIdentity());
    persistentIdentity.put("id", this.id);
    return persistentIdentity;
  }

  // Ordinary in-memory identity -- deliberately NOT AbstractAssociativeEntity's default (which
  // would use getPersistentIdentity(), walking the whole parent chain up through parentLocation).
  // That's expensive (allocates a Map at every level) and unsafe (parentLocation isn't wired until
  // after a repository load completes -- see RootFolderEntity's field javadoc), for what call
  // sites across the codebase (queue reordering, Set/Map membership, etc.) just need to be a cheap
  // "is this the same library item" check. Mirrors AbstractPersistentEntity's old id-with-natural-
  // identity-fallback pattern, just against id instead of persistentIdentity.
  @Override
  public int hashCode() {

    if (this.id != null) {
      return this.id.hashCode();
    }
    return getNaturalIdentity().hashCode();
  }

  @Override
  public boolean equals(Object that) {

    if (that == null) {
      return false;
    }
    if (that == this) {
      return true;
    }
    if (this.getClass() != that.getClass()) {
      return false;
    }

    AbstractLibraryEntity other = (AbstractLibraryEntity) that;
    if (this.id != null && other.id != null) {
      return this.id.equals(other.id);
    }
    return this.getNaturalIdentity().equals(other.getNaturalIdentity());
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
      sb.append(File.separatorChar);
    }    
    sb.append(this.name);
    return sb.toString();
  }
}
