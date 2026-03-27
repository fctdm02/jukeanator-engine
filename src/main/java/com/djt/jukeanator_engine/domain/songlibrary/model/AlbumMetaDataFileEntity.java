package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;

public class AlbumMetaDataFileEntity extends AbstractFileEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  public static final String CoverArtURL = "CoverArtURL";
  public static final String RecordLabel = "RecordLabel";
  public static final String ReleaseDate = "ReleaseDate";
  public static final String HasExplicit = "HasExplicit";

  private String coverArtUrl = "";
  private String recordLabel = "";
  private String releaseDate = "";
  private boolean hasExplicit = false;

  private transient boolean isLoaded = false;

  public AlbumMetaDataFileEntity() {}

  public AlbumMetaDataFileEntity(AlbumFolderEntity parentAlbum, String name) {
    super(parentAlbum, name);
  }

  public boolean isValid() {
    return getRecordLabel() != null && !getRecordLabel().isEmpty();
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

    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      String line;

      while ((line = reader.readLine()) != null) {
        if (line.startsWith("CoverArtURL=")) {
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
      throw new SongLibraryException("Could not read metadata: " + path, e);
    }

    isLoaded = true;
  }

  public void writeMetadataToFileSystem(Map<String, String> metadata) {

    // Populate fields safely
    this.coverArtUrl = safe(metadata.getOrDefault(CoverArtURL, ""));
    this.recordLabel = safe(metadata.getOrDefault(RecordLabel, "Unknown"));
    this.releaseDate = safe(metadata.getOrDefault(ReleaseDate, ""));
    this.hasExplicit = Boolean.parseBoolean(metadata.getOrDefault(HasExplicit, "false"));

    Path path = Path.of(getNaturalIdentity());

    try {
      // Ensure directory exists
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }

      try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {

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
      throw new SongLibraryException("Could not write metadata: " + path, e);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
