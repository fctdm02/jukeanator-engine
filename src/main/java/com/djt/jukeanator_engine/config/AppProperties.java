package com.djt.jukeanator_engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector;
import com.djt.jukeanator_engine.domain.common.utils.OperatingSystemDetector.OSType;

/**
 * Top-level application properties bound to the {@code app:} YAML prefix.
 *
 * <p>
 * All services that need a filesystem root path (song-library, song-queue, user) should call
 * {@link #getEffectiveRootPath()} rather than reading their own per-domain {@code root-path}
 * property. The resolved path is chosen at runtime based on the detected operating system:
 * {@code app.root-path-windows} on Windows, {@code app.root-path} everywhere else.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

  /** Root path used on Linux / macOS. */
  private String rootPath;

  /**
   * Root path used on Windows. When set, this value takes precedence over {@link #rootPath}
   * whenever the JVM is running on a Windows host.
   */
  private String rootPathWindows;

  private Jwt jwt = new Jwt();
  private Logging logging = new Logging();

  // ── OS-aware path resolution ──────────────────────────────────────────────

  /**
   * Returns the correct root path for the current operating system.
   *
   * <ul>
   * <li>Windows: returns {@link #rootPathWindows} if it is non-blank, otherwise falls back to
   * {@link #rootPath}.
   * <li>All other platforms: returns {@link #rootPath}.
   * </ul>
   */
  public String getEffectiveRootPath() {
    if (OperatingSystemDetector.getOperatingSystem() == OSType.WINDOWS && rootPathWindows != null
        && !rootPathWindows.isBlank()) {
      return rootPathWindows;
    }
    return rootPath;
  }

  // ── Getters / setters ─────────────────────────────────────────────────────

  public String getRootPath() {
    return rootPath;
  }

  public void setRootPath(String rootPath) {
    this.rootPath = rootPath;
  }

  public String getRootPathWindows() {
    return rootPathWindows;
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
