package com.djt.jukeanator_engine.domain.useractivity.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
import com.djt.jukeanator_engine.domain.useractivity.repository.UserActivityRepository;

@ExtendWith(MockitoExtension.class)
class UserActivityEventListenerTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);

  @Mock
  private UserActivityRepository userActivityRepository;

  @InjectMocks
  private UserActivityEventListener userActivityEventListener;

  @Test
  void onOwnLocationIdChanged_delegatesToRepositoryWithPreviousAndConfirmedIds() {

    userActivityEventListener.onOwnLocationIdChanged(
        new OwnLocationIdChangedEvent(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));

    verify(userActivityRepository).changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);
  }

  @Test
  void onOwnLocationIdChanged_swallowsRepositoryFailure_soTheIdCorrectionItselfNeverFails() {

    // Activity tracking must never fail the location-id correction that published this event --
    // e.g. the filesystem repository failing to move a day-file.
    doThrow(new UncheckedIOException("disk full", new IOException("disk full")))
        .when(userActivityRepository)
        .changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertDoesNotThrow(() -> userActivityEventListener.onOwnLocationIdChanged(
        new OwnLocationIdChangedEvent(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID)));

    verify(userActivityRepository).changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);
  }
}
