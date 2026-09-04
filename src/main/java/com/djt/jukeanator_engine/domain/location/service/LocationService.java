package com.djt.jukeanator_engine.domain.location.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySyncAckDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationPricingConfigDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationSummaryDto;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.location.dto.UpdateLocationInfoRequest;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;

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

  /**
   * Returns this standalone/slave instance's own location, creating a default one ("Rock On
   * Third") the first time this is called if none exists yet -- idempotent thereafter. Not
   * meaningful on master, which owns no location of its own.
   *
   * <p>{@code preferredPersistentIdentity} is only consulted the first time a location is
   * created: slave mode must pass its {@code app.location-id} (the id master already assigned via
   * {@link #registerLocation}, so the slave's local record and master's agree); standalone mode
   * (no master to agree with) passes {@code null} to auto-generate one the same way {@link
   * #registerLocation} does.
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   */
  @PublicServiceMethod
  LocationEntity getOrCreateOwnLocation(Integer preferredPersistentIdentity);

  /**
   * Re-keys this standalone/slave instance's own location to {@code confirmedLocationId}, if it
   * doesn't already match -- needed when master resolves this slave's identity by API key alone
   * during the {@code /ws-slave} handshake (see {@code StompLocationApiKeyChannelInterceptor}) and
   * the id it confirms differs from this slave's own local guess (e.g. an admin hand-assigned a
   * different id than {@code app.location-id} when provisioning it on master). A no-op if the ids
   * already match. Called by {@code SlaveConnectionManager}, which must also call {@code
   * SongLibraryService.reinitializeOwnLocation()} afterward so its own locationId-keyed cache
   * doesn't go stale under the pre-correction id.
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   */
  @PublicServiceMethod
  void reconcileOwnLocationId(Integer confirmedLocationId);

  /**
   * Updates this standalone/slave instance's own location's operator-editable info (name,
   * coordinates, logo, geofencing) and persists the change. Backs the Swing admin panel's "Edit
   * Location Info" dialog. Not meaningful on master, which owns no location of its own.
   */
  LocationEntity updateOwnLocationInfo(UpdateLocationInfoRequest request);

  /**
   * Returns the location with {@code locationId}, or {@code null} if none exists. Used by
   * {@code PricingService} on master to resolve a location's synced pricing config.
   */
  @PublicServiceMethod
  LocationEntity getLocationByIdNullIfNotExists(Integer locationId);

  /**
   * Master-only. Caches a slave's own credit-config bundle (pushed on every {@code /ws-slave}
   * (re)connect — see {@code SlaveConnectionManager}) on its {@link LocationEntity}, so master can
   * price that location's Web/Mobile UI without needing the slave's own application.yml. A no-op
   * if {@code locationId} does not (yet) correspond to a known location.
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   */
  @PublicServiceMethod
  void updatePricingConfig(Integer locationId, LocationPricingConfigDto pricingConfig);
}
