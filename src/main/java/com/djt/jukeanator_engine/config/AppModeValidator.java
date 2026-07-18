package com.djt.jukeanator_engine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fails fast on obviously-inconsistent {@code app.mode} configuration, rather than letting a
 * slave silently run with no master to talk to, or a master silently run with the JFC/Swing UI
 * (and its process-global LOCAL identity) active.
 */
@Component
public class AppModeValidator implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AppModeValidator.class);

  private final AppProperties appProperties;

  public AppModeValidator(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  @Override
  public void run(ApplicationArguments args) {

    if (appProperties.isSlave()) {
      requireNonBlank("app.master-instance-url", appProperties.getMasterInstanceUrl());
      requireNonBlank("app.location-id", appProperties.getLocationId());
      requireNonBlank("app.location-api-key", appProperties.getLocationApiKey());
    }

    if (appProperties.isMaster() && appProperties.isUiEnabled()) {
      throw new IllegalStateException(
          "app.mode=master requires app.ui-enabled=false — the master is headless and "
              + "location-agnostic; it must never launch the JFC/Swing UI.");
    }

    log.info("Running with app.mode={}", appProperties.getMode());
  }

  private void requireNonBlank(String propertyName, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "app.mode=slave requires " + propertyName + " to be set, but it was blank/missing.");
    }
  }
}
