package com.djt.jukeanator_engine.domain.location.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
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

/**
 * Covers every method declared on {@link LocationService}, run against
 * {@link #locationServiceImpl}, a locally constructed instance with fully mocked dependencies
 * (fast, deterministic, no Spring context needed) -- unlike {@code UserServiceTest}, which exercises
 * a Spring-managed {@code UserService} bean end-to-end.
 *
 * @author tmyers
 */
public class LocationServiceTest {

  private static final String REGISTERED_LOCATION_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REGISTERED_API_KEY_HASH = "hashed-api-key";

  @TempDir
  private Path storageRoot;

  private LocationRepository locationRepository;
  private PasswordEncoder passwordEncoder;
  private ApplicationEventPublisher eventPublisher;
  private ConnectedSlaveRegistry connectedSlaveRegistry;
  private LocationRootEntity locationRoot;
  private LocationServiceImpl locationServiceImpl;

  @BeforeEach
  void setUp() throws EntityDoesNotExistException {

    locationRepository = mock(LocationRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    connectedSlaveRegistry = new ConnectedSlaveRegistry();

    locationRoot = new LocationRootEntity();
    LocationEntity registered = new LocationEntity(Integer.valueOf(1), REGISTERED_LOCATION_ID,
        "Rock On Third", 40.0, -105.0, REGISTERED_API_KEY_HASH);
    registered.setStatus(LocationStatus.PROVISIONED);
    locationRoot.addLocation(registered);

    when(locationRepository.loadAggregateRoot(anyString())).thenReturn(locationRoot);

    locationServiceImpl = new LocationServiceImpl(locationRepository, passwordEncoder,
        eventPublisher, ObjectMappers.create(), storageRoot.toString(), connectedSlaveRegistry);
  }

  private LocationEntity registeredLocation() {
    return locationRoot.getLocationByIdNullIfNotExists(REGISTERED_LOCATION_ID);
  }

  @Test
  void registerLocation_addsToRootAndReturnsPlaintextApiKeyOnce() {

    when(passwordEncoder.encode(anyString())).thenReturn("hashed-new-key");

    RegisterLocationRequest request = new RegisterLocationRequest("New Bar", 1.0, 2.0);
    ProvisionedLocationDto result = locationServiceImpl.registerLocation(request);

    assertNotNull(result.locationId());
    assertNotNull(result.apiKey());
    assertEquals("New Bar", result.name());

    LocationEntity stored = locationRoot.getLocationByIdNullIfNotExists(result.locationId());
    assertNotNull(stored);
    assertEquals("hashed-new-key", stored.getApiKeyHash());
    assertEquals(LocationStatus.PROVISIONED, stored.getStatus());

    verify(locationRepository).storeAggregateRoot(locationRoot);
    verify(eventPublisher).publishEvent(new LocationRegisteredEvent(result.locationId(), "New Bar"));
  }

  @Test
  void listLocations_reportsOnlineStatusFromConnectedSlaveRegistry() {

    connectedSlaveRegistry.markConnected(REGISTERED_LOCATION_ID, "session-1");

    List<LocationSummaryDto> summaries = locationServiceImpl.listLocations();

    assertEquals(1, summaries.size());
    LocationSummaryDto summary = summaries.get(0);
    assertEquals(REGISTERED_LOCATION_ID, summary.locationId());
    assertEquals("Rock On Third", summary.name());
    assertTrue(summary.online());
  }

  @Test
  void listLocations_reportsOfflineWhenNotConnected() {

    List<LocationSummaryDto> summaries = locationServiceImpl.listLocations();

    assertEquals(1, summaries.size());
    assertFalse(summaries.get(0).online());
  }

  @Test
  void verifyApiKey_trueWhenHashMatches() {

    when(passwordEncoder.matches("plaintext-key", REGISTERED_API_KEY_HASH)).thenReturn(true);

    assertTrue(locationServiceImpl.verifyApiKey(REGISTERED_LOCATION_ID, "plaintext-key"));
  }

  @Test
  void verifyApiKey_falseWhenHashDoesNotMatch() {

    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

    assertFalse(locationServiceImpl.verifyApiKey(REGISTERED_LOCATION_ID, "wrong-key"));
  }

  @Test
  void verifyApiKey_falseWhenLocationUnknown() {

    assertFalse(locationServiceImpl.verifyApiKey("unknown-location", "any-key"));
  }

  @Test
  void verifyApiKey_falseWhenApiKeyNull() {

    assertFalse(locationServiceImpl.verifyApiKey(REGISTERED_LOCATION_ID, null));
  }

  @Test
  void recordHeartbeat_updatesLastSeenAtAndStatus() {

    registeredLocation().setStatus(LocationStatus.PENDING);

    locationServiceImpl.recordHeartbeat(REGISTERED_LOCATION_ID);

    assertEquals(LocationStatus.ACTIVE, registeredLocation().getStatus());
    assertNotNull(registeredLocation().getLastSeenAt());
    verify(locationRepository).storeAggregateRoot(locationRoot);
  }

  @Test
  void recordHeartbeat_noopWhenLocationUnknown() {

    locationServiceImpl.recordHeartbeat("unknown-location");

    verify(locationRepository, never()).storeAggregateRoot(any());
  }

  private LibrarySnapshotDto emptySnapshot() {
    return new LibrarySnapshotDto(List.of(), List.of(), List.of());
  }

  private LibrarySnapshotDto snapshotWithOneAlbum(String coverArtHash) {
    LibrarySnapshotAlbumDto album = new LibrarySnapshotAlbumDto(1, "Album", 1, "Artist", 1,
        "Genre", coverArtHash, false, null, null, false, List.of());
    return new LibrarySnapshotDto(List.of(), List.of(), List.of(album));
  }

  @Test
  void receiveLibraryMetadataSync_rejectsInvalidApiKey() {

    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

    assertThrows(LocationServiceException.class, () -> locationServiceImpl
        .receiveLibraryMetadataSync(REGISTERED_LOCATION_ID, "bad-key", emptySnapshot()));

    verify(locationRepository, never()).storeAggregateRoot(any());
  }

  @Test
  void receiveLibraryMetadataSync_persistsSnapshotAndRecordsHeartbeat() {

    when(passwordEncoder.matches("good-key", REGISTERED_API_KEY_HASH)).thenReturn(true);

    LibrarySyncAckDto ack = locationServiceImpl.receiveLibraryMetadataSync(REGISTERED_LOCATION_ID,
        "good-key", snapshotWithOneAlbum("hash-1"));

    // First sync: no previous cover art on file, so the new hash needs (re)upload.
    assertEquals(List.of(1), ack.sourceAlbumIdsNeedingCoverArt());
    assertEquals(LocationStatus.ACTIVE, registeredLocation().getStatus());
    assertNotNull(registeredLocation().getLibraryLastSyncedAt());
    verify(eventPublisher).publishEvent(new LocationLibrarySyncedEvent(REGISTERED_LOCATION_ID, 1));

    LibrarySnapshotDto readBack = locationServiceImpl.getLibrarySnapshot(REGISTERED_LOCATION_ID);
    assertNotNull(readBack);
    assertEquals(1, readBack.albums().size());
  }

  @Test
  void receiveLibraryMetadataSync_secondSyncOnlyFlagsChangedCoverArtHashes() {

    when(passwordEncoder.matches("good-key", REGISTERED_API_KEY_HASH)).thenReturn(true);

    locationServiceImpl.receiveLibraryMetadataSync(REGISTERED_LOCATION_ID, "good-key",
        snapshotWithOneAlbum("same-hash"));

    LibrarySyncAckDto secondAck = locationServiceImpl.receiveLibraryMetadataSync(
        REGISTERED_LOCATION_ID, "good-key", snapshotWithOneAlbum("same-hash"));

    assertTrue(secondAck.sourceAlbumIdsNeedingCoverArt().isEmpty());
  }

  @Test
  void getLibrarySnapshot_nullWhenNeverSynced() {
    assertNull(locationServiceImpl.getLibrarySnapshot(REGISTERED_LOCATION_ID));
  }

  @Test
  void receiveLibraryCoverArt_rejectsInvalidApiKey() {

    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

    assertThrows(LocationServiceException.class, () -> locationServiceImpl
        .receiveLibraryCoverArt(REGISTERED_LOCATION_ID, "bad-key", 1, new byte[] {1, 2, 3}));
  }

  @Test
  void receiveLibraryCoverArt_writesFileAndRecordsHeartbeat() {

    when(passwordEncoder.matches("good-key", REGISTERED_API_KEY_HASH)).thenReturn(true);

    byte[] imageBytes = new byte[] {1, 2, 3, 4};
    locationServiceImpl.receiveLibraryCoverArt(REGISTERED_LOCATION_ID, "good-key", 1, imageBytes);

    Path coverArtPath = locationServiceImpl.getCoverArtPath(REGISTERED_LOCATION_ID, 1);
    assertNotNull(coverArtPath);
    assertEquals(LocationStatus.ACTIVE, registeredLocation().getStatus());
    verify(locationRepository, times(1)).storeAggregateRoot(locationRoot);
  }

  @Test
  void getCoverArtPath_nullWhenNeverUploaded() {
    assertNull(locationServiceImpl.getCoverArtPath(REGISTERED_LOCATION_ID, 999));
  }
}
