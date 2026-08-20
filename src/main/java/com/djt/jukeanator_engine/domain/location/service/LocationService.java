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
 * Provisions locations and receives their library syncs. {@link #registerLocation} and
 * {@link #listLocations} are usable in any {@code app.mode} — e.g. a standalone/slave instance can
 * register a location locally so its JSON persistence gives the operator a ready-made record to
 * turn into a SQL insert against the master's hosted database. The sync-receiving methods below are
 * meaningful only when this instance is the master actually being synced to by slaves.
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
  boolean verifyApiKey(Integer locationId, String apiKey);

  /**
   * Resolves the location whose bcrypt hash matches {@code apiKey}, without trusting any
   * caller-declared id — needed for the initial {@code /ws-slave} handshake, where a fresh slave's
   * own guess at its locationId (from {@code app.location-id}) may not match the id an admin
   * later assigns by hand when inserting its row into master's database (see
   * {@code StompLocationApiKeyChannelInterceptor}). Returns {@code null} if no location's hash
   * matches.
   */
  @PublicServiceMethod
  Integer resolveAndVerifyByApiKey(String apiKey);

  /**
   * Updates {@code lastSeenAt}; called whenever a slave successfully authenticates a request.
   * Called both from the HTTP library-sync path (where {@code LocationApiKeyAuthenticationFilter}
   * has already populated Spring Security's {@code SecurityContextHolder}) and from
   * {@code StompLocationApiKeyChannelInterceptor} on STOMP CONNECT — the latter has no such
   * context (a STOMP session's {@code simpUser} is a separate concept from Spring Security's
   * per-thread auth), so this must be exempt like {@link #verifyApiKey}.
   */
  @PublicServiceMethod
  void recordHeartbeat(Integer locationId);

  /**
   * Persists the slave's metadata snapshot under its own per-location storage root and returns
   * which albums' cover art master still needs (missing or stale hash).
   */
  LibrarySyncAckDto receiveLibraryMetadataSync(Integer locationId, String apiKey,
      LibrarySnapshotDto snapshot) throws LocationServiceException;

  /** Persists a single album's cover art image for {@code locationId}. */
  void receiveLibraryCoverArt(Integer locationId, String apiKey, Integer sourceAlbumId,
      byte[] imageBytes) throws LocationServiceException;

  /**
   * The most recently synced metadata snapshot for {@code locationId}, or {@code null} if the
   * location has never synced.
   */
  LibrarySnapshotDto getLibrarySnapshot(Integer locationId);

  /** Filesystem path to a previously-synced album's cover art, or {@code null} if not present. */
  java.nio.file.Path getCoverArtPath(Integer locationId, Integer sourceAlbumId);
}
