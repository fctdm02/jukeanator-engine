package com.djt.jukeanator_engine.domain.license.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.djt.jukeanator_engine.domain.common.security.SecurityContextPropagatingRunnable;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.license.config.LicenseProperties;

/**
 * Background license work on one daemon thread, following the slave sync services' pattern
 * (system principal, failures logged and swallowed, never blocking local operation):
 *
 * <ul>
 * <li>right after startup: automatic provisioning, subscription check, heartbeat, version check;</li>
 * <li>hourly: the subscription hour counter;</li>
 * <li>daily at {@code license.revalidation-hour}: subscription check and version check;</li>
 * <li>every {@code license.heartbeat-interval}: heartbeat.</li>
 * </ul>
 *
 * <p>Server calls are skipped when {@code license.server-url} is blank.
 */
public class LicenseScheduler {

  private static final Logger log = LoggerFactory.getLogger(LicenseScheduler.class);

  private final LicenseService licenseService;
  private final LicenseProperties properties;
  private final ScheduledExecutorService executor;

  public LicenseScheduler(LicenseService licenseService, LicenseProperties properties) {
    this.licenseService = licenseService;
    this.properties = properties;
    this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "license-scheduler");
      t.setDaemon(true);
      return t;
    });
  }

  public void start() {
    // Separate tasks, so one failing step never skips the others.
    executor.execute(task("automatic provisioning", () -> {
      if (serverEnabled()) {
        licenseService.autoProvisionIfConfigured();
      }
    }));
    executor.execute(task("startup subscription check", () -> {
      if (serverEnabled()) {
        licenseService.validateSubscription();
      }
    }));
    executor.execute(task("startup heartbeat", () -> {
      if (serverEnabled()) {
        licenseService.sendHeartbeat();
      }
    }));
    executor.execute(task("startup version check", () -> {
      if (serverEnabled()) {
        licenseService.checkEngineVersion();
      }
    }));

    executor.scheduleAtFixedRate(task("hourly tick", licenseService::hourlyTick), 1, 1,
        TimeUnit.HOURS);

    long daily = Duration.ofDays(1).toMinutes();
    long firstDaily = minutesUntilRevalidation();
    executor.scheduleAtFixedRate(task("daily subscription check", () -> {
      if (serverEnabled()) {
        licenseService.validateSubscription();
      }
    }), firstDaily, daily, TimeUnit.MINUTES);
    executor.scheduleAtFixedRate(task("daily version check", () -> {
      if (serverEnabled()) {
        licenseService.checkEngineVersion();
      }
    }), firstDaily, daily, TimeUnit.MINUTES);

    long heartbeatMinutes = Math.max(5, properties.getHeartbeatInterval().toMinutes());
    executor.scheduleWithFixedDelay(task("heartbeat", () -> {
      if (serverEnabled()) {
        licenseService.sendHeartbeat();
      }
    }), heartbeatMinutes, heartbeatMinutes, TimeUnit.MINUTES);
  }

  @PreDestroy
  public void stop() {
    executor.shutdownNow();
  }

  private boolean serverEnabled() {
    return properties.getServerUrl() != null && !properties.getServerUrl().isBlank();
  }

  private long minutesUntilRevalidation() {
    String z = properties.getTimeZone();
    ZoneId zone = z == null || z.isBlank() ? ZoneId.systemDefault() : ZoneId.of(z);
    LocalDateTime now = LocalDateTime.now(zone);
    int hour = Math.floorMod(properties.getRevalidationHour(), 24);
    LocalDateTime next = now.toLocalDate().atTime(hour, 0);
    if (!next.isAfter(now)) {
      next = next.plusDays(1);
    }
    return Math.max(1, Duration.between(now, next).toMinutes());
  }

  private static Runnable task(String name, Runnable work) {
    return new SecurityContextPropagatingRunnable(() -> {
      try {
        work.run();
      } catch (RuntimeException e) {
        log.warn("License {} failed: {}", name, e.getMessage(), e);
      }
    }, SystemPrincipal.SystemAuthenticationToken.INSTANCE);
  }
}
