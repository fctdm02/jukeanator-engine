package com.djt.jukeanator_engine.domain.songlibrary.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.djt.jukeanator_engine.domain.songlibrary.dto.LocationMetadataDto;

/** Unit tests for {@link LocationMetaDataFileEntity}. */
public class LocationMetaDataFileEntityTest {

  @Test
  void constructor_persistsDefaultsToFilesystem_whenFileDoesNotExist(@TempDir Path rootPath)
      throws Exception {

    RootFolderEntity root = new RootFolderEntity(rootPath.toString());

    Path metadataFile = rootPath.resolve(LocationMetaDataFileEntity.LOCATION_METADATA_FILENAME);
    assertTrue(Files.exists(metadataFile), "Metadata file should be created with defaults");

    LocationMetaDataFileEntity metadata = root.getMetadata();
    assertEquals(1, metadata.getLocationId());
    assertEquals("Rock On Third", metadata.getLocationName());

    List<String> lines = Files.readAllLines(metadataFile);
    assertTrue(lines.contains("LocationID=1"));
    assertTrue(lines.contains("LocationName=Rock On Third"));
  }

  @Test
  void constructor_readsExistingValues_whenFileAlreadyExists(@TempDir Path rootPath)
      throws Exception {

    Files.writeString(
        rootPath.resolve(LocationMetaDataFileEntity.LOCATION_METADATA_FILENAME),
        "LocationID=42\n"
            + "LocationName=Custom Venue\n"
            + "LogoName=CustomLogo.jpg\n"
            + "Latitude=1.5\n"
            + "Longitude=-2.5\n"
            + "IsGeoFenced=false\n");

    RootFolderEntity root = new RootFolderEntity(rootPath.toString());
    LocationMetaDataFileEntity metadata = root.getMetadata();

    assertEquals(42, metadata.getLocationId());
    assertEquals("Custom Venue", metadata.getLocationName());
    assertEquals("CustomLogo.jpg", metadata.getLogoName());
    assertEquals(1.5, metadata.getLatitude());
    assertEquals(-2.5, metadata.getLongitude());
    assertEquals(false, metadata.isGeoFenced());
  }

  @Test
  void metadataFile_isLocatedInRootPathDirectory(@TempDir Path rootPath) {

    RootFolderEntity root = new RootFolderEntity(rootPath.toString());
    LocationMetaDataFileEntity metadata = root.getMetadata();

    assertEquals(
        rootPath.resolve(LocationMetaDataFileEntity.LOCATION_METADATA_FILENAME).toString(),
        metadata.getNaturalIdentity());
  }

  @Test
  void writeMetadataToFileSystem_overwritesFileWithNewValues(@TempDir Path rootPath)
      throws Exception {

    RootFolderEntity root = new RootFolderEntity(rootPath.toString());
    LocationMetaDataFileEntity metadata = root.getMetadata();

    metadata.writeMetadataToFileSystem(
        new LocationMetadataDto(7, "New Name", "NewLogo.jpg", 10.0, 20.0, false));

    // A fresh instance reading from the same file should see the new values.
    LocationMetaDataFileEntity reloaded = new LocationMetaDataFileEntity(root);
    assertEquals(7, reloaded.getLocationId());
    assertEquals("New Name", reloaded.getLocationName());
  }
}
