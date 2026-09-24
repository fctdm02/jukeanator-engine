package com.djt.jukeanator_engine.domain.useractivity.controller;

import static java.util.Objects.requireNonNull;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.location.controller.LocationController;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityService;

/**
 * Master-only. Slave user-activity mirror intake -- the activity counterpart of {@code
 * FinancialLedgerSyncController}, authenticated the exact same way (the {@code
 * location-id}/{@code location-api-key} headers, via {@code LocationApiKeyAuthenticationFilter}).
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations")
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class UserActivitySyncController {

  private final UserActivityService userActivityService;

  public UserActivitySyncController(UserActivityService userActivityService) {

    requireNonNull(userActivityService, "userActivityService cannot be null");
    this.userActivityService = userActivityService;
  }

  @PostMapping("/{locationId}/user-activity/sync")
  public ResponseEntity<Void> syncUserActivity(
      @PathVariable Integer locationId,
      @RequestHeader(LocationController.LOCATION_API_KEY_HEADER) String apiKey,
      @RequestBody List<UserActivityRecord> records) {

    userActivityService.receiveUserActivitySync(locationId, apiKey, records);
    return ResponseEntity.noContent().build();
  }
}
