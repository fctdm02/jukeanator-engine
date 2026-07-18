package com.djt.jukeanator_engine.domain.location.service;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotAlbumDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySyncAckDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationSummaryDto;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.location.event.LocationLibrarySyncedEvent;
import com.djt.jukeanator_engine.domain.location.event.LocationRegisteredEvent;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationStatus;
import com.djt.jukeanator_engine.domain.location.repository.LocationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author tmyers
 */
public class LocationServiceImpl implements LocationService {

  private static final Logger log = LoggerFactory.getLogger(LocationServiceImpl.class);

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final LocationRepository locationRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final String storageRoot;
  private final ConnectedSlaveRegistry connectedSlaveRegistry;

  private LocationRootEntity locationRoot;

  public LocationServiceImpl(LocationRepository locationRepository,
      PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper, String storageRoot,
      ConnectedSlaveRegistry connectedSlaveRegistry) {

    requireNonNull(locationRepository, "locationRepository cannot be null");
    requireNonNull(passwordEncoder, "passwordEncoder cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");
    requireNonNull(objectMapper, "objectMapper cannot be null");
    requireNonNull(storageRoot, "storageRoot cannot be null");
    requireNonNull(connectedSlaveRegistry, "connectedSlaveRegistry cannot be null");

    this.locationRepository = locationRepository;
    this.passwordEncoder = passwordEncoder;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    this.storageRoot = storageRoot;
    this.connectedSlaveRegistry = connectedSlaveRegistry;

    initialize();

    log.info("Using location root: " + this.locationRoot);
  }

  private void initialize() {

    try {
      this.locationRoot = this.locationRepository.loadAggregateRoot(this.storageRoot);
    } catch (EntityDoesNotExistException ednee) {
      log.info("No existing location list found at: " + this.storageRoot
          + " — starting with an empty one");
      this.locationRoot = new LocationRootEntity();
    }
  }

  @Override
  public ProvisionedLocationDto registerLocation(RegisterLocationRequest request) {

    String locationId = java.util.UUID.randomUUID().toString();
    String apiKey = generateApiKey();

    Integer persistentIdentity = Integer.valueOf(this.locationRoot.getLocations().size() + 1);
    LocationEntity location = new LocationEntity(persistentIdentity, locationId, request.name(),
        request.latitude(), request.longitude(), passwordEncoder.encode(apiKey));
    location.setStatus(LocationStatus.PROVISIONED);

    this.locationRoot.addLocation(location);
    this.locationRepository.storeAggregateRoot(this.locationRoot);

    eventPublisher.publishEvent(new LocationRegisteredEvent(locationId, request.name()));

    return new ProvisionedLocationDto(locationId, apiKey, request.name());
  }

  @Override
  public List<LocationSummaryDto> listLocations() {

    List<LocationSummaryDto> summaries = new ArrayList<>();
    for (LocationEntity location : this.locationRoot.getLocations()) {
      boolean online = connectedSlaveRegistry.isConnected(location.getLocationId());
      summaries.add(new LocationSummaryDto(location.getLocationId(), location.getName(),
          location.getLatitude(), location.getLongitude(), online));
    }
    return summaries;
  }

  @Override
  public boolean verifyApiKey(String locationId, String apiKey) {

    LocationEntity location = this.locationRoot.getLocationByIdNullIfNotExists(locationId);
    if (location == null || apiKey == null) {
      return false;
    }
    return passwordEncoder.matches(apiKey, location.getApiKeyHash());
  }

  @Override
  public void recordHeartbeat(String locationId) {

    LocationEntity location = this.locationRoot.getLocationByIdNullIfNotExists(locationId);
    if (location == null) {
      return;
    }
    location.setLastSeenAt(Instant.now());
    location.setStatus(LocationStatus.ACTIVE);
    this.locationRepository.storeAggregateRoot(this.locationRoot);
  }

