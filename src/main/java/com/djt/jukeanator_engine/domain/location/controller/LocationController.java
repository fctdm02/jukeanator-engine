package com.djt.jukeanator_engine.domain.location.controller;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySyncAckDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationSummaryDto;
import com.djt.jukeanator_engine.domain.location.dto.ProvisionedLocationDto;
import com.djt.jukeanator_engine.domain.location.dto.RegisterLocationRequest;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.user.dto.CreditTransactionDto;
import com.djt.jukeanator_engine.domain.user.service.UserService;

/**
 * Master-only. Provisioning + the public location picker + slave library-sync intake.
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations")
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class LocationController {

  public static final String LOCATION_ID_HEADER = "location-id";
  public static final String LOCATION_API_KEY_HEADER = "location-api-key";

  private final LocationService locationService;
  private final UserService userService;

  public LocationController(LocationService locationService, UserService userService) {

    requireNonNull(locationService, "locationService cannot be null");
    requireNonNull(userService, "userService cannot be null");
    this.locationService = locationService;
    this.userService = userService;
  }

  @PostMapping
  public ResponseEntity<ProvisionedLocationDto> registerLocation(
      @RequestBody RegisterLocationRequest request) {

    return ResponseEntity.ok(locationService.registerLocation(request));
  }

  @GetMapping
  public ResponseEntity<List<LocationSummaryDto>> listLocations() {

    return ResponseEntity.ok(locationService.listLocations());
  }

  @PostMapping("/{locationId}/library-sync/metadata")
  public ResponseEntity<LibrarySyncAckDto> syncLibraryMetadata(
      @PathVariable String locationId,
      @RequestHeader(LOCATION_API_KEY_HEADER) String apiKey,
      @RequestBody LibrarySnapshotDto snapshot) {

    return ResponseEntity
        .ok(locationService.receiveLibraryMetadataSync(locationId, apiKey, snapshot));
  }

  @PostMapping("/{locationId}/library-sync/cover-art/{sourceAlbumId}")
  public ResponseEntity<Void> syncCoverArt(
      @PathVariable String locationId,
      @PathVariable Integer sourceAlbumId,
      @RequestHeader(LOCATION_API_KEY_HEADER) String apiKey,
      @RequestBody byte[] imageBytes) {

    locationService.receiveLibraryCoverArt(locationId, apiKey, sourceAlbumId, imageBytes);
    return ResponseEntity.noContent().build();
  }

  /** Admin-only, bar-owner accounting: every credit transaction tagged with this location. */
  @GetMapping("/{locationId}/credit-ledger")
  public ResponseEntity<List<CreditTransactionDto>> getCreditLedger(
      @PathVariable String locationId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

    return ResponseEntity.ok(userService.getCreditLedgerForLocation(locationId, from, to));
  }
}
