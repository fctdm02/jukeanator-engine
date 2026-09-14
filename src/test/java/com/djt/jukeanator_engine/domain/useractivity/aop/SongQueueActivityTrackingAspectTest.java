package com.djt.jukeanator_engine.domain.useractivity.aop;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityService;

@ExtendWith(MockitoExtension.class)
class SongQueueActivityTrackingAspectTest {

  @Mock
  private UserActivityService userActivityService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private SongQueueActivityTrackingAspect aspectUnderTest() {
    return new SongQueueActivityTrackingAspect(userActivityService);
  }

  private JoinPoint joinPointWithArgs(Object... args) {
    JoinPoint jp = mock(JoinPoint.class);
    when(jp.getArgs()).thenReturn(args);
    return jp;
  }

  private void authenticateAs(String username) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(username, null, List.of()));
  }

  @Test
  void trackAddSongToQueue_normalPlay_recordsQueueSongAdded_asSwingUi_whenLocalUser() {

    authenticateAs(SongQueueService.LOCAL_USERNAME);
    AddSongToQueueRequest request =
        new AddSongToQueueRequest(SongQueueService.LOCAL_USERNAME, 10, 20, 1, false);

    aspectUnderTest().trackAddSongToQueue(joinPointWithArgs(Integer.valueOf(7), request));

    verify(userActivityService).record(eq(Integer.valueOf(7)), eq(UserActivitySource.SWING_UI),
        eq(SongQueueService.LOCAL_USERNAME), eq(UserActivityType.QUEUE_SONG_ADDED),
        eq(Map.of("albumId", 10, "songId", 20)));
  }

  @Test
  void trackAddSongToQueue_priorityPlay_recordsQueuePrioritySongAdded_asMobileWeb_whenNotLocalUser() {

    authenticateAs("user@example.com");
    AddSongToQueueRequest request =
        new AddSongToQueueRequest("user@example.com", 10, 20, 2, true);

    aspectUnderTest().trackAddSongToQueue(joinPointWithArgs(Integer.valueOf(7), request));

    verify(userActivityService).record(eq(Integer.valueOf(7)), eq(UserActivitySource.MOBILE_WEB),
        eq("user@example.com"), eq(UserActivityType.QUEUE_PRIORITY_SONG_ADDED),
        eq(Map.of("albumId", 10, "songId", 20)));
  }

  @Test
  void trackMoveSongUpInQueue_recordsQueueSongMovedUp() {

    authenticateAs(SongQueueService.LOCAL_USERNAME);
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(10, 20);

    aspectUnderTest().trackMoveSongUpInQueue(joinPointWithArgs(Integer.valueOf(3), request));

    verify(userActivityService).record(eq(Integer.valueOf(3)), eq(UserActivitySource.SWING_UI),
        eq(SongQueueService.LOCAL_USERNAME), eq(UserActivityType.QUEUE_SONG_MOVED_UP),
        eq(Map.of("albumId", 10, "songId", 20)));
  }

  @Test
  void trackMoveSongDownInQueue_recordsQueueSongMovedDown() {

    authenticateAs(SongQueueService.LOCAL_USERNAME);
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(10, 20);

    aspectUnderTest().trackMoveSongDownInQueue(joinPointWithArgs(Integer.valueOf(3), request));

    verify(userActivityService).record(eq(Integer.valueOf(3)), eq(UserActivitySource.SWING_UI),
        eq(SongQueueService.LOCAL_USERNAME), eq(UserActivityType.QUEUE_SONG_MOVED_DOWN),
        eq(Map.of("albumId", 10, "songId", 20)));
  }

  @Test
  void trackRemoveSongDownFromQueue_recordsQueueSongRemoved_asMobileWeb_whenAuthenticatedAsWebUser() {

    authenticateAs("user@example.com");
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(10, 20);

    aspectUnderTest()
        .trackRemoveSongDownFromQueue(joinPointWithArgs(Integer.valueOf(3), request));

    verify(userActivityService).record(eq(Integer.valueOf(3)), eq(UserActivitySource.MOBILE_WEB),
        eq("user@example.com"), eq(UserActivityType.QUEUE_SONG_REMOVED),
        eq(Map.of("albumId", 10, "songId", 20)));
  }

  @Test
  void trackMoveSongUpInQueue_withNoAuthenticationPresent_fallsBackToLocalUsername() {

    // No authenticateAs(...) call -- simulates a background thread with no SecurityContext set.
    ChangeSongQueueRequest request = new ChangeSongQueueRequest(10, 20);

    aspectUnderTest().trackMoveSongUpInQueue(joinPointWithArgs(Integer.valueOf(3), request));

    verify(userActivityService).record(eq(Integer.valueOf(3)), eq(UserActivitySource.SWING_UI),
        eq(SongQueueService.LOCAL_USERNAME), eq(UserActivityType.QUEUE_SONG_MOVED_UP),
        eq(Map.of("albumId", 10, "songId", 20)));
  }
}
