package com.djt.jukeanator_engine.domain.useractivity.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import com.djt.jukeanator_engine.domain.useractivity.event.UserActivityRecordedEvent;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.djt.jukeanator_engine.domain.useractivity.repository.UserActivityRepository;

public class UserActivityServiceImpl implements UserActivityService {

  private final UserActivityRepository userActivityRepository;
  private final ApplicationEventPublisher eventPublisher;

  public UserActivityServiceImpl(UserActivityRepository userActivityRepository,
      ApplicationEventPublisher eventPublisher) {
    this.userActivityRepository = userActivityRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void record(Integer locationId, UserActivitySource source, String username,
      UserActivityType activityType, Map<String, Object> details) {

    UserActivityRecord record = new UserActivityRecord(locationId, source, username, activityType,
        Instant.now(), details != null ? details : Map.of());

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
}
