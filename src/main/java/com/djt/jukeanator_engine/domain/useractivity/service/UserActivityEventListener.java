package com.djt.jukeanator_engine.domain.useractivity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
import com.djt.jukeanator_engine.domain.useractivity.event.UserActivityRecordedEvent;
import com.djt.jukeanator_engine.domain.useractivity.repository.UserActivityRepository;

/**
 * Persists {@link UserActivityRecordedEvent}s off the thread that captured them. {@code @Async}
 * requires {@code @EnableAsync} on the application context, already present on {@code AppConfig}.
 * A persistence failure here is logged, not rethrown -- losing one activity-tracking record must
 * never surface as a user-visible error in the Swing UI or an HTTP response for the action that
 * triggered it.
 */
public class UserActivityEventListener {

  private static final Logger log = LoggerFactory.getLogger(UserActivityEventListener.class);

  private final UserActivityRepository userActivityRepository;

  public UserActivityEventListener(UserActivityRepository userActivityRepository) {
    this.userActivityRepository = userActivityRepository;
  }

  @Async
  @EventListener
  public void onUserActivityRecorded(UserActivityRecordedEvent event) {
    try {
      userActivityRepository.record(event.getRecord());
    } catch (Exception e) {
      log.warn("Failed to persist user activity record: {}", event.getRecord(), e);
    }
  }

  // Synchronous (not @Async), so activity is already filed under the confirmed id before the
  // reconciliation that published this event returns. Logged, not rethrown, for the same reason
  // as above -- activity tracking must never fail the location-id correction itself.
  @EventListener
  public void onOwnLocationIdChanged(OwnLocationIdChangedEvent event) {
    try {
      userActivityRepository.changeLocationId(event.previousLocationId(),
          event.confirmedLocationId());
    } catch (Exception e) {
      log.warn("Failed to move user activity from locationId {} to {}",
          event.previousLocationId(), event.confirmedLocationId(), e);
    }
  }
}
