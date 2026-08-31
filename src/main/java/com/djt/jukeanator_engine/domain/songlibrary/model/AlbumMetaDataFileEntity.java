package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;

public class AlbumMetaDataFileEntity extends AbstractFileEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  private String genre = "";
  private String coverArtUrl = "";
  private String recordLabel = "";
  private String releaseDate = "";
  private boolean hasExplicit = false;

  public boolean getHasExplicit() {
    return hasExplicit;
  }

  public void setHasExplicit(boolean hasExplicit) {
    this.hasExplicit = hasExplicit;
  }

  public boolean isLoaded() {
    return isLoaded;
  }

  public void setLoaded(boolean isLoaded) {
    this.isLoaded = isLoaded;
  }

  public void setGenre(String genre) {
    this.genre = genre;
  }

  public void setCoverArtUrl(String coverArtUrl) {
    this.coverArtUrl = coverArtUrl;
  }

  public void setRecordLabel(String recordLabel) {
    this.recordLabel = recordLabel;
  }

  public void setReleaseDate(String releaseDate) {
    this.releaseDate = releaseDate;
  }

  private transient boolean isLoaded = false;

  public AlbumMetaDataFileEntity(AlbumFolderEntity parentAlbum, String name) {
    super(parentAlbum, name);
  }

  public boolean isValid() {
    return getRecordLabel() != null && !getRecordLabel().isEmpty();
  }

  public String getGenre() {
    ensureLoaded();
    return genre;
  }

  public String getCoverArtUrl() {
    ensureLoaded();
    return coverArtUrl;
  }

  public String getRecordLabel() {
    ensureLoaded();
    return recordLabel;
  }

  public String getReleaseDate() {
    ensureLoaded();
    return releaseDate;
  }

  public boolean hasExplicit() {
    ensureLoaded();
    return hasExplicit;
  }

  private void ensureLoaded() {
    if (!isLoaded) {
      readMetadataFromFileSystem();
    }
  }

  private void readMetadataFromFileSystem() {

    Path path = Path.of(getNaturalIdentity());

    if (!Files.exists(path)) {
      isLoaded = true;
      return;
    }

    readKeyValueMetadata(path, StandardCharsets.UTF_8);

    isLoaded = true;
  }

  /**
   * If a legacy ".metadata" file (from the prior version of the application) is present
   * alongside metadata.txt, it is authoritative: its values are read and immediately written
   * back out to metadata.txt (and kept in-memory), overwriting whatever was there before.
   *
   * <p>Legacy files predate this application's switch to UTF-8 and were written using the
   * platform's default (typically Windows) charset, so they are read as ISO-8859-1 -- a
   * single-byte charset that maps every byte to a character and therefore never fails to
   * decode, unlike UTF-8.
   *
   * @return true if a legacy file was found and applied, false otherwise
   */
  public boolean applyLegacyMetadataIfPresent() {

    Path path = Path.of(getNaturalIdentity());
    Path parentDir = path.getParent();
    if (parentDir == null) {
      return false;
    }

    Path legacyPath = parentDir.resolve(AlbumFolderEntity.LEGACY_METADATA_FILENAME);
    if (!Files.exists(legacyPath)) {
      return false;
    }

    readKeyValueMetadata(legacyPath, StandardCharsets.ISO_8859_1);
    isLoaded = true;

    writeMetadataToFileSystem();

    return true;
  }

  private void readKeyValueMetadata(Path path, Charset charset) {

    try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
      String line;

      while ((line = reader.readLine()) != null) {

        if (line.startsWith("Genre=")) {
          genre = line.substring("Genre=".length());
        } else if (line.startsWith("CoverArtURL=")) {
          coverArtUrl = line.substring("CoverArtURL=".length());
        } else if (line.startsWith("RecordLabel=")) {
          recordLabel = line.substring("RecordLabel=".length());
        } else if (line.startsWith("ReleaseDate=")) {
          releaseDate = line.substring("ReleaseDate=".length());
        } else if (line.startsWith("HasExplicit=")) {
          hasExplicit = Boolean.parseBoolean(line.substring("HasExplicit=".length()));
        }
      }

    } catch (IOException e) {
      throw new SongLibraryServiceException("Could not read metadata: " + path, e);
    }
  }

  public void writeMetadataToFileSystem() {

    AlbumMetadataDto metadata = new AlbumMetadataDto("", "", recordLabel,
        releaseDate, genre, coverArtUrl, hasExplicit);

    writeMetadataToFileSystem(metadata);
  }

  public void writeMetadataToFileSystem(AlbumMetadataDto metadata) {

    // Populate fields safely
    this.genre = safe(metadata.getGenre());
    this.coverArtUrl = safe(metadata.getCoverArtUrl());
    this.recordLabel = safe(metadata.getRecordLabel());
    this.releaseDate = safe(metadata.getReleaseDate());
    this.hasExplicit = metadata.hasExplicit();

    Path path = Path.of(getNaturalIdentity());

    try {
      // Ensure directory exists
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }

      try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {

        writer.write("Genre=" + genre);
        writer.newLine();
        writer.write("CoverArtURL=" + coverArtUrl);
        writer.newLine();
        writer.write("ReleaseDate=" + releaseDate);
        writer.newLine();
        writer.write("RecordLabel=" + recordLabel);
        writer.newLine();
        writer.write("HasExplicit=" + hasExplicit);
        writer.newLine();
      }

      isLoaded = true;

    } catch (IOException e) {
      throw new SongLibraryServiceException("Could not write metadata: " + path, e);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
