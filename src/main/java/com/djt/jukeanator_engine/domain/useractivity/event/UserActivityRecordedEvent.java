package com.djt.jukeanator_engine.domain.useractivity.event;

import org.springframework.context.ApplicationEvent;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;

/**
 * Published by {@code UserActivityServiceImpl.record(...)} the moment an activity is captured.
 * {@code UserActivityEventListener} handles this {@code @Async}, so the calling thread (the Swing
 * EDT for desktop UI events, or the HTTP request thread for mobile/web queue actions) never blocks
 * on the repository's file/DB I/O.
 */
public class UserActivityRecordedEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1L;

  private final UserActivityRecord record;

  public UserActivityRecordedEvent(Object source, UserActivityRecord record) {
    super(source);
    this.record = record;
  }

  public UserActivityRecord getRecord() {
    return record;
  }
}
