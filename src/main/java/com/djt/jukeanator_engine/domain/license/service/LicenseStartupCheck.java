package com.djt.jukeanator_engine.domain.license.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import com.djt.jukeanator_engine.domain.common.security.SecurityContextPropagatingRunnable;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.license.model.LicenseStatus;

/**
 * Legacy {@code checkRuntime()} at startup: reads the license files synchronously (local only, so
 * the UI starts with the right commercial state), then hands every license server call to the
 * {@link LicenseScheduler} so a slow or unreachable server never delays startup.
 */
public class LicenseStartupCheck implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LicenseStartupCheck.class);

  private final LicenseService licenseService;
  private final LicenseScheduler scheduler;

  public LicenseStartupCheck(LicenseService licenseService, LicenseScheduler scheduler) {
    this.licenseService = licenseService;
    this.scheduler = scheduler;
  }

  @Override
  public void run(ApplicationArguments args) {
    new SecurityContextPropagatingRunnable(() -> {
      try {
        LicenseStatus status = licenseService.refreshLocal();
        log.info("License: {} (machine id {}, folder {}), commercial operation {}",
            status.describe(), status.machineId(), status.licenseDirectory(),
            status.commercialEnabled() ? "enabled" : "disabled");
        if (status.message() != null) {
          log.warn("License message {}: {}", status.messageCode(), status.message());
        }
      } catch (RuntimeException e) {
        log.error("License check failed; running non-commercial: {}", e.getMessage(), e);
      }
    }, SystemPrincipal.SystemAuthenticationToken.INSTANCE).run();
    scheduler.start();
  }
}
