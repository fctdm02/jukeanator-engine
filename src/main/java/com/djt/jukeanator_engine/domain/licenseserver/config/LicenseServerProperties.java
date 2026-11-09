package com.djt.jukeanator_engine.domain.licenseserver.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties bound to the {@code license-server:} YAML prefix. Only used in master mode, where
 * this instance is the public www.jukeanator.com site and license server.
 */
@Validated
@ConfigurationProperties(prefix = "license-server")
public class LicenseServerProperties {

  /**
   * Time zone for subscription quarter math (the paid-through quarter stamped at provisioning).
   * The legacy server used the host's default zone.
   */
  private String timeZone = "America/Detroit";

  /**
   * External directory served at {@code /temp/**} and {@code /webstart/**}: the legacy
   * {@code MP3Jukeboxx.jar} auto-update jar, {@code kiosk.zip}, third-party jars, JNLP files.
   * Blank disables those paths.
   */
  private String downloadsDir = "";

  /**
   * The frozen legacy auto-update jar whose date/size the legacy {@code getJukeANatorBuildDate} /
   * {@code getJukeANatorBuildSize} commands report. Legacy clients re-download on any date
   * mismatch, so this must keep pointing at the last legacy build. Blank = {@code
   * <downloads-dir>/temp/MP3Jukeboxx.jar}.
   */
  private String legacyJarPath = "";

  /** Latest jukeanator-engine version reported to engine clients ({@code getengineversion}). */
  private String latestEngineVersion = "";

  /** Where engine clients can download {@link #latestEngineVersion}. */
  private String engineDownloadUrl = "";

  /** Recipients of the public contact form ({@code command=feedback}). */
  private List<String> feedbackRecipients = new ArrayList<>(List.of("tmyers1@yahoo.com"));

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public String getDownloadsDir() {
    return downloadsDir;
  }

  public void setDownloadsDir(String downloadsDir) {
    this.downloadsDir = downloadsDir;
  }

  public String getLegacyJarPath() {
    return legacyJarPath;
  }

  public void setLegacyJarPath(String legacyJarPath) {
    this.legacyJarPath = legacyJarPath;
  }

  public String getLatestEngineVersion() {
    return latestEngineVersion;
  }

  public void setLatestEngineVersion(String latestEngineVersion) {
    this.latestEngineVersion = latestEngineVersion;
  }

  public String getEngineDownloadUrl() {
    return engineDownloadUrl;
  }

  public void setEngineDownloadUrl(String engineDownloadUrl) {
    this.engineDownloadUrl = engineDownloadUrl;
  }

  public List<String> getFeedbackRecipients() {
    return feedbackRecipients;
  }

  public void setFeedbackRecipients(List<String> feedbackRecipients) {
    this.feedbackRecipients = feedbackRecipients;
  }
}
