package com.djt.jukeanator_engine.domain.useractivity.aop;

import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityService;

/**
 * Records a {@link UserActivityType#QUEUE_SONG_ADDED}/{@code QUEUE_PRIORITY_SONG_ADDED}/{@code
 * QUEUE_SONG_MOVED_UP}/{@code QUEUE_SONG_MOVED_DOWN}/{@code QUEUE_SONG_REMOVED} event for every
 * successful call to the 4 queue-mutating {@link SongQueueService} methods.
 *
 * <p>
 * Both the Swing desktop UI ({@code QueuePanel}, {@code AddSongToQueueCard}) and the mobile/web REST
 * API ({@code SongQueueController}) call these same {@link SongQueueService} methods, so this one
 * aspect covers both UIs without any change to {@code SongQueueServiceImpl} or either caller --
 * mirroring how {@link com.djt.jukeanator_engine.domain.common.aop.ServiceLoggingAspect} wraps every
 * service method for logging.
 *
 * <p>
 * Source/username is resolved the same way {@code ServiceLoggingAspect} resolves it -- from {@link
 * SecurityContextHolder}, populated for every thread either by {@code LocalSecurityContextConfigurer}
 * (Swing, {@link SongQueueService#LOCAL_USERNAME}) or the JWT filter chain (mobile/web, the
 * authenticated user's email) -- rather than from the request DTO, since {@link
 * ChangeSongQueueRequest} (used by the 3 move/remove methods) carries no username field at all.
 *
 * <p>
 * {@code @AfterReturning} rather than {@code @Around}: a failed queue action (e.g. insufficient
 * credits, an already-full queue) isn't a genuine user activity to record.
 */
@Aspect
@Component
public class SongQueueActivityTrackingAspect {

  private final UserActivityService userActivityService;

  public SongQueueActivityTrackingAspect(UserActivityService userActivityService) {
    this.userActivityService = userActivityService;
  }

  @Pointcut("execution(* com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService."
      + "addSongToQueue(..))")
  private void addSongToQueue() {}

  @Pointcut("execution(* com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService."
      + "moveSongUpInQueue(..))")
  private void moveSongUpInQueue() {}

  @Pointcut("execution(* com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService."
      + "moveSongDownInQueue(..))")
  private void moveSongDownInQueue() {}

  @Pointcut("execution(* com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService."
      + "removeSongDownFromQueue(..))")
  private void removeSongDownFromQueue() {}

  @AfterReturning("addSongToQueue()")
  public void trackAddSongToQueue(JoinPoint jp) {

    Integer locationId = (Integer) jp.getArgs()[0];
    AddSongToQueueRequest request = (AddSongToQueueRequest) jp.getArgs()[1];

    UserActivityType activityType =
        request.priorityPlay() ? UserActivityType.QUEUE_PRIORITY_SONG_ADDED
            : UserActivityType.QUEUE_SONG_ADDED;

    record(locationId, activityType,
        Map.of("albumId", request.albumId(), "songId", request.songId()));
  }

  @AfterReturning("moveSongUpInQueue()")
  public void trackMoveSongUpInQueue(JoinPoint jp) {
    trackChange(jp, UserActivityType.QUEUE_SONG_MOVED_UP);
  }

  @AfterReturning("moveSongDownInQueue()")
  public void trackMoveSongDownInQueue(JoinPoint jp) {
    trackChange(jp, UserActivityType.QUEUE_SONG_MOVED_DOWN);
  }

  @AfterReturning("removeSongDownFromQueue()")
  public void trackRemoveSongDownFromQueue(JoinPoint jp) {
    trackChange(jp, UserActivityType.QUEUE_SONG_REMOVED);
  }

  private void trackChange(JoinPoint jp, UserActivityType activityType) {

    Integer locationId = (Integer) jp.getArgs()[0];
    ChangeSongQueueRequest request = (ChangeSongQueueRequest) jp.getArgs()[1];

    record(locationId, activityType,
        Map.of("albumId", request.albumId(), "songId", request.songId()));
  }

  private void record(Integer locationId, UserActivityType activityType,
      Map<String, Object> details) {

    String username = resolveUsername();
    UserActivitySource source = SongQueueService.LOCAL_USERNAME.equals(username)
        ? UserActivitySource.SWING_UI
        : UserActivitySource.MOBILE_WEB;

    userActivityService.record(locationId, source, username, activityType, details);
  }

  private static String resolveUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.isAuthenticated()) ? auth.getName()
        : SongQueueService.LOCAL_USERNAME;
  }
}
