package com.djt.jukeanator_engine.domain.useractivity.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.useractivity.event.UserActivityRecordedEvent;
import com.djt.jukeanator_engine.domain.useractivity.model.PendingUserActivity;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.djt.jukeanator_engine.domain.useractivity.repository.UserActivityRepository;

public class UserActivityServiceImpl implements UserActivityService {

  private static final Logger log = LoggerFactory.getLogger(UserActivityServiceImpl.class);

  private final UserActivityRepository userActivityRepository;
  private final ApplicationEventPublisher eventPublisher;
  // Looked up lazily: SongQueueActivityTrackingAspect depends on this service, and aspects are
  // instantiated early -- an eager LocationService dependency here would drag LocationService into
  // that early phase, ahead of the proxies (security, logging) it should be wrapped in.
  private final Supplier<LocationService> locationService;

  public UserActivityServiceImpl(UserActivityRepository userActivityRepository,
      ApplicationEventPublisher eventPublisher, Supplier<LocationService> locationService) {
    this.userActivityRepository = userActivityRepository;
    this.eventPublisher = eventPublisher;
    this.locationService = locationService;
  }

  @Override
  public void record(Integer locationId, UserActivitySource source, String username,
      UserActivityType activityType, Map<String, Object> details) {

    UserActivityRecord record = new UserActivityRecord(locationId, source, username, activityType,
        Instant.now(), details != null ? details : Map.of(), UUID.randomUUID().toString());

    // Publishing is synchronous (returns only once every listener has run), but the one listener
    // that does the actual persistence (UserActivityEventListener) is itself @Async, so this call
    // still returns to the caller -- the Swing EDT or an HTTP request thread -- without waiting on
    // file/DB I/O.
    eventPublisher.publishEvent(new UserActivityRecordedEvent(this, record));
  }

  @Override
  public List<UserActivityRecord> findRecentActivity(Integer locationId, int limit) {
    return userActivityRepository.findRecentActivity(locationId, limit);
  }

  @Override
  public void purgeActivityOlderThan(int retentionDays) {
    Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    userActivityRepository.purgeOlderThan(cutoff);
  }

  @Override
  public List<PendingUserActivity> getPendingMasterSync(int limit) {
    return userActivityRepository.findPendingMasterSync(limit);
  }

  @Override
  public void markSyncedToMaster(List<PendingUserActivity> acknowledged) {
    userActivityRepository.markSyncedToMaster(acknowledged);
  }

  @Override
  public synchronized int receiveUserActivitySync(Integer locationId, String apiKey,
      List<UserActivityRecord> records) throws LocationServiceException {

    // Re-verifies locationId+apiKey even though the security filter chain already authenticated
    // *some* location's credentials -- it never confirms those credentials belong to *this*
    // path's locationId. Same check as FinancialLedgerServiceImpl.requireValidLocation.
    if (!locationService.get().verifyApiKey(locationId, apiKey)) {
      throw new LocationServiceException("Invalid locationId/apiKey for locationId: " + locationId);
    }

    int stored = 0;
    for (UserActivityRecord record : records) {
      if (record.activityId() == null || record.source() == null || record.activityType() == null
          || record.username() == null || record.occurredAt() == null) {
        log.warn("Skipping incomplete mirrored activity record from locationId " + locationId
            + ": " + record);
        continue;
      }
      UserActivityRecord attributed = new UserActivityRecord(locationId, record.source(),
          record.username(), record.activityType(), record.occurredAt(),
          record.details() != null ? record.details() : Map.of(), record.activityId());
      if (userActivityRepository.recordIfAbsent(attributed)) {
        stored++;
      }
    }
    return stored;
  }
}
