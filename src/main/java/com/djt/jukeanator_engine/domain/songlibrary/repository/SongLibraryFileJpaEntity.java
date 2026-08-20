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
 * Flat JPA persistence row for the {@code song_library_files} table -- see {@link
 * SongLibraryFolderJpaEntity}'s class javadoc for why this is a standalone type rather than a JPA
 * annotation retrofit of {@code AbstractFileEntity}/{@code SongFileEntity} etc.
 *
 * @author tmyers
 */
@Entity
@Table(name = "song_library_files")
public class SongLibraryFileJpaEntity {

  public enum FileType {
    SONG, ALBUM_COVER_ART, ALBUM_METADATA, LOCATION_METADATA
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

  @Column(name = "parent_folder_id", nullable = false)
  private Integer parentFolderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "file_type", nullable = false, length = 20)
  private FileType fileType;

  // The domain object's own persistentIdentity (scan-local song id). NULL for the three singleton
  // file types (cover art / album metadata / location metadata), which have no per-instance id.
  @Column(name = "source_id")
  private Integer sourceId;

  @Column(name = "name", nullable = false, length = 500)
  private String name;

  // SONG columns
  @Column(name = "artist_name", length = 500)
  private String artistName;

  @Column(name = "song_name", length = 500)
  private String songName;

  @Column(name = "track_number")
  private Integer trackNumber;

  @Column(name = "num_plays")
  private Integer numPlays;

  // ALBUM_METADATA columns
  @Column(name = "genre")
  private String genre;

  @Column(name = "cover_art_url", length = 1000)
  private String coverArtUrl;

  @Column(name = "record_label")
  private String recordLabel;

  @Column(name = "release_date", length = 20)
  private String releaseDate;

  @Column(name = "has_explicit")
  private Boolean hasExplicit;

  // LOCATION_METADATA columns
  @Column(name = "location_name")
  private String locationName;

  @Column(name = "logo_name")
  private String logoName;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @Column(name = "is_geo_fenced")
  private Boolean isGeoFenced;

  protected SongLibraryFileJpaEntity() {} // for JPA

  public SongLibraryFileJpaEntity(Integer locationId, Integer parentFolderId, FileType fileType,
      Integer sourceId, String name) {
    this.locationId = locationId;
    this.parentFolderId = parentFolderId;
    this.fileType = fileType;
    this.sourceId = sourceId;
    this.name = name;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public Integer getParentFolderId() {
    return parentFolderId;
  }

  public FileType getFileType() {
    return fileType;
  }

  public Integer getSourceId() {
    return sourceId;
  }

  public String getName() {
    return name;
  }

  public String getArtistName() {
    return artistName;
  }

  public void setArtistName(String artistName) {
    this.artistName = artistName;
  }

  public String getSongName() {
    return songName;
  }

  public void setSongName(String songName) {
    this.songName = songName;
  }

  public Integer getTrackNumber() {
    return trackNumber;
  }

  public void setTrackNumber(Integer trackNumber) {
    this.trackNumber = trackNumber;
  }

  public Integer getNumPlays() {
    return numPlays;
  }

  public void setNumPlays(Integer numPlays) {
    this.numPlays = numPlays;
  }

  public String getGenre() {
    return genre;
  }

  public void setGenre(String genre) {
    this.genre = genre;
  }

  public String getCoverArtUrl() {
    return coverArtUrl;
  }

  public void setCoverArtUrl(String coverArtUrl) {
    this.coverArtUrl = coverArtUrl;
  }

  public String getRecordLabel() {
    return recordLabel;
  }

  public void setRecordLabel(String recordLabel) {
    this.recordLabel = recordLabel;
  }

  public String getReleaseDate() {
    return releaseDate;
  }

  public void setReleaseDate(String releaseDate) {
    this.releaseDate = releaseDate;
  }

  public Boolean getHasExplicit() {
    return hasExplicit;
  }

  public void setHasExplicit(Boolean hasExplicit) {
    this.hasExplicit = hasExplicit;
  }

  public String getLocationName() {
    return locationName;
  }

  public void setLocationName(String locationName) {
    this.locationName = locationName;
  }

  public String getLogoName() {
    return logoName;
  }

  public void setLogoName(String logoName) {
    this.logoName = logoName;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public Boolean getIsGeoFenced() {
    return isGeoFenced;
  }

  public void setIsGeoFenced(Boolean isGeoFenced) {
    this.isGeoFenced = isGeoFenced;
  }
}
