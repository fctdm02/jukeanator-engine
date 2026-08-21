package com.djt.jukeanator_engine.domain.songlibrary.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 */
public final class SongLibraryRepositoryFileSystemImpl implements SongLibraryRepository {

  private static final Logger log = LoggerFactory.getLogger(SongLibraryRepositoryFileSystemImpl.class);

  private static final String UNSAFE_FILENAME_CHARS_REGEX = "[ /\\\\:*?\"<>|]";

  private RootFolderEntity root;
  private SongLibraryObjectPersistor songLibraryObjectPersistor;
  private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
  private String basePath;
  private volatile String resolvedFilePath;

  public SongLibraryRepositoryFileSystemImpl(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    this.basePath = basePath;
    this.songLibraryObjectPersistor = new SongLibraryObjectPersistor();
  }

  public void setBasePath(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    this.basePath = basePath;
  }

  /**
   * Builds the filename the song library should be persisted under for a given location name --
   * spaces and other filesystem-unsafe characters are replaced with underscores. See
   * docs/multi-tenant-mode.md, Item 2, for why this filename is location-name-derived (and
   * reconciled at load time in {@link #loadAggregateRoot(String)}) rather than fixed.
   */
  static String sanitizeLocationNameForFilename(String locationName) {

    if (locationName == null || locationName.isBlank()) {
      return "SongLibrary";
    }

    return locationName.trim().replaceAll(UNSAFE_FILENAME_CHARS_REGEX, "_");
  }

  @Override
  public RootFolderEntity loadAggregateRoot(String locationName) throws EntityDoesNotExistException {

    String expectedFilePath = buildExpectedFilePath(locationName);

    if (Files.exists(Path.of(expectedFilePath))) {

      this.root = loadFromDisk(expectedFilePath, locationName);
      this.resolvedFilePath = expectedFilePath;
      return this.root;
    }

    Path chosenCandidate = chooseOtherOosFile();

    if (chosenCandidate == null) {
      throw new EntityDoesNotExistException(
          "Could not read song library from disk with locationName: "
              + locationName
              + " and filePath: "
              + expectedFilePath);
    }

    this.root = loadFromDisk(chosenCandidate.toString(), locationName);

    try {
      Files.move(chosenCandidate, Path.of(expectedFilePath));
      log.info("Renamed song library file from {} to {} to match the current location name.",
          chosenCandidate, expectedFilePath);
    } catch (IOException ioe) {
      throw new EntityDoesNotExistException(
          "Loaded song library from " + chosenCandidate
              + " but could not rename it to " + expectedFilePath
              + ": " + ioe.getMessage());
    }

    this.resolvedFilePath = expectedFilePath;
    return this.root;
  }

  private RootFolderEntity loadFromDisk(String filePath, String locationName)
      throws EntityDoesNotExistException {

    try {

      RootFolderEntity loaded = this.songLibraryObjectPersistor.loadSongLibraryFromDisk(filePath);
      loaded.initialize();
      return loaded;

    } catch (ClassNotFoundException | IOException e) {
      throw new EntityDoesNotExistException("Could not read song library from disk with locationName: "
          + locationName
          + " and filePath: "
          + filePath);
    }
  }

  /**
   * Looks for whatever {@code .oos} file(s) already exist under {@code basePath} -- the song
   * library repository is the only thing that writes {@code .oos} files, so under normal
   * operation there should be at most one. If more than one is found (e.g. a leftover from a
   * prior location name), the most recently modified one is chosen and the rest are logged as
   * ignored.
   */
  private Path chooseOtherOosFile() {

    List<Path> candidates = new ArrayList<>();

    try (DirectoryStream<Path> stream =
        Files.newDirectoryStream(Path.of(basePath), "*" + SongLibraryRepository.OOS_FILE_EXTENSION)) {

      for (Path candidate : stream) {
        if (Files.isRegularFile(candidate)) {
          candidates.add(candidate);
        }
      }

    } catch (IOException e) {
      return null;
    }

    if (candidates.isEmpty()) {
      return null;
    }

    if (candidates.size() == 1) {
      return candidates.get(0);
    }

    candidates.sort(Comparator.comparing(this::lastModifiedTimeOrEpoch).reversed());

    Path chosen = candidates.get(0);
    log.warn(
        "Found {} .oos files under {} but expected at most one; using the most recently "
            + "modified one ({}) and ignoring the rest: {}",
        candidates.size(), basePath, chosen, candidates.subList(1, candidates.size()));

    return chosen;
  }

  private FileTime lastModifiedTimeOrEpoch(Path path) {
    try {
      return Files.getLastModifiedTime(path);
    } catch (IOException e) {
      return FileTime.fromMillis(0L);
    }
  }

  @Override
  public void storeAggregateRoot(RootFolderEntity root) {

    String filePath = buildExpectedFilePath(root.getLocationName());

    try {
      this.root = root;
      this.songLibraryObjectPersistor.writeSongLibraryToDisk(root, filePath);
      this.resolvedFilePath = filePath;
    } catch (IOException ioe) {
      throw new SongLibraryServiceException("Could not write song library to disk with filePath: "
          + filePath);
    }
  }

  @Override
  public RootFolderEntity loadAggregateRoot(int persistentIdentity) throws EntityDoesNotExistException {

    throw new SongLibraryServiceException("This method is unsupported for the file system implementation");
  }

  @Override
  public void storeSongLibraryAsync() throws EntityDoesNotExistException {

    RootFolderEntity rootToPersist = this.root;

    persistenceExecutor.submit(() -> {

      try {
        storeAggregateRoot(rootToPersist);
      } catch (Exception e) {
        throw new SongLibraryServiceException("Could not asynchronously persist song library", e);
      }
    });
  }

  @Override
  public String getResolvedFilePath() {
    return this.resolvedFilePath;
  }

  private String buildExpectedFilePath(String locationName) {
    return basePath + File.separator + sanitizeLocationNameForFilename(locationName)
        + SongLibraryRepository.OOS_FILE_EXTENSION;
  }
  
  @Override
  public Integer updateNumPlaysForSong(
      RootFolderEntity root, 
      Integer locationId, 
      Integer albumId,
      Integer songId, 
      Integer numPlays) throws EntityDoesNotExistException {

    persistenceExecutor.submit(() -> {

      try {
        storeAggregateRoot(root);
      } catch (Exception e) {
        throw new SongLibraryServiceException("Could not asynchronously persist song library", e);
      }
    });
    
    return numPlays;    
  }  
}
