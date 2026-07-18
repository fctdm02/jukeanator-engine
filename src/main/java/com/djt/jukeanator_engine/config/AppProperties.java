package com.djt.jukeanator_engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector.OSType;

/**
 * Top-level application properties bound to the {@code app:} YAML prefix.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private boolean uiEnabled = false; // if true, a JFC UI is launched, otherwise, headless backend

  private String rootPathWindows;
  private String rootPathUnix; // Both Linux and MacOSX

  // standalone: today's single-tenant behavior (default). master: headless, location-agnostic,
  // hosts the location registry and proxies to slaves. slave: a physical location that syncs its
  // library to the master and accepts remote commands over a persistent connection.
  private String mode = "standalone";
  private String masterInstanceUrl; // slave-only: e.g. https://jukeanator.com
  private String locationId; // slave-only: assigned at provisioning
  private String locationApiKey; // slave-only: secret issued at provisioning

  private Jwt jwt = new Jwt();
  private Logging logging = new Logging();

  public boolean isUiEnabled() {
    return uiEnabled;
  }

  public void setUiEnabled(boolean uiEnabled) {
    this.uiEnabled = uiEnabled;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public boolean isStandalone() {
    return "standalone".equalsIgnoreCase(mode);
  }

  public boolean isMaster() {
    return "master".equalsIgnoreCase(mode);
  }

  public boolean isSlave() {
    return "slave".equalsIgnoreCase(mode);
  }

  public String getMasterInstanceUrl() {
    return masterInstanceUrl;
  }

  public void setMasterInstanceUrl(String masterInstanceUrl) {
    this.masterInstanceUrl = masterInstanceUrl;
  }

  public String getLocationId() {
    return locationId;
  }

  public void setLocationId(String locationId) {
    this.locationId = locationId;
  }

  public String getLocationApiKey() {
    return locationApiKey;
  }

  public void setLocationApiKey(String locationApiKey) {
    this.locationApiKey = locationApiKey;
  }

  public String getEffectiveRootPath() {

    if (OperatingSystemDetector.getOperatingSystem() == OSType.WINDOWS) {
      return rootPathWindows;
    }
    return rootPathUnix;
  }

  public String getRootPathWindows() {
    return rootPathWindows;
  }

  public String getRootPathUnix() {
    return rootPathUnix;
  }

  public void setRootPathUnix(String rootPathUnix) {
    this.rootPathUnix = rootPathUnix;
  }
  
  public void setRootPathWindows(String rootPathWindows) {
    this.rootPathWindows = rootPathWindows;
  }

  public Jwt getJwt() {
    return jwt;
  }

  public void setJwt(Jwt jwt) {
    this.jwt = jwt;
  }

  public Logging getLogging() {
    return logging;
  }

  public void setLogging(Logging logging) {
    this.logging = logging;
  }

  // ── Nested types ──────────────────────────────────────────────────────────

  public static class Jwt {

    private String secret;
    private long expirationMs;

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }

    public long getExpirationMs() {
      return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
      this.expirationMs = expirationMs;
    }
  }

  public static class Logging {

    private String filePath;
    private String maxFileSize;

    public String getFilePath() {
      return filePath;
    }

    public void setFilePath(String filePath) {
      this.filePath = filePath;
    }

    public String getMaxFileSize() {
      return maxFileSize;
    }

    public void setMaxFileSize(String maxFileSize) {
      this.maxFileSize = maxFileSize;
    }
  }
}
