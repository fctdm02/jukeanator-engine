package com.djt.jukeanator_engine.domain.songqueue.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.config.NotMasterModeCondition;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

import jakarta.annotation.PreDestroy;

/**
 * On slave/standalone instances, saves the song queue to disk on shutdown so it can be restored
 * on the next startup instead of resetting to empty. Skipped on the master, which is
 * headless/location-agnostic and doesn't own a local song queue to persist.
 */
@Component
@Conditional(NotMasterModeCondition.class)   // exists everywhere except master
public class SongQueueScheduler {

  private static final Logger log = LoggerFactory.getLogger(SongQueueScheduler.class);

  private final AppProperties appProperties;
  private final SongQueueService songQueueService;

  public SongQueueScheduler(AppProperties appProperties, SongQueueService songQueueService) {
    this.appProperties = appProperties;
    this.songQueueService = songQueueService;
  }

  @PreDestroy
  public void storeSongQueueOnShutdown() {

    if (appProperties.isMaster()) {
      return;
    }

    /*
     * Runs on the JVM shutdown hook thread, which carries no security context — seed one with the
     * internal SYSTEM principal to satisfy ServiceSecurityAspect, mirroring SongLibraryScheduler.
     */
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(SystemPrincipal.SystemAuthenticationToken.INSTANCE);
    SecurityContextHolder.setContext(ctx);

    try {
      songQueueService.storeSongQueue();
      log.info("Stored song queue (application shutdown)");
    } catch (Exception e) {
      log.error("Failed to store song queue (application shutdown)", e);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
