package com.djt.jukeanator_engine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.location.config.GeoFenceProperties;

/**
 * Fails fast on obviously-inconsistent {@code app.mode} configuration, rather than letting a
 * slave silently run with no master to talk to, or a master silently run with the JFC/Swing UI
 * (and its process-global LOCAL identity) active.
 */
@Component
public class AppModeValidator implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AppModeValidator.class);

  private final AppProperties appProperties;
  private final GeoFenceProperties geoFenceProperties;

  public AppModeValidator(AppProperties appProperties, GeoFenceProperties geoFenceProperties) {
    this.appProperties = appProperties;
    this.geoFenceProperties = geoFenceProperties;
  }

  @Override
  public void run(ApplicationArguments args) {

    if (appProperties.isSlave()) {
      requireNonBlank("app.master-instance-url", appProperties.getMasterInstanceUrl());
      requireNonNull("app.location-id", appProperties.getLocationId());
      requireNonBlank("app.location-api-key", appProperties.getLocationApiKey());
    }

    if (appProperties.isMaster() && appProperties.isUiEnabled()) {
      throw new IllegalStateException(
          "app.mode=master requires app.ui-enabled=false — the master is headless and "
              + "location-agnostic; it must never launch the JFC/Swing UI.");
    }

    log.info("Running with app.mode={}", appProperties.getMode());
    logGeoFenceSettings();
  }

  // Geo-fencing misconfiguration is logged rather than fatal: it only ever leaves the fence
  // unenforced or more permissive, and must never stop a location's jukebox from starting.
  private void logGeoFenceSettings() {

    if (geoFenceProperties.isEnabled() && !appProperties.isMaster()) {
      log.warn("app.geo-fence.enabled=true is ignored: geo-fencing is only enforced when "
          + "app.mode=master (current app.mode={})", appProperties.getMode());
    } else if (geoFenceProperties.isEnabled()) {
      log.info("Geo-fencing enabled: radius={} m, max accuracy={} m, max position age={} s",
          geoFenceProperties.getRadiusMeters(), geoFenceProperties.getMaxAccuracyMeters(),
          geoFenceProperties.getMaxPositionAgeSeconds());
    }

    if (geoFenceProperties.isAllowSimulatedPosition()) {
      log.warn("app.geo-fence.allow-simulated-position=true: patrons can enter any location by "
          + "hand. This is for QA only and must never be set in production.");
    }
  }

  private void requireNonBlank(String propertyName, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "app.mode=slave requires " + propertyName + " to be set, but it was blank/missing.");
    }
  }

  private void requireNonNull(String propertyName, Object value) {
    if (value == null) {
      throw new IllegalStateException(
          "app.mode=slave requires " + propertyName + " to be set, but it was blank/missing.");
    }
  }
}
