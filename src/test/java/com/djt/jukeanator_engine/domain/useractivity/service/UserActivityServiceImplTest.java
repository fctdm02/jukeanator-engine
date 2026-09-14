package com.djt.jukeanator_engine.domain.useractivity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.useractivity.event.UserActivityRecordedEvent;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.djt.jukeanator_engine.domain.useractivity.repository.UserActivityRepository;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceImplTest {

  @Mock
  private UserActivityRepository userActivityRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private UserActivityServiceImpl userActivityService;

  @Captor
  private ArgumentCaptor<UserActivityRecordedEvent> eventCaptor;

  @Test
  void record_publishesAnEvent_withFieldsCarriedThroughAndOccurredAtStamped() {

    userActivityService.record(Integer.valueOf(5), UserActivitySource.MOBILE_WEB,
        "user@example.com", UserActivityType.QUEUE_SONG_REMOVED,
        Map.of("albumId", 1, "songId", 2));

    verify(eventPublisher).publishEvent(eventCaptor.capture());

    var record = eventCaptor.getValue().getRecord();
    assertEquals(Integer.valueOf(5), record.locationId());
    assertEquals(UserActivitySource.MOBILE_WEB, record.source());
    assertEquals("user@example.com", record.username());
    assertEquals(UserActivityType.QUEUE_SONG_REMOVED, record.activityType());
    assertEquals(Map.of("albumId", 1, "songId", 2), record.details());
    assertNotNull(record.occurredAt());
  }

  @Test
  void record_withNullDetails_recordsAnEmptyMap_ratherThanNull() {

    userActivityService.record(Integer.valueOf(1), UserActivitySource.SWING_UI, "LOCAL",
        UserActivityType.TAB_NAVIGATION, null);

    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertEquals(Map.of(), eventCaptor.getValue().getRecord().details());
  }

  @Test
  void recordSwingActivity_defaultsSourceToSwingUi_andUsernameToLocalSentinel() {

    userActivityService.recordSwingActivity(Integer.valueOf(3), UserActivityType.ARTIST_VIEWED,
        Map.of("artistName", "Radiohead"));

    verify(eventPublisher).publishEvent(eventCaptor.capture());

    var record = eventCaptor.getValue().getRecord();
    assertEquals(UserActivitySource.SWING_UI, record.source());
    assertEquals(SongQueueService.LOCAL_USERNAME, record.username());
    assertEquals(UserActivityType.ARTIST_VIEWED, record.activityType());
  }

  @Test
  void findRecentActivity_delegatesDirectlyToTheRepository() {

    List<UserActivityRecord> expected = List.of(new UserActivityRecord(Integer.valueOf(5),
        UserActivitySource.SWING_UI, "LOCAL", UserActivityType.TAB_NAVIGATION, Instant.now(),
        Map.of()));
    when(userActivityRepository.findRecentActivity(Integer.valueOf(5), 500)).thenReturn(expected);

    List<UserActivityRecord> result = userActivityService.findRecentActivity(Integer.valueOf(5), 500);

    assertSame(expected, result);
  }

  @Test
  void purgeActivityOlderThan_computesCutoffFromRetentionDays_andDelegatesToTheRepository() {

    Instant before = Instant.now().minus(30, ChronoUnit.DAYS);

    userActivityService.purgeActivityOlderThan(30);

    ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(userActivityRepository).purgeOlderThan(cutoffCaptor.capture());

    Instant after = Instant.now().minus(30, ChronoUnit.DAYS);
    Instant cutoff = cutoffCaptor.getValue();
    assertTrue(!cutoff.isBefore(before) && !cutoff.isAfter(after),
        "Expected cutoff to be ~30 days before now, was: " + cutoff);
  }
}
