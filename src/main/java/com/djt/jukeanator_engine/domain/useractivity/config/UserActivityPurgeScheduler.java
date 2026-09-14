package com.djt.jukeanator_engine.domain.useractivity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.useractivity.service.UserActivityService;

/**
 * Daily purge of activity records older than {@link UserActivityProperties#getRetentionDays()}
 * (30 days by default), keeping the activity log/table bounded to a rolling window instead of
 * growing forever -- mirrors {@code SongLibraryScheduler}'s daily-cron + SYSTEM security-context
 * pattern, needed here for the same reason: this runs on Spring's scheduling thread, which in
 * REST/remote mode carries no security context of its own (only Swing/local mode's {@code
 * SecurityContextHolder.MODE_GLOBAL} would make one visible automatically).
 */
@Component
public class UserActivityPurgeScheduler {

  private static final Logger log = LoggerFactory.getLogger(UserActivityPurgeScheduler.class);

  private final UserActivityService userActivityService;
  private final UserActivityProperties userActivityProperties;

  public UserActivityPurgeScheduler(UserActivityService userActivityService,
      UserActivityProperties userActivityProperties) {
    this.userActivityService = userActivityService;
    this.userActivityProperties = userActivityProperties;
  }

  @Scheduled(cron = "0 15 5 * * *")
  public void purgeExpiredActivityDaily() {

    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(SystemPrincipal.SystemAuthenticationToken.INSTANCE);
    SecurityContextHolder.setContext(ctx);

    try {
      int retentionDays = userActivityProperties.getRetentionDays();
      userActivityService.purgeActivityOlderThan(retentionDays);
      log.info("Purged user activity records older than {} days", retentionDays);
    } catch (Exception e) {
      log.error("Failed to purge expired user activity records", e);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
