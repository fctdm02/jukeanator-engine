package com.djt.jukeanator_engine.domain.songlibrary.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * Flat JPA persistence row for the {@code song_library_folders} table -- deliberately a
 * standalone type, not a JPA annotation retrofit of {@code FolderEntity}/{@code RootFolderEntity}
 * etc. Those domain classes have {@code equals}/{@code hashCode}/{@code compareTo} rooted in a
 * filesystem-path-shaped {@code getNaturalIdentity()} (see {@code AbstractEntity#compareTo}) and
 * hold children in natural-ordering {@code TreeSet}s -- mapping that directly as a bidirectional,
 * self-referencing JPA entity graph would put Hibernate's collection hydration in the well-known
 * "natural order depends on a lazily-resolved association" failure mode. Instead {@link
 * SongLibraryRepositoryJpaImpl} reads/writes these flat rows via plain JPQL (the same shape {@code
 * UserRepositoryJpaImpl} already uses for {@code UserEntity}) and manually assembles/decomposes
 * the {@code RootFolderEntity} tree in application code.
 *
 * @author tmyers
 */
@Entity
@Table(name = "song_library_folders")
public class SongLibraryFolderJpaEntity {

  public enum FolderType {
    ROOT, GENRE, ARTIST, ALBUM
  }

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE,
      generator = AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE)
  @SequenceGenerator(name = AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE,
      sequenceName = AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE, allocationSize = 1)
  @Column(name = "persistent_identity")
  private Integer persistentIdentity;

  @Column(name = "location_id", nullable = false)
  private Integer locationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "folder_type", nullable = false, length = 20)
  private FolderType folderType;

  @Column(name = "parent_folder_id")
  private Integer parentFolderId;

  // The domain object's own persistentIdentity (a scan-local id assigned by SongScanner on the
  // owning slave) -- never merged/reused across locations. NULL for ROOT, which has no scan-local
  // id of its own. This is intentionally distinct from the persistentIdentity above, which is
  // this row's own surrogate database key, used only for parent_folder_id linkage.
  @Column(name = "source_id")
  private Integer sourceId;

  @Column(name = "name", nullable = false, length = 500)
  private String name;

  protected SongLibraryFolderJpaEntity() {} // for JPA

  public SongLibraryFolderJpaEntity(Integer locationId, FolderType folderType,
      Integer parentFolderId, Integer sourceId, String name) {
    this.locationId = locationId;
    this.folderType = folderType;
    this.parentFolderId = parentFolderId;
    this.sourceId = sourceId;
    this.name = name;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public FolderType getFolderType() {
    return folderType;
  }

  public Integer getParentFolderId() {
    return parentFolderId;
  }

  public Integer getSourceId() {
    return sourceId;
  }

  public String getName() {
    return name;
  }
}
