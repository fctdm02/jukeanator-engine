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

  private String rootPathWindows;
  private String rootPathUnix; // Both Linux and MacOSX

  private Jwt jwt = new Jwt();
  private Logging logging = new Logging();

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
