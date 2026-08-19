package com.djt.jukeanator_engine.domain.songlibrary.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.djt.jukeanator_engine.domain.songlibrary.dto.LocationMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;

public class LocationMetaDataFileEntity extends AbstractFileEntity implements Serializable {

  private static final long serialVersionUID = 1L;
  
  public static final String LOCATION_METADATA_FILENAME = "locationMetadata.txt";  

  private Integer locationId = 1;
  private String locationName = "Rock On Third";
  private String logoName = "RockOnThirdLogo.jpg";
  private Double latitude = 42.4883;
  private Double longitude = -83.143;
  private boolean isGeoFenced = true;
  
  private transient boolean isLoaded = false;

  public LocationMetaDataFileEntity(RootFolderEntity root) {
    super(root, LOCATION_METADATA_FILENAME);
    ensureLoaded();
  }

  public Integer getLocationId() {
    ensureLoaded();
    return locationId;
  }

  public void setLocationId(Integer locationId) {
    this.locationId = locationId;
  }

  public String getLocationName() {
    ensureLoaded();
    return locationName;
  }

  public void setLocationName(String locationName) {
    this.locationName = locationName;
  }

  public String getLogoName() {
    ensureLoaded();
    return logoName;
  }

  public void setLogoName(String logoName) {
    this.logoName = logoName;
  }

  public Double getLatitude() {
    ensureLoaded();
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    ensureLoaded();
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public boolean isGeoFenced() {
    ensureLoaded();
    return isGeoFenced;
  }

  public void setGeoFenced(boolean isGeoFenced) {
    this.isGeoFenced = isGeoFenced;
  }

  private void ensureLoaded() {
    if (!isLoaded) {
      readMetadataFromFileSystem();
    }
  }

  private void readMetadataFromFileSystem() {

    Path path = Path.of(getNaturalIdentity());

    if (!Files.exists(path)) {
      // No metadata file yet -- persist the defaults so the user has a file to edit.
      writeMetadataToFileSystem();
      return;
    }

    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      String line;

      while ((line = reader.readLine()) != null) {

        if (line.startsWith("LocationID=")) {
          locationId = Integer.parseInt(line.substring("LocationID=".length()).trim());
        } else if (line.startsWith("LocationName=")) {
          locationName = line.substring("LocationName=".length());
        } else if (line.startsWith("LogoName=")) {
          logoName = line.substring("LogoName=".length());
        } else if (line.startsWith("Latitude=")) {
          latitude = Double.parseDouble(line.substring("Latitude=".length()).trim());
        } else if (line.startsWith("Longitude=")) {
          longitude = Double.parseDouble(line.substring("Longitude=".length()).trim());          
        } else if (line.startsWith("IsGeoFenced=")) {
          isGeoFenced = Boolean.parseBoolean(line.substring("IsGeoFenced=".length()).trim());
        }
      }

    } catch (IOException e) {
      throw new SongLibraryServiceException("Could not read location metadata: " + path, e);
    }

    isLoaded = true;
  }

  public void writeMetadataToFileSystem() {

    LocationMetadataDto metadata = new LocationMetadataDto(
        locationId,
        locationName,
        logoName,
        latitude,
        longitude,
        isGeoFenced);

    writeMetadataToFileSystem(metadata);
  }

  public void writeMetadataToFileSystem(LocationMetadataDto metadata) {

    // Populate fields safely
    this.locationId = safe(metadata.getLocationId());
    this.locationName = safe(metadata.getLocationName());
    this.logoName = safe(metadata.getLogoName());
    this.latitude = safe(metadata.getLatitude());
    this.longitude = safe(metadata.getLongitude());
    this.isGeoFenced = metadata.isGeoFenced();

    Path path = Path.of(getNaturalIdentity());

    try {
      // Ensure directory exists
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }

      try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {

        writer.write("LocationID=" + locationId);
        writer.newLine();
        writer.write("LocationName=" + locationName);
        writer.newLine();
        writer.write("LogoName=" + logoName);
        writer.newLine();
        writer.write("Latitude=" + latitude);
        writer.newLine();
        writer.write("Longitude=" + longitude);
        writer.newLine();        
        writer.write("IsGeoFenced=" + isGeoFenced);
        writer.newLine();
      }

      isLoaded = true;

    } catch (IOException e) {
      throw new SongLibraryServiceException("Could not write location metadata: " + path, e);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private Integer safe(Integer value) {
    return value == null ? 1 : value;
  }
  
  private Double safe(Double value) {
    return value == null ? 0.0D : value;
  }  
}
