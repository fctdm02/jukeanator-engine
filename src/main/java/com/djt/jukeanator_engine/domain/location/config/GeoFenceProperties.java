package com.djt.jukeanator_engine.domain.location.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties bound to the {@code app.geo-fence:} YAML prefix. Geo-fencing restricts Web/Mobile UI
 * patrons to queue operations only while they are physically at a location whose
 * {@code isGeoFenced} flag is set. It is enforced only when {@code enabled} is true <em>and</em>
 * {@code app.mode=master}, since master is the instance that receives Web/Mobile UI traffic in
 * production; any other combination leaves every queue operation unrestricted.
 */
@Validated
@ConfigurationProperties(prefix = "app.geo-fence")
public class GeoFenceProperties {

  private boolean enabled = false;
  private double radiusMeters = 50.0;
  private double maxAccuracyMeters = 150.0;
  private long maxPositionAgeSeconds = 120L;
  private boolean allowSimulatedPosition = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public double getRadiusMeters() {
    return radiusMeters;
  }

  public void setRadiusMeters(double radiusMeters) {
    this.radiusMeters = radiusMeters;
  }

  public double getMaxAccuracyMeters() {
    return maxAccuracyMeters;
  }

  public void setMaxAccuracyMeters(double maxAccuracyMeters) {
    this.maxAccuracyMeters = maxAccuracyMeters;
  }

  public long getMaxPositionAgeSeconds() {
    return maxPositionAgeSeconds;
  }

  public void setMaxPositionAgeSeconds(long maxPositionAgeSeconds) {
    this.maxPositionAgeSeconds = maxPositionAgeSeconds;
  }

  public boolean isAllowSimulatedPosition() {
    return allowSimulatedPosition;
  }

  public void setAllowSimulatedPosition(boolean allowSimulatedPosition) {
    this.allowSimulatedPosition = allowSimulatedPosition;
  }
}
