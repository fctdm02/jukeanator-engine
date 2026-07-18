package com.djt.jukeanator_engine.domain.location.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySyncAckDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationSummaryDto;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;

/**
 * Master-only. Provisions locations and receives their library syncs.
 *
 * @author tmyers
 */
public interface LocationService {

  /** Generates a locationId + one-time-shown API secret; only the secret's bcrypt hash is kept. */
  ProvisionedLocationDto registerLocation(RegisterLocationRequest request);

  /** Public-facing location picker list. */
  List<LocationSummaryDto> listLocations();

  /**
   * True if {@code apiKey} matches the bcrypt hash on file for {@code locationId}. Called by
   * {@code LocationApiKeyAuthenticationFilter} itself, before any authentication exists yet — this
   * is the credential check that establishes it, exactly like {@code UserService.login()}.
   */
  @PublicServiceMethod
  boolean verifyApiKey(String locationId, String apiKey);

  /**
   * Updates {@code lastSeenAt}; called whenever a slave successfully authenticates a request.
   * Called both from the HTTP library-sync path (where {@code LocationApiKeyAuthenticationFilter}
   * has already populated Spring Security's {@code SecurityContextHolder}) and from
   * {@code StompLocationApiKeyChannelInterceptor} on STOMP CONNECT — the latter has no such
   * context (a STOMP session's {@code simpUser} is a separate concept from Spring Security's
   * per-thread auth), so this must be exempt like {@link #verifyApiKey}.
   */
  @PublicServiceMethod
  void recordHeartbeat(String locationId);

  /**
   * Persists the slave's metadata snapshot under its own per-location storage root and returns
   * which albums' cover art master still needs (missing or stale hash).
   */
  LibrarySyncAckDto receiveLibraryMetadataSync(String locationId, String apiKey,
      LibrarySnapshotDto snapshot) throws LocationServiceException;

  /** Persists a single album's cover art image for {@code locationId}. */
  void receiveLibraryCoverArt(String locationId, String apiKey, Integer sourceAlbumId,
      byte[] imageBytes) throws LocationServiceException;

  /**
   * The most recently synced metadata snapshot for {@code locationId}, or {@code null} if the
   * location has never synced. Used by {@code SongLibraryServiceLocationProxy} to serve
   * browse/search reads without a live round-trip to the slave.
   */
  LibrarySnapshotDto getLibrarySnapshot(String locationId);

  /** Filesystem path to a previously-synced album's cover art, or {@code null} if not present. */
  java.nio.file.Path getCoverArtPath(String locationId, Integer sourceAlbumId);
}