  @Override
  public LibrarySyncAckDto receiveLibraryMetadataSync(String locationId, String apiKey,
      LibrarySnapshotDto snapshot) {

    requireValidLocation(locationId, apiKey);

    Map<Integer, String> previousCoverArtHashes = loadPreviousCoverArtHashes(locationId);

    Path libraryFile = locationStorageRoot(locationId).resolve("library.json");
    try {
      Files.createDirectories(libraryFile.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(libraryFile.toFile(), snapshot);
    } catch (IOException ioe) {
      throw new LocationServiceException(
          "Could not write library snapshot for locationId: " + locationId, ioe);
    }

    recordHeartbeat(locationId);
    LocationEntity location = this.locationRoot.getLocationByIdNullIfNotExists(locationId);
    location.setLibraryLastSyncedAt(Instant.now());
    this.locationRepository.storeAggregateRoot(this.locationRoot);

    List<Integer> needingCoverArt = new ArrayList<>();
    for (LibrarySnapshotAlbumDto album : snapshot.albums()) {
      String previousHash = previousCoverArtHashes.get(album.sourceAlbumId());
      if (album.coverArtHash() != null && !album.coverArtHash().equals(previousHash)) {
        needingCoverArt.add(album.sourceAlbumId());
      }
    }

    eventPublisher
        .publishEvent(new LocationLibrarySyncedEvent(locationId, snapshot.albums().size()));

    return new LibrarySyncAckDto(needingCoverArt);
  }

  @Override
  public void receiveLibraryCoverArt(String locationId, String apiKey, Integer sourceAlbumId,
      byte[] imageBytes) {

    requireValidLocation(locationId, apiKey);

    Path coverArtDir = locationStorageRoot(locationId).resolve("cover-art");
    Path coverArtFile = coverArtDir.resolve(sourceAlbumId + ".jpg");
    try {
      Files.createDirectories(coverArtDir);
      Files.write(coverArtFile, imageBytes);
    } catch (IOException ioe) {
      throw new LocationServiceException("Could not write cover art for locationId: " + locationId
          + ", sourceAlbumId: " + sourceAlbumId, ioe);
    }

    recordHeartbeat(locationId);
  }

  @Override
  public LibrarySnapshotDto getLibrarySnapshot(String locationId) {

    Path libraryFile = locationStorageRoot(locationId).resolve("library.json");
    if (!Files.exists(libraryFile)) {
      return null;
    }
    try {
      return objectMapper.readValue(libraryFile.toFile(), LibrarySnapshotDto.class);
    } catch (IOException ioe) {
      throw new LocationServiceException(
          "Could not read library snapshot for locationId: " + locationId, ioe);
    }
  }

  @Override
  public Path getCoverArtPath(String locationId, Integer sourceAlbumId) {

    Path coverArtFile = locationStorageRoot(locationId).resolve("cover-art")
        .resolve(sourceAlbumId + ".jpg");
    return Files.exists(coverArtFile) ? coverArtFile : null;
  }

  private void requireValidLocation(String locationId, String apiKey) {

    if (!verifyApiKey(locationId, apiKey)) {
      throw new LocationServiceException("Invalid locationId/apiKey for locationId: " + locationId);
    }
  }

  private Path locationStorageRoot(String locationId) {
    return Path.of(this.storageRoot, locationId);
  }

  private Map<Integer, String> loadPreviousCoverArtHashes(String locationId) {

    Path libraryFile = locationStorageRoot(locationId).resolve("library.json");
    if (!Files.exists(libraryFile)) {
      return new HashMap<>();
    }
    try {
      LibrarySnapshotDto previous = objectMapper.readValue(libraryFile.toFile(),
          LibrarySnapshotDto.class);
      Map<Integer, String> hashes = new HashMap<>();
      for (LibrarySnapshotAlbumDto album : previous.albums()) {
        hashes.put(album.sourceAlbumId(), album.coverArtHash());
      }
      return hashes;
    } catch (IOException ioe) {
      log.warn("Could not read previous library snapshot for locationId: " + locationId
          + " — treating all cover art as needing (re)upload", ioe);
      return new HashMap<>();
    }
  }

  private static String generateApiKey() {

    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
