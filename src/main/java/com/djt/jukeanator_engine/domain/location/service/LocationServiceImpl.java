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
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
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
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumMetaDataFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.LocationMetaDataFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryJpaImpl;
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
  private final SongLibraryRepository songLibraryRepository;

  private LocationRootEntity locationRoot;

  public LocationServiceImpl(LocationRepository locationRepository,
      PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper, String storageRoot,
      ConnectedSlaveRegistry connectedSlaveRegistry, SongLibraryRepository songLibraryRepository) {

    requireNonNull(locationRepository, "locationRepository cannot be null");
    requireNonNull(passwordEncoder, "passwordEncoder cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");
    requireNonNull(objectMapper, "objectMapper cannot be null");
    requireNonNull(storageRoot, "storageRoot cannot be null");
    requireNonNull(connectedSlaveRegistry, "connectedSlaveRegistry cannot be null");
    requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");

    this.locationRepository = locationRepository;
    this.passwordEncoder = passwordEncoder;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    this.storageRoot = storageRoot;
    this.connectedSlaveRegistry = connectedSlaveRegistry;
    this.songLibraryRepository = songLibraryRepository;

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

    String apiKey = generateApiKey();

    // JPA-backed stores allocate from the shared persistent-identity sequence, so a
    // registration-assigned id can never later collide with one Hibernate generates itself;
    // nextPersistentIdentity() returns null for the filesystem-backed store, which has no such
    // sequence, so we fall back to count+1 there.
    Integer persistentIdentity = this.locationRepository.nextPersistentIdentity();
    if (persistentIdentity == null) {
      persistentIdentity = Integer.valueOf(this.locationRoot.getLocations().size() + 1);
    }

    LocationEntity location = new LocationEntity(persistentIdentity, request.name(),
        request.latitude(), request.longitude(), passwordEncoder.encode(apiKey));
    location.setStatus(LocationStatus.PROVISIONED);

    this.locationRoot.addLocation(location);
    this.locationRepository.storeAggregateRoot(this.locationRoot);

    eventPublisher.publishEvent(new LocationRegisteredEvent(persistentIdentity, request.name()));

    return new ProvisionedLocationDto(persistentIdentity, apiKey, request.name());
  }

  @Override
  public List<LocationSummaryDto> listLocations() {

    List<LocationSummaryDto> summaries = new ArrayList<>();
    for (LocationEntity location : this.locationRoot.getLocations()) {
      boolean online = connectedSlaveRegistry.isConnected(location.getPersistentIdentity());
      summaries.add(new LocationSummaryDto(location.getPersistentIdentity(), location.getName(),
          location.getLatitude(), location.getLongitude(), online));
    }
    return summaries;
  }

  @Override
  public boolean verifyApiKey(Integer locationId, String apiKey) {

    LocationEntity location = this.locationRoot.getLocationByIdNullIfNotExists(locationId);
    if (location == null || apiKey == null) {
      return false;
    }
    return passwordEncoder.matches(apiKey, location.getApiKeyHash());
  }

  @Override
  public Integer resolveAndVerifyByApiKey(String apiKey) {

    if (apiKey == null) {
      return null;
    }
    for (LocationEntity location : this.locationRoot.getLocations()) {
      if (passwordEncoder.matches(apiKey, location.getApiKeyHash())) {
        return location.getPersistentIdentity();
      }
    }
    return null;
  }

  @Override
  public void recordHeartbeat(Integer locationId) {

    LocationEntity location = this.locationRoot.getLocationByIdNullIfNotExists(locationId);
    if (location == null) {
      return;
    }
    location.setLastSeenAt(Instant.now());
    location.setStatus(LocationStatus.ACTIVE);
    this.locationRepository.storeAggregateRoot(this.locationRoot);
  }

  @Override
  public LibrarySyncAckDto receiveLibraryMetadataSync(Integer locationId, String apiKey,
      LibrarySnapshotDto snapshot) {

    requireValidLocation(locationId, apiKey);

    Map<Integer, String> previousCoverArtHashes = loadPreviousCoverArtHashes(locationId);

    // The JSON snapshot file remains the source of truth for cover-art-hash diffing (see
    // loadPreviousCoverArtHashes) regardless of repository-type -- coverArtHash has no analog in
    // the JPA schema (AlbumMetaDataFileEntity never tracked one), so this file is written
    // unconditionally. When song-library.repository-type=jpa, the same snapshot additionally
    // populates the JPA tables, which is what SongLibraryServiceImpl actually serves browse/search
    // reads from for this location.
    Path libraryFile = locationStorageRoot(locationId).resolve("library.json");
    try {
      Files.createDirectories(libraryFile.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(libraryFile.toFile(), snapshot);
    } catch (IOException ioe) {
      throw new LocationServiceException(
          "Could not write library snapshot for locationId: " + locationId, ioe);
    }

    if (songLibraryRepository instanceof SongLibraryRepositoryJpaImpl) {
      persistSnapshotToJpa(locationId, snapshot);
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

  /**
   * Builds a {@link RootFolderEntity} tree from a synced snapshot -- the same shape {@code
   * SongScanner} would build from real files -- and stores it via the JPA repository, so {@code
   * SongLibraryServiceImpl#getOrLoadRoot} transparently picks up this location the next time it's
   * browsed. Mirrors the field mapping the deleted {@code SongLibraryServiceLocationProxy} used to
   * do at read time, just performed once at sync time instead of on every request.
   *
   * <p>Every {@link AlbumMetaDataFileEntity}/{@link LocationMetaDataFileEntity} built here is
   * marked loaded via its setter rather than through {@code writeMetadataToFileSystem()} -- see
   * {@code SongLibraryRepositoryJpaImpl}'s class javadoc for why a synthetically-built root must
   * never trigger real disk I/O.
   */
  private void persistSnapshotToJpa(Integer locationId, LibrarySnapshotDto snapshot) {

    RootFolderEntity root = new RootFolderEntity("synced-location-" + locationId);
    LocationMetaDataFileEntity metadata = root.getMetadata();
    metadata.setLocationId(locationId);
    metadata.setLoaded(true);

    Map<Integer, GenreFolderEntity> genresBySourceId = new HashMap<>();
    Map<String, ArtistFolderEntity> artistsByGenreAndName = new HashMap<>();

    try {
      for (LibrarySnapshotAlbumDto albumDto : snapshot.albums()) {

        GenreFolderEntity genre = genresBySourceId.computeIfAbsent(albumDto.sourceGenreId(),
            id -> {
              GenreFolderEntity g = new GenreFolderEntity(root,
                  albumDto.genreName() != null ? albumDto.genreName() : "Unknown");
              try {
                root.addChildFolder(g);
              } catch (EntityAlreadyExistsException e) {
                throw new LocationServiceException("Duplicate genre folder for locationId: "
                    + locationId + ", genreName: " + albumDto.genreName(), e);
              }
              return g;
            });

        String artistKey = albumDto.sourceGenreId() + "|" + albumDto.artistName();
        ArtistFolderEntity artist = artistsByGenreAndName.computeIfAbsent(artistKey, key -> {
          ArtistFolderEntity a = new ArtistFolderEntity(genre,
              albumDto.artistName() != null ? albumDto.artistName() : "Unknown");
          try {
            genre.addChildFolder(a);
          } catch (EntityAlreadyExistsException e) {
            throw new LocationServiceException("Duplicate artist folder for locationId: "
                + locationId + ", artistName: " + albumDto.artistName(), e);
          }
          return a;
        });

        AlbumFolderEntity album = new AlbumFolderEntity(artist, albumDto.name());
        album.setPersistentIdentity(albumDto.sourceAlbumId());
        artist.addChildFolder(album);

        album.createCoverArtEntity();
        album.createMetadataEntity();
        AlbumMetaDataFileEntity albumMetadata = album.getMetaData();
        albumMetadata.setGenre(albumDto.genreName());
        albumMetadata.setRecordLabel(albumDto.recordLabel());
        albumMetadata.setReleaseDate(albumDto.releaseDate());
        albumMetadata.setHasExplicit(Boolean.TRUE.equals(albumDto.hasExplicit()));
        albumMetadata.setLoaded(true);

        for (var songDto : albumDto.songs()) {
          SongFileEntity song = new SongFileEntity(album, songDto.title());
          song.setPersistentIdentity(songDto.sourceSongId());
          song.setArtistName(albumDto.artistName());
          song.setSongName(songDto.title());
          song.setTrackNumber(songDto.trackNumber());
          song.setNumPlays(songDto.numPlays() != null ? songDto.numPlays() : Integer.valueOf(0));
          album.addChildSong(song);
        }
      }
    } catch (EntityAlreadyExistsException e) {
      throw new LocationServiceException(
          "Could not assemble synced library tree for locationId: " + locationId, e);
    }

    root.initialize();
    songLibraryRepository.storeAggregateRoot(root);
  }

  @Override
  public void receiveLibraryCoverArt(Integer locationId, String apiKey, Integer sourceAlbumId,
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
  public LibrarySnapshotDto getLibrarySnapshot(Integer locationId) {

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
  public Path getCoverArtPath(Integer locationId, Integer sourceAlbumId) {

    Path coverArtFile = locationStorageRoot(locationId).resolve("cover-art")
        .resolve(sourceAlbumId + ".jpg");
    return Files.exists(coverArtFile) ? coverArtFile : null;
  }

  private void requireValidLocation(Integer locationId, String apiKey) {

    if (!verifyApiKey(locationId, apiKey)) {
      throw new LocationServiceException("Invalid locationId/apiKey for locationId: " + locationId);
    }
  }

  private Path locationStorageRoot(Integer locationId) {
    return Path.of(this.storageRoot, String.valueOf(locationId));
  }

  private Map<Integer, String> loadPreviousCoverArtHashes(Integer locationId) {

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
