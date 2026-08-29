package com.djt.jukeanator_engine.domain.songlibrary.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Flat JPA persistence row for the single {@code song_library} table -- one table across the
 * whole {@code FolderEntity} (root/genre/artist/album) and {@code AbstractFileEntity} (song)
 * hierarchy, discriminated by {@link LibraryItemType}. Replaces the earlier split {@code
 * SongLibraryFolderJpaEntity}/{@code SongLibraryFileJpaEntity} pair -- see {@code
 * SongLibraryRepositoryJpaImpl}'s class javadoc for why this stays a standalone flat type rather
 * than a JPA annotation retrofit of the domain model itself.
 *
 * <p>{@code id} is <em>not</em> Hibernate-generated: it's assigned by {@code SongScanner}'s single
 * shared per-scan counter (see {@code AbstractLibraryEntity#getId}), unique across an entire
 * location's scan -- so the composite primary key {@code (locationId, id)} needs no separate
 * surrogate/source-id column pair the way the split tables used to.
 *
 * <p>Album metadata (genre/cover-art-url/record-label/release-date/has-explicit) lives directly
 * on the {@code ALBUM} row's own columns rather than a separate child row -- {@code
 * AlbumMetaDataFileEntity}/{@code AlbumCoverArtFileEntity} are synthesized in-memory objects, not
 * separately persisted (see {@code SongLibraryRepositoryJpaImpl}'s assemble/decompose logic).
 *
 * @author tmyers
 */
@Entity
@Table(name = "song_library")
@IdClass(SongLibraryJpaEntityId.class)
public class SongLibraryJpaEntity {

  public enum LibraryItemType {
    ROOT, FOLDER, GENRE, ARTIST, SONG_ARTIST, ALBUM, SONG
  }

  @Id
  @Column(name = "parent_location_id")
  private Integer locationId;

  @Id
  @Column(name = "id")
  private Integer id;

  @Column(name = "version", nullable = false)
  private Integer version = 1;

  @Column(name = "name", nullable = false, length = 500)
  private String name;

  @Column(name = "parent_folder_id")
  private Integer parentFolderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "class_discriminator", nullable = false, length = 20)
  private LibraryItemType classDiscriminator;

  // SONG columns
  @Column(name = "song_artist_name", length = 500)
  private String songArtistName;

  @Column(name = "song_name", length = 500)
  private String songName;

  @Column(name = "song_track_number")
  private Integer songTrackNumber;

  @Column(name = "song_num_plays")
  private Integer songNumPlays;

  // ALBUM metadata columns
  @Column(name = "album_genre")
  private String albumGenre;

  @Column(name = "album_cover_art_url", length = 1000)
  private String albumCoverArtUrl;

  @Column(name = "album_record_label")
  private String albumRecordLabel;

  @Column(name = "album_release_date", length = 20)
  private String albumReleaseDate;

  @Column(name = "album_has_explicit")
  private Boolean albumHasExplicit;

  protected SongLibraryJpaEntity() {} // for JPA

  public SongLibraryJpaEntity(Integer locationId, Integer id, String name,
      Integer parentFolderId, LibraryItemType classDiscriminator) {
    this.locationId = locationId;
    this.id = id;
    this.name = name;
    this.parentFolderId = parentFolderId;
    this.classDiscriminator = classDiscriminator;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public Integer getId() {
    return id;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public Integer getParentFolderId() {
    return parentFolderId;
  }

  public LibraryItemType getClassDiscriminator() {
    return classDiscriminator;
  }

  public String getSongArtistName() {
    return songArtistName;
  }

  public void setSongArtistName(String songArtistName) {
    this.songArtistName = songArtistName;
  }

  public String getSongName() {
    return songName;
  }

  public void setSongName(String songName) {
    this.songName = songName;
  }

  public Integer getSongTrackNumber() {
    return songTrackNumber;
  }

  public void setSongTrackNumber(Integer songTrackNumber) {
    this.songTrackNumber = songTrackNumber;
  }

  public Integer getSongNumPlays() {
    return songNumPlays;
  }

  public void setSongNumPlays(Integer songNumPlays) {
    this.songNumPlays = songNumPlays;
  }

  public String getAlbumGenre() {
    return albumGenre;
  }

  public void setAlbumGenre(String albumGenre) {
    this.albumGenre = albumGenre;
  }

  public String getAlbumCoverArtUrl() {
    return albumCoverArtUrl;
  }

  public void setAlbumCoverArtUrl(String albumCoverArtUrl) {
    this.albumCoverArtUrl = albumCoverArtUrl;
  }

  public String getAlbumRecordLabel() {
    return albumRecordLabel;
  }

  public void setAlbumRecordLabel(String albumRecordLabel) {
    this.albumRecordLabel = albumRecordLabel;
  }

  public String getAlbumReleaseDate() {
    return albumReleaseDate;
  }

  public void setAlbumReleaseDate(String albumReleaseDate) {
    this.albumReleaseDate = albumReleaseDate;
  }

  public Boolean getAlbumHasExplicit() {
    return albumHasExplicit;
  }

  public void setAlbumHasExplicit(Boolean albumHasExplicit) {
    this.albumHasExplicit = albumHasExplicit;
  }
}
